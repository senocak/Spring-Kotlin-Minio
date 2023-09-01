package com.github.senocak.minio.model

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.annotation.JsonPropertyOrder
import com.fasterxml.jackson.annotation.JsonTypeInfo
import com.fasterxml.jackson.annotation.JsonTypeName

@JsonTypeName("exception")
@JsonPropertyOrder("statusCode", "error", "variables")
@JsonTypeInfo(include = JsonTypeInfo.As.WRAPPER_OBJECT, use = JsonTypeInfo.Id.NAME)
@JsonIgnoreProperties(ignoreUnknown = false)
@JsonInclude(JsonInclude.Include.NON_NULL)
data class ExceptionDto(val statusCode: Int = 200, var error: OmaErrorMessageTypeDto? = null, var variables: Array<String?> = arrayOf(String())){

    @JsonPropertyOrder("id", "text")
    data class OmaErrorMessageTypeDto(val id: String? = null, val text: String? = null)
}