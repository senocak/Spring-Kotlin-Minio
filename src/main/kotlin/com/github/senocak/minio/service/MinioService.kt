package com.github.senocak.minio.service

import com.github.senocak.minio.config.MinioConfig
import com.github.senocak.minio.model.BucketDto
import com.github.senocak.minio.model.FileResponse
import com.github.senocak.minio.model.OmaErrorMessageType
import com.github.senocak.minio.model.ServerException
import io.minio.BucketExistsArgs
import io.minio.DeleteBucketTagsArgs
import io.minio.GetBucketPolicyArgs
import io.minio.GetBucketTagsArgs
import io.minio.GetObjectArgs
import io.minio.ListObjectsArgs
import io.minio.ListenBucketNotificationArgs
import io.minio.MakeBucketArgs
import io.minio.MinioClient
import io.minio.PutObjectArgs
import io.minio.RemoveBucketArgs
import io.minio.RemoveObjectArgs
import io.minio.RemoveObjectsArgs
import io.minio.Result
import io.minio.SetBucketPolicyArgs
import io.minio.SetBucketTagsArgs
import io.minio.StatObjectArgs
import io.minio.StatObjectResponse
import io.minio.messages.Bucket
import io.minio.messages.DeleteError
import io.minio.messages.DeleteObject
import io.minio.messages.Event
import io.minio.messages.Item
import io.minio.messages.NotificationRecords
import io.minio.messages.Tags
import java.io.ByteArrayInputStream
import java.io.InputStream
import java.time.LocalDateTime
import java.util.Locale
import java.util.UUID
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.scheduling.annotation.Async
import org.springframework.stereotype.Service
import org.springframework.util.StringUtils
import org.springframework.web.multipart.MultipartFile


@Service
class MinioService(private val minioClient: MinioClient, private val minioConfig: MinioConfig){
    private val log: Logger = LoggerFactory.getLogger(this.javaClass)

    fun listBuckets(): List<BucketDto> =
        minioClient.listBuckets()
            .map { b: Bucket -> BucketDto(name = b.name(), creationDate = b.creationDate(), policy = getBucketPolicy(bucketName = b.name())) }.toList()
            .also { log.info("listBuckets: $it") }

    fun makeBucket(bucket: String, region: String? = null, policy: String? = null): Unit  =
        minioClient.makeBucket(MakeBucketArgs.builder().bucket(bucket).region(region).build())
            .also {
                if (policy != null)
                    minioClient.setBucketPolicy(SetBucketPolicyArgs.builder().bucket(bucket).config(policy).build())
            }
            .also { log.info("makeBucket: $it") }

    fun show(bucketName: String): List<String> {
        bucketExists(bucketName = bucketName)
        return listObjects(bucketName)
            .map { o: Result<Item> -> o.get().objectName() }
            .toList()
            .also { log.info("show: $it") }
    }

    fun deleteBucket(bucketName: String): Unit = minioClient.removeBucket(RemoveBucketArgs.builder().bucket(bucketName).build())
        .also { log.info("deleteBucket: $it") }

    fun uploadFile(multipartFile: MultipartFile, bucketName: String = minioConfig.bucketName): FileResponse {
        val fileType: String = getFileType(type = StringUtils.getFilenameExtension(multipartFile.originalFilename))
        return try {
            if (!bucketExists(bucketName = bucketName))
                this.makeBucket(bucket = bucketName)
            val fileName: String = multipartFile.originalFilename ?: throw Exception("originalFilename error")
            val objectName: String = UUID.randomUUID().toString().replace(regex = "-".toRegex(), replacement = "") + fileName.substring(fileName.lastIndexOf(string = "."))
            minioClient.putObject(
                PutObjectArgs.builder().bucket(bucketName).`object`(objectName)
                    .stream(ByteArrayInputStream(multipartFile.bytes), -1, minioConfig.fileSize.toLong())
                    .contentType(fileType)
                    .build()
            )
            FileResponse(fileName = objectName, fileSize = multipartFile.size, contentType = fileType)
                .also {
                        it: FileResponse ->
                    it.createdTime = LocalDateTime.now()
                    log.info("uploadFile: $it")
                }
        } catch (e: Exception) {
            log.error("Exception occurred. ${e.message}")
            throw e
        }
    }

    private fun getFileType(type: String?): String =
         when {
            type == null -> throw Exception("Type can not be null")
            type.lowercase(Locale.getDefault()) in listOf("jpg", "jpeg", "gif", "png", "bmp", "pcx", "tga", "psd", "tiff") -> "image/$type"
            type.lowercase(Locale.getDefault()) in listOf("mp3", "ogg", "wav", "real", "ape", "module", "midi", "vqf", "cd") -> "audio/$type"
            type.lowercase(Locale.getDefault()) in listOf("mp4", "avi", "mpeg-1", "rm", "asf", "wmv", "qlv", "mpeg-2", "mpeg4", "mov", "3gp") -> "video/$type"
            type.lowercase(Locale.getDefault()) in listOf("doc", "docx", "ppt", "pptx", "xls", "xlsx", "zip", "jar") -> "application/$type"
            type.lowercase(Locale.getDefault()).equals("txt", ignoreCase = true) -> "text/$type"
            else -> throw Exception("FilenameExtension error")
        }

