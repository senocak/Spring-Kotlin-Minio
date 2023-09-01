package com.github.senocak.minio.model

import org.springframework.http.HttpStatus

class ServerException(var omaErrorMessageType: OmaErrorMessageType, var variables: Array<String?> = arrayOf(), var statusCode: HttpStatus = HttpStatus.BAD_REQUEST):
    Exception("OmaErrorMessageType: $omaErrorMessageType, variables: $variables, statusCode: $statusCode")