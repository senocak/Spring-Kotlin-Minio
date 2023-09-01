package com.github.senocak.minio.service

import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.slf4j.MDC
import org.springframework.stereotype.Component
import org.springframework.web.servlet.AsyncHandlerInterceptor
import org.springframework.web.servlet.HandlerMapping

@Component
class CustomInterceptor: AsyncHandlerInterceptor {
    override fun preHandle(request: HttpServletRequest, response: HttpServletResponse, handler: Any): Boolean {
        val pathVariables: Map<*, *>? = request.getAttribute(HandlerMapping.URI_TEMPLATE_VARIABLES_ATTRIBUTE) as Map<*, *>?
        MDC.put("bucketName", pathVariables?.get("bucketName")?.toString())
        return true
    }
}