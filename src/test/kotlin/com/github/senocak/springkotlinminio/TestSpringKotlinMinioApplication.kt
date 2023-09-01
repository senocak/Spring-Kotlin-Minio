package com.github.senocak.springkotlinminio

import com.github.senocak.minio.MinioApplication
import org.springframework.boot.fromApplication
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.boot.with

@TestConfiguration(proxyBeanMethods = false)
class TestSpringKotlinMinioApplication

fun main(args: Array<String>) {
    fromApplication<MinioApplication>().with(TestSpringKotlinMinioApplication::class).run(*args)
}
