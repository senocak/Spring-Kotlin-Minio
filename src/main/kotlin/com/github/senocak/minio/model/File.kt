package com.github.senocak.minio.model

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonInclude
import io.minio.messages.Owner
import java.time.ZonedDateTime

@JsonIgnoreProperties(ignoreUnknown = false)
@JsonInclude(JsonInclude.Include.NON_NULL)
data class File(
    val etag: String? = null,
    val objectName: String? = null,
    val lastModified: ZonedDateTime? = null,
    val owner: Owner? = null,
    val size: Long = 0,
    val storageClass: String? = null,
    val isLatest: Boolean = false,
    val versionId: String? = null,
    val userMetadata: MutableMap<String, String>? = mutableMapOf(),
    val isDir: Boolean = false
)
