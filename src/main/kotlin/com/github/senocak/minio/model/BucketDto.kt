package com.github.senocak.minio.model

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonInclude
import java.time.ZonedDateTime

@JsonIgnoreProperties(ignoreUnknown = false)
@JsonInclude(JsonInclude.Include.NON_NULL)
data class BucketDto(
    var name: String,
    var creationDate: ZonedDateTime? = null,
    var policy: String? = null,
    var tags: Map<String, String> = hashMapOf(),
    var objects: List<String>? = listOf()
)
