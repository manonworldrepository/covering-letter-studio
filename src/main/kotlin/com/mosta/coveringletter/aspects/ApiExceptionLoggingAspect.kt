package com.mosta.coveringletter.aspects

import org.aspectj.lang.ProceedingJoinPoint
import org.aspectj.lang.annotation.Around
import org.aspectj.lang.annotation.Aspect
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component

private val logger = LoggerFactory.getLogger(ApiExceptionLoggingAspect::class.java)

@Aspect
@Component
class ApiExceptionLoggingAspect {

	@Around("execution(* com.mosta.coveringletter.api.ApiExceptionHandler.handleResponseStatus(..))")
	fun logResponseStatus(joinPoint: ProceedingJoinPoint) {
		val exception = joinPoint.args[0] as? Exception
			?: throw IllegalStateException("Expected ResponseStatusException as first argument")
		logger.warn(
			"Cover letter request failed (HTTP {}): {}",
			(exception as? org.springframework.web.server.ResponseStatusException)?.statusCode?.value() ?: 0,
			exception.message
		)
		joinPoint.proceed()
	}

	@Around("execution(* com.mosta.coveringletter.api.ApiExceptionHandler.handleUnexpected(..))")
	fun logUnexpected(joinPoint: ProceedingJoinPoint) {
		val exception = joinPoint.args[0] as Exception
		logger.error("Unexpected error while handling a cover letter request.", exception)
		joinPoint.proceed()
	}
}
