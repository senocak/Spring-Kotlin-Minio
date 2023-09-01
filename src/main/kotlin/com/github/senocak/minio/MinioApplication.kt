package com.github.senocak.minio

import com.github.senocak.minio.service.CustomInterceptor
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication
import org.springframework.scheduling.annotation.EnableAsync
import org.springframework.web.servlet.config.annotation.InterceptorRegistry
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer

@EnableAsync
@SpringBootApplication
class MinioApplication(private val customInterceptor: CustomInterceptor): WebMvcConfigurer {
    override fun addInterceptors(registry: InterceptorRegistry) {
        registry
            .addInterceptor(customInterceptor)
            .addPathPatterns("/buckets/{bucketName}/**")
    }
}

fun main(args: Array<String>) {
    runApplication<MinioApplication>(*args)
}