package com.mosta.coveringletter.aspects

import org.aspectj.lang.ProceedingJoinPoint
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ApiExceptionLoggingAspectTests {

	@Test
	fun `aspect is annotated with @Aspect`() {
		val aspectAnnotation = ApiExceptionLoggingAspect::class.java.annotations
			.find { it is org.aspectj.lang.annotation.Aspect }
		assertTrue(aspectAnnotation != null)
	}

	@Test
	fun `aspect is annotated with @Component`() {
		val componentAnnotation = ApiExceptionLoggingAspect::class.java.annotations
			.find { it is org.springframework.stereotype.Component }
		assertTrue(componentAnnotation != null)
	}

	@Test
	fun `logResponseStatus method exists with ProceedingJoinPoint parameter`() {
		val method = ApiExceptionLoggingAspect::class.java.getDeclaredMethod(
			"logResponseStatus", ProceedingJoinPoint::class.java
		)
		assertTrue(method != null)
		val around = method.getAnnotation(org.aspectj.lang.annotation.Around::class.java)
		assertTrue(around != null)
	}

	@Test
	fun `logUnexpected method exists with ProceedingJoinPoint parameter`() {
		val method = ApiExceptionLoggingAspect::class.java.getDeclaredMethod(
			"logUnexpected", ProceedingJoinPoint::class.java
		)
		assertTrue(method != null)
		val around = method.getAnnotation(org.aspectj.lang.annotation.Around::class.java)
		assertTrue(around != null)
	}

	@Test
	fun `logResponseStatus pointcut targets ApiExceptionHandler`() {
		val method = ApiExceptionLoggingAspect::class.java.getDeclaredMethod(
			"logResponseStatus", ProceedingJoinPoint::class.java
		)
		val around = method.getAnnotation(org.aspectj.lang.annotation.Around::class.java)
		assertTrue(around != null)
		assertTrue(around.value.contains("ApiExceptionHandler"))
	}

	@Test
	fun `logUnexpected pointcut targets ApiExceptionHandler`() {
		val method = ApiExceptionLoggingAspect::class.java.getDeclaredMethod(
			"logUnexpected", ProceedingJoinPoint::class.java
		)
		val around = method.getAnnotation(org.aspectj.lang.annotation.Around::class.java)
		assertTrue(around != null)
		assertTrue(around.value.contains("ApiExceptionHandler"))
	}
}