    fun getBucketPolicy(bucketName: String): String {
        bucketExists(bucketName = bucketName)
        return minioClient.getBucketPolicy(GetBucketPolicyArgs.builder().bucket(bucketName).build())
            .also { log.info("getBucketPolicy: bucketName: $bucketName, $it") }
    }

    fun setBucketTags(bucketName: String, tags: Map<String, String>) =
        bucketExists(bucketName = bucketName)
            .also {
                minioClient.setBucketTags(SetBucketTagsArgs.builder().bucket(bucketName).tags(tags).build())
                    .also { log.info("setBucketTags: bucketName: $bucketName, tags: $tags") }
            }

    fun getBucketTags(bucketName: String): Tags {
        bucketExists(bucketName = bucketName)
        return minioClient.getBucketTags(GetBucketTagsArgs.builder().bucket(bucketName).build())
            .also { log.info("getBucketTags: bucketName: $bucketName, $it") }
    }

    fun deleteBucketTags(bucketName: String, tag: String) =
        bucketExists(bucketName = bucketName)
            .also {
                minioClient.deleteBucketTags(DeleteBucketTagsArgs.builder().bucket(bucketName).build())
                    .also { log.info("deleteBucketTags: bucketName: $bucketName, tag: $tag") }
            }

    fun removeObjects(bucketName: String, objects: MutableList<DeleteObject>): MutableIterable<Result<DeleteError>> {
        bucketExists(bucketName = bucketName)
        return minioClient.removeObjects(RemoveObjectsArgs.builder().bucket(bucketName).objects(objects).build())
            .also { log.info("removeObjects: bucketName: $bucketName, objects: $objects, $it") }
    }

    fun bucketExists(bucketName: String): Boolean =
        when {
            !minioClient.bucketExists(BucketExistsArgs.builder().bucket(bucketName).build()) ->
                throw ServerException(omaErrorMessageType = OmaErrorMessageType.NOT_FOUND, statusCode = HttpStatus.NOT_FOUND)
                    .also { log.error("Bucket:$bucketName not found") }
            else -> true
        }

    fun listObjects(bucketName: String): MutableIterable<Result<Item>> {
        bucketExists(bucketName = bucketName)
        return minioClient.listObjects(ListObjectsArgs.builder().bucket(bucketName).build())
            .also { log.info("listObjects: bucketName: $bucketName, $it") }
    }

    fun removeObject(bucketName: String, objectName: String) {
        bucketExists(bucketName = bucketName)
            .also {
                minioClient.removeObject(RemoveObjectArgs.builder().bucket(bucketName).`object`(objectName).build())
                    .also { log.info("removeObject: bucketName: $bucketName") }
            }
    }

    fun downloadObject(bucketName: String, objectName: String): InputStream {
        val statObject: StatObjectResponse = statObject(bucketName, objectName)
        if (statObject.size() > 0)
            return minioClient.getObject(GetObjectArgs.builder().bucket(bucketName).`object`(objectName).build())
                .also { log.info("downloadObject: bucketName: $bucketName, $it") }
        throw ServerException(omaErrorMessageType = OmaErrorMessageType.NOT_FOUND, statusCode = HttpStatus.NOT_FOUND)
            .also { log.error("ServerException: ${it.message}, bucketName: $bucketName, objectName: $objectName") }
    }

    fun statObject(bucketName: String, objectName: String): StatObjectResponse {
        bucketExists(bucketName = bucketName)
        return minioClient.statObject(StatObjectArgs.builder().bucket(bucketName).`object`(objectName).build())
            .also { log.error("statObject: bucketName: $bucketName, objectName: $objectName, $it") }
    }

    @Async
    fun listenBucketNotification(bucketName: String) {
        bucketExists(bucketName = bucketName)
        val listenBucketNotification = minioClient.listenBucketNotification(
            ListenBucketNotificationArgs.builder()
                .bucket(bucketName)
                .events(arrayOf("s3:ObjectCreated:*", "s3:ObjectAccessed:*"))
                .build()
        )
        while (listenBucketNotification.hasNext()) {
            val notificationRecords: NotificationRecords = listenBucketNotification.next().get()
            notificationRecords.events().forEach { event: Event -> log.info("bucketName: ${event.bucketName()} eventType: ${event.eventType()}") }
        }
    }
}