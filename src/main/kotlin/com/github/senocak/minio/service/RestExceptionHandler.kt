package com.github.senocak.minio.service

import com.github.senocak.minio.model.ExceptionDto
import com.github.senocak.minio.model.OmaErrorMessageType
import com.github.senocak.minio.model.ServerException
import io.minio.errors.ErrorResponseException
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice

@RestControllerAdvice
class RestExceptionHandler {
    private val log: Logger = LoggerFactory.getLogger(this.javaClass)

    @ExceptionHandler(ServerException::class)
    fun handleServerException(ex: ServerException): ResponseEntity<Any> =
        generateResponseEntity(
            httpStatus = ex.statusCode,
            omaErrorMessageType = ex.omaErrorMessageType,
            variables = ex.variables
        )

    @ExceptionHandler(ErrorResponseException::class)
    fun handleServerException(ex: ErrorResponseException): ResponseEntity<Any> =
        generateResponseEntity(
            httpStatus = HttpStatus.FORBIDDEN,
            omaErrorMessageType = OmaErrorMessageType.GENERIC_SERVICE_ERROR,
            variables = arrayOf(ex.message)
        )

    @ExceptionHandler(Exception::class)
    fun handleGeneralException(ex: Exception): ResponseEntity<Any> =
        generateResponseEntity(
            httpStatus = HttpStatus.INTERNAL_SERVER_ERROR,
            variables = arrayOf(ex.message),
            omaErrorMessageType = OmaErrorMessageType.GENERIC_SERVICE_ERROR
        )

    private fun generateResponseEntity(
        httpStatus: HttpStatus,
        omaErrorMessageType: OmaErrorMessageType,
        variables: Array<String?>
    ): ResponseEntity<Any> {
        log.error("Exception is handled. HttpStatus: $httpStatus, OmaErrorMessageType: $omaErrorMessageType, variables: ${variables.toList()}")
        val exceptionDto: ExceptionDto = ExceptionDto(statusCode = httpStatus.value(), variables = variables)
            .also {
                    it: ExceptionDto ->
                it.error = ExceptionDto.OmaErrorMessageTypeDto(
                    id = omaErrorMessageType.messageId,
                    text = omaErrorMessageType.text
                )
            }
        return ResponseEntity.status(httpStatus).body(exceptionDto)
    }
}
