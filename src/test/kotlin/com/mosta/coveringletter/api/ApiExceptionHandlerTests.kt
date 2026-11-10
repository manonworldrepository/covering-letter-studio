package com.mosta.coveringletter.api

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.springframework.http.HttpStatus
import org.springframework.mock.http.server.reactive.MockServerHttpResponse
import org.springframework.web.bind.support.WebExchangeBindException
import org.springframework.web.server.ResponseStatusException
import org.springframework.validation.BeanPropertyBindingResult
import org.springframework.core.MethodParameter
import java.lang.reflect.Method

class ApiExceptionHandlerTests {

    private val handler = ApiExceptionHandler()

    private fun createMethodParameter(): MethodParameter {
        val method: Method = ApiExceptionHandler::class.java.getDeclaredMethod(
            "handleValidation",
            WebExchangeBindException::class.java,
            org.springframework.http.server.reactive.ServerHttpResponse::class.java
        )
        return MethodParameter(method, 0)
    }

    @Test
    fun `handles ResponseStatusException with custom message`() {
        val response = MockServerHttpResponse()
        val exception = ResponseStatusException(HttpStatus.BAD_REQUEST, "Custom error message")
        val result = handler.handleResponseStatus(exception, response)

        assertEquals(HttpStatus.BAD_REQUEST, response.statusCode)
        assertEquals("Custom error message", result.message)
    }

    @Test
    fun `handles ResponseStatusException with null reason`() {
        val response = MockServerHttpResponse()
        val exception = ResponseStatusException(HttpStatus.NOT_FOUND)
        val result = handler.handleResponseStatus(exception, response)

        assertEquals(HttpStatus.NOT_FOUND, response.statusCode)
        assertEquals("Request failed (HTTP 404).", result.message)
    }

    @Test
    fun `handles WebExchangeBindException with field error`() {
        val response = MockServerHttpResponse()
        val bindingResult = BeanPropertyBindingResult(java.lang.Object(), "target")
        bindingResult.addError(org.springframework.validation.FieldError(
            "target", "keywords", "Must not be blank"
        ))
        val exception = WebExchangeBindException(createMethodParameter(), bindingResult)
        val result = handler.handleValidation(exception, response)

        assertEquals(HttpStatus.BAD_REQUEST, response.statusCode)
        assertEquals("Must not be blank", result.message)
    }

    @Test
    fun `handles WebExchangeBindException with no field errors`() {
        val response = MockServerHttpResponse()
        val bindingResult = BeanPropertyBindingResult(java.lang.Object(), "target")
        val exception = WebExchangeBindException(createMethodParameter(), bindingResult)
        val result = handler.handleValidation(exception, response)

        assertEquals(HttpStatus.BAD_REQUEST, response.statusCode)
        assertEquals("Request validation failed.", result.message)
    }

    @Test
    fun `handles unexpected exception with generic message`() {
        val response = MockServerHttpResponse()
        val exception = RuntimeException("Internal failure")
        val result = handler.handleUnexpected(exception, response)

        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.statusCode)
        assertEquals("The server could not process the request. Check the application logs for details.", result.message)
    }

    @Test
    fun `handles ResponseStatusException with various status codes`() {
        val response = MockServerHttpResponse()
        val exception = ResponseStatusException(HttpStatus.UNAUTHORIZED, "Unauthorized")
        val result = handler.handleResponseStatus(exception, response)

        assertEquals(HttpStatus.UNAUTHORIZED, response.statusCode)
        assertEquals("Unauthorized", result.message)
    }
}
