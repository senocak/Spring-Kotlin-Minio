package com.github.senocak.minio.config

import io.minio.MinioClient
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
@ConfigurationProperties(prefix = "minio")
class MinioConfig {
    lateinit var endpoint: String
    lateinit var port: String
    lateinit var accessKey: String
    lateinit var secretKey: String
    lateinit var secure: String
    lateinit var bucketName: String
    lateinit var imageSize: String
    lateinit var fileSize: String

    @Bean
    fun minioClient(): MinioClient {
        return MinioClient.builder()
            .credentials(accessKey, secretKey)
            .endpoint(endpoint, port.toInt(), secure.toBoolean())
            .build()
        // return MinioClient.builder()
        //    .endpoint("https://play.min.io")
        //    .credentials("Q3AM3UQ867SPQQA43P2F", "zuf+tfteSlswRu7BJ86wekitnifILbZam1KYY3TG")
        //    .build()
        // return MinioClient.builder()
        //    .endpoint("https://s3.amazonaws.com")
        //    .credentials("YOUR-ACCESSKEYID", "YOUR-SECRETACCESSKEY")
        //    .build()
    }
}
