package com.mosta.coveringletter.security

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.http.HttpStatus
import org.springframework.mock.http.server.reactive.MockServerHttpRequest
import org.springframework.mock.web.server.MockServerWebExchange
import org.springframework.security.core.AuthenticationException
import org.springframework.security.web.server.WebFilterExchange
import org.springframework.web.server.WebFilterChain

class LoginFailureHandlerTests {

	private lateinit var handler: SecurityConfig.LoginFailureHandler

	@BeforeEach
	fun setup() {
		handler = SecurityConfig.LoginFailureHandler()
	}

	@Test
	fun `redirects to login with error parameter on failure`() {
		val exchange = createExchange("/login/oauth2/code/google")
		val exception = MockAuthenticationException("test")

		handler.onAuthenticationFailure(exchange, exception).block()

		assertEquals(HttpStatus.FOUND, exchange.exchange.response.statusCode)
		assertEquals("/login.html", exchange.exchange.response.headers.location?.path)
		assertEquals(true, exchange.exchange.response.headers.location?.query?.contains("error=authentication_failed") == true)
	}

	@Test
	fun `redirects to login with error for any path`() {
		val exchange = createExchange("/api/settings")
		val exception = MockAuthenticationException("test")

		handler.onAuthenticationFailure(exchange, exception).block()

		assertEquals(HttpStatus.FOUND, exchange.exchange.response.statusCode)
		assertEquals(true, exchange.exchange.response.headers.location?.query?.contains("error=authentication_failed") == true)
	}

	@Test
	fun `handler is correctly instantiated`() {
		val handler = SecurityConfig.LoginFailureHandler()
		assertEquals(true, handler is SecurityConfig.LoginFailureHandler)
	}

	private fun createExchange(path: String): WebFilterExchange {
		val request = MockServerHttpRequest.get(path).build()
		val serverExchange = MockServerWebExchange.builder(request).build()
		val chain = object : WebFilterChain {
			override fun filter(exchange: org.springframework.web.server.ServerWebExchange): reactor.core.publisher.Mono<Void> = reactor.core.publisher.Mono.empty()
		}
		return WebFilterExchange(serverExchange, chain)
	}

	private class MockAuthenticationException(msg: String) : AuthenticationException(msg)
}
