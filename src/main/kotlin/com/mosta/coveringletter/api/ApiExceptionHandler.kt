package com.mosta.coveringletter.api

import org.springframework.http.HttpStatus
import org.springframework.http.server.reactive.ServerHttpResponse
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice
import org.springframework.web.bind.support.WebExchangeBindException
import org.springframework.web.server.ResponseStatusException

@RestControllerAdvice
class ApiExceptionHandler {
	@ExceptionHandler(ResponseStatusException::class)
	fun handleResponseStatus(exception: ResponseStatusException, response: ServerHttpResponse): ApiErrorResponse {
		val message = exception.reason ?: "Request failed (HTTP ${exception.statusCode.value()})."
		ApiExceptionHandlerSupport.logger.warn("Cover letter request failed (HTTP {}): {}", exception.statusCode.value(), message)
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

	@ExceptionHandler(Exception::class)
	fun handleUnexpected(exception: Exception, response: ServerHttpResponse): ApiErrorResponse {
		ApiExceptionHandlerSupport.logger.error("Unexpected error while handling a cover letter request.", exception)
		response.statusCode = HttpStatus.INTERNAL_SERVER_ERROR
		return ApiErrorResponse("The server could not process the request. Check the application logs for details.")
	}
}
