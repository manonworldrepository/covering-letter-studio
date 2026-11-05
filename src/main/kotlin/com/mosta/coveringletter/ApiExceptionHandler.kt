package com.mosta.coveringletter

import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice
import org.springframework.web.server.ResponseStatusException

data class ApiErrorResponse(val message: String)

@RestControllerAdvice
class ApiExceptionHandler {
	@ExceptionHandler(ResponseStatusException::class)
	fun handleResponseStatus(exception: ResponseStatusException): ResponseEntity<ApiErrorResponse> {
		val message = exception.reason ?: "Request failed (HTTP ${exception.statusCode.value()})."
		logger.warn("Cover letter request failed (HTTP {}): {}", exception.statusCode.value(), message)
		return ResponseEntity.status(exception.statusCode).body(ApiErrorResponse(message))
	}

	@ExceptionHandler(Exception::class)
	fun handleUnexpected(exception: Exception): ResponseEntity<ApiErrorResponse> {
		logger.error("Unexpected error while handling a cover letter request.", exception)
		return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
			.body(ApiErrorResponse("The server could not process the request. Check the application logs for details."))
	}

	private companion object {
		val logger = LoggerFactory.getLogger(ApiExceptionHandler::class.java)
	}
}
