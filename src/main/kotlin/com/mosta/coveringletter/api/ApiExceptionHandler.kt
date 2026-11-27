package com.mosta.coveringletter.api

import com.mosta.coveringletter.api.response.ApiErrorResponse
import org.springframework.http.HttpStatus
import org.springframework.http.server.reactive.ServerHttpResponse
import org.springframework.security.access.AccessDeniedException
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice
import org.springframework.web.bind.support.WebExchangeBindException
import org.springframework.web.server.ResponseStatusException

@RestControllerAdvice
class ApiExceptionHandler {
	@ExceptionHandler(ResponseStatusException::class)
	fun handleResponseStatus(exception: ResponseStatusException, response: ServerHttpResponse): ApiErrorResponse {
		val message = exception.reason ?: "Request failed (HTTP ${exception.statusCode.value()})."
		response.statusCode = exception.statusCode
		return ApiErrorResponse(message)
	}

	@ExceptionHandler(WebExchangeBindException::class)
	fun handleValidation(exception: WebExchangeBindException, response: ServerHttpResponse): ApiErrorResponse {
		val message = exception.bindingResult.fieldErrors.firstOrNull()?.defaultMessage
			?: "Request validation failed."
		response.statusCode = HttpStatus.BAD_REQUEST
		return ApiErrorResponse(message)
	}

	@ExceptionHandler(IllegalArgumentException::class)
	fun handleIllegalArgument(exception: IllegalArgumentException, response: ServerHttpResponse): ApiErrorResponse {
		// JWT validation failures (expired token, invalid signature, wrong type) throw IllegalArgumentException
		// These should return 401 Unauthorized, not 500 Internal Server Error
		response.statusCode = HttpStatus.UNAUTHORIZED
		return ApiErrorResponse(exception.message ?: "Unauthorized.")
	}

	@ExceptionHandler(AccessDeniedException::class)
	fun handleAccessDenied(exception: AccessDeniedException, response: ServerHttpResponse): ApiErrorResponse {
		response.statusCode = HttpStatus.FORBIDDEN
		return ApiErrorResponse("Access denied.")
	}

	@ExceptionHandler(Exception::class)
	fun handleUnexpected(exception: Exception, response: ServerHttpResponse): ApiErrorResponse {
		response.statusCode = HttpStatus.INTERNAL_SERVER_ERROR
		return ApiErrorResponse("The server could not process the request. Check the application logs for details.")
	}
}
