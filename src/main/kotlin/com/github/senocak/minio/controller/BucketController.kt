package com.github.senocak.minio.controller

import com.github.senocak.minio.model.BucketDto
import com.github.senocak.minio.model.File
import com.github.senocak.minio.model.FileResponse
import com.github.senocak.minio.model.OmaErrorMessageType
import com.github.senocak.minio.model.ServerException
import com.github.senocak.minio.service.MinioService
import io.minio.messages.DeleteError
import io.minio.messages.DeleteObject
import io.minio.messages.Item
import io.minio.Result
import jakarta.servlet.http.HttpServletResponse
import java.io.IOException
import java.io.InputStream
import java.io.UnsupportedEncodingException
import java.net.URLEncoder
import java.util.LinkedList
import org.apache.tomcat.util.http.fileupload.IOUtils
import org.springframework.http.HttpStatus
import org.springframework.http.HttpStatusCode
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.multipart.MultipartFile

@RestController
@RequestMapping("/buckets")
class BucketController(private val minioService: MinioService) {

    @GetMapping
    fun listBuckets(@RequestParam(required = false) objects: Boolean? = false): List<BucketDto> =
        minioService.listBuckets()
            .onEach { bn: BucketDto ->
                run {
                    if (bn.policy.isNullOrBlank())
                        bn.policy = null
                    else
                        bn.policy = minioService.getBucketPolicy(bucketName = bn.name)
                    bn.tags = minioService.getBucketTags(bucketName = bn.name).get()
                    if (objects != null && objects)
                        bn.objects = minioService.show(bucketName = bn.name)
                    else
                        bn.objects = null
                }
            }

    @PostMapping
    @ResponseStatus(value = HttpStatus.CREATED)
    fun makeBucket(@RequestBody bucket: BucketDto): BucketDto = BucketDto(name = bucket.name)
        .apply {
            minioService.makeBucket(bucket = bucket.name, policy = bucket.policy)
            val bucketDto: BucketDto = showBucket(bucketName = bucket.name)
            this.name = bucketDto.name
            this.creationDate = bucketDto.creationDate
            if (!bucket.policy.isNullOrBlank())
                this.policy = minioService.getBucketPolicy(bucketName = bucketDto.name)
            this.tags = bucket.tags
                .also { minioService.setBucketTags(bucketName = bucketDto.name, tags = bucket.tags) }
            this.objects = minioService.show(bucketName = bucketDto.name)
            minioService.listenBucketNotification(bucketName = bucketDto.name)
            return@apply
        }

    @GetMapping("/{bucketName}")
    fun showBucket(@PathVariable bucketName: String, @RequestParam(required = false) objects: Boolean? = false): BucketDto = BucketDto(name = bucketName)
        .apply {
            val bucketDto: BucketDto = listBuckets().firstOrNull { bn: BucketDto -> bn.name == bucketName }
                ?: throw ServerException(omaErrorMessageType = OmaErrorMessageType.NOT_FOUND, statusCode = HttpStatus.NOT_FOUND)
            this.name = bucketDto.name
            this.creationDate = bucketDto.creationDate
            if (!bucketDto.policy.isNullOrBlank())
                this.policy = minioService.getBucketPolicy(bucketName = bucketDto.name)
            this.tags = minioService.getBucketTags(bucketName = bucketName).get()
            if (objects != null && objects)
                this.objects = minioService.listObjects(bucketName = bucketName).map { o: Result<Item> -> o.get().objectName() }
            else
                this.objects = null
            return@apply
        }

    @DeleteMapping("/{bucketName}")
    fun deleteBucket(@PathVariable bucketName: String, @RequestParam(required = false) force: Boolean = false): ResponseEntity<String> {
        for (result: Result<Item> in minioService.listObjects(bucketName = bucketName))
            if (result.get().size() > 0 && !force)
                throw ServerException(omaErrorMessageType = OmaErrorMessageType.GENERIC_SERVICE_ERROR, statusCode = HttpStatus.CONFLICT,
                    variables = arrayOf("There are objects in bucket."))
            else
                deleteObjectsAsList(bucketName = bucketName, objectNames = mutableListOf(result.get().objectName()))
        minioService.deleteBucket(bucketName = bucketName)
        return ResponseEntity.noContent().build()
    }

    @GetMapping("/{bucketName}/files")
    fun getAllFilesForBucket(@PathVariable bucketName: String): ArrayList<File> =
        arrayListOf<File>()
            .also {
                minioService.listObjects(bucketName = bucketName)
                    .map { o: Result<Item> -> o.get() }
                    .forEach { o: Item -> it.add(element = File(
                        etag = o.etag(),
                        objectName = o.objectName(),
                        lastModified = o.lastModified(),
                        owner = o.owner(),
                        size = o.size(),
                        storageClass = o.storageClass(),
                        isLatest = o.isLatest,
                        versionId = o.versionId(),
                        userMetadata = o.userMetadata(),
                        isDir = o.isDir
                    )) }
            }

    @PostMapping("/{bucketName}/files")
    @ResponseStatus(value = HttpStatus.CREATED)
    fun uploadFileForBucket(@RequestBody file: MultipartFile, @PathVariable bucketName: String): FileResponse =
        minioService.uploadFile(multipartFile = file, bucketName = bucketName)

    @DeleteMapping("/{bucketName}/files")
    fun deleteObjectsAsList(@PathVariable bucketName: String, @RequestBody objectNames: List<String>): ResponseEntity<Any> {
        val objects: MutableList<DeleteObject> = LinkedList()
        objectNames.forEach { ob: String -> objects.add(element = DeleteObject(ob)) }
        val results: MutableIterable<Result<DeleteError>> = minioService.removeObjects(bucketName = bucketName, objects = objects)
        for (result: Result<DeleteError> in results) {
            val error: DeleteError = result.get()
            return ResponseEntity("Objectname: ${error.objectName()}, Message: ${error.message()}", HttpStatusCode.valueOf(400))
        }
        return ResponseEntity.noContent().build()
    }

    @GetMapping("/{bucketName}/files/{objectName}")
    fun download(response: HttpServletResponse, @PathVariable bucketName: String, @PathVariable("objectName") objectName: String) {
        var inputStream: InputStream? = null
        try {
            inputStream = minioService.downloadObject(bucketName, objectName)
            response.setHeader("Content-Disposition", "attachment;filename=${URLEncoder.encode(objectName, "UTF-8")}")
            response.characterEncoding = "UTF-8"
            IOUtils.copy(inputStream, response.outputStream)
        } catch (e: UnsupportedEncodingException) {
            println("MinioController | download | UnsupportedEncodingException : " + e.message)
        } catch (e: IOException) {
            println("MinioController | download | IOException : " + e.message)
        } finally {
            try {
                inputStream?.close()
            } catch (e: IOException) {
                println("MinioController | download | IOException : " + e.message)
            }
        }
    }

    @DeleteMapping("/{bucketName}/files/{objectName}")
    fun removeObject(@PathVariable bucketName: String, @PathVariable objectName: String): ResponseEntity<Any> {
        minioService.removeObject(bucketName, objectName)
        return ResponseEntity.noContent().build()
    }
}