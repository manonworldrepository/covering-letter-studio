package com.mosta.coveringletter.security

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.mock.http.server.reactive.MockServerHttpRequest
import org.springframework.mock.web.server.MockServerWebExchange
import org.springframework.security.core.AuthenticationException

class ApiAuthenticationEntryPointTests {

	private lateinit var entryPoint: SecurityConfig.ApiAuthenticationEntryPoint

	@BeforeEach
	fun setup() {
		entryPoint = SecurityConfig.ApiAuthenticationEntryPoint()
	}

	@Test
	fun `returns 401 JSON for API path`() {
		val exchange = createExchange("/api/settings")
		val exception = MockAuthenticationException("test")

		entryPoint.commence(exchange, exception).block()

		assertEquals(HttpStatus.UNAUTHORIZED, exchange.response.statusCode)
		assertEquals(MediaType.APPLICATION_JSON, exchange.response.headers.contentType)
		val body = exchange.response.bodyAsString.block() ?: ""
		assertEquals(true, body.contains("Session expired"))
	}

	@Test
	fun `returns 401 JSON for nested API path`() {
		val exchange = createExchange("/api/applied-jobs/123/status")
		val exception = MockAuthenticationException("test")

		entryPoint.commence(exchange, exception).block()

		assertEquals(HttpStatus.UNAUTHORIZED, exchange.response.statusCode)
		assertEquals(MediaType.APPLICATION_JSON, exchange.response.headers.contentType)
	}

	@Test
	fun `redirects to login for non-API path`() {
		val exchange = createExchange("/dashboard")
		val exception = MockAuthenticationException("test")

		entryPoint.commence(exchange, exception).block()

		assertEquals(HttpStatus.FOUND, exchange.response.statusCode)
		assertEquals("/login.html", exchange.response.headers.location?.path)
	}

	@Test
	fun `redirects to login for root path`() {
		val exchange = createExchange("/")
		val exception = MockAuthenticationException("test")

		entryPoint.commence(exchange, exception).block()

		assertEquals(HttpStatus.FOUND, exchange.response.statusCode)
		assertEquals("/login.html", exchange.response.headers.location?.path)
	}

	@Test
	fun `redirects to login for static resource path`() {
		val exchange = createExchange("/favicon.ico")
		val exception = MockAuthenticationException("test")

		entryPoint.commence(exchange, exception).block()

		assertEquals(HttpStatus.FOUND, exchange.response.statusCode)
		assertEquals("/login.html", exchange.response.headers.location?.path)
	}

	@Test
	fun `returns 401 JSON for api root path`() {
		val exchange = createExchange("/api/")
		val exception = MockAuthenticationException("test")

		entryPoint.commence(exchange, exception).block()

		assertEquals(HttpStatus.UNAUTHORIZED, exchange.response.statusCode)
	}

	private fun createExchange(path: String): MockServerWebExchange {
		val request = MockServerHttpRequest.get(path).build()
		val exchange = MockServerWebExchange.builder(request).build()
		return exchange
	}

	private class MockAuthenticationException(msg: String) : AuthenticationException(msg)
}
