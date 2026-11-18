package com.mosta.coveringletter.security

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.http.HttpStatus
import org.springframework.mock.http.server.reactive.MockServerHttpRequest
import org.springframework.mock.web.server.MockServerWebExchange
import org.springframework.security.core.Authentication
import org.springframework.security.core.GrantedAuthority
import org.springframework.security.web.server.WebFilterExchange
import org.springframework.web.server.WebFilterChain

class LoginSuccessHandlerTests {

	private lateinit var handler: SecurityConfig.LoginSuccessHandler

	@BeforeEach
	fun setup() {
		handler = SecurityConfig.LoginSuccessHandler(setOf("info@manonworld.de", "admin@example.com"))
	}

	@Test
	fun `redirects to home for allowed email`() {
		val exchange = createExchange("/login/oauth2/code/google")
		val authentication = MockAuthentication("info@manonworld.de")

		handler.onAuthenticationSuccess(exchange, authentication).block()

		assertEquals(HttpStatus.FOUND, exchange.exchange.response.statusCode)
		assertEquals("/", exchange.exchange.response.headers.location?.path)
	}

	@Test
	fun `redirects to home for allowed email case insensitive`() {
		val exchange = createExchange("/login/oauth2/code/google")
		val authentication = MockAuthentication("INFO@MANONWORLD.DE")

		handler.onAuthenticationSuccess(exchange, authentication).block()

		assertEquals(HttpStatus.FOUND, exchange.exchange.response.statusCode)
		assertEquals("/", exchange.exchange.response.headers.location?.path)
	}

	@Test
	fun `redirects to access denied for non-allowed email`() {
		val exchange = createExchange("/login/oauth2/code/google")
		val authentication = MockAuthentication("unauthorized@example.com")

		handler.onAuthenticationSuccess(exchange, authentication).block()

		assertEquals(HttpStatus.FOUND, exchange.exchange.response.statusCode)
		assertEquals("/login.html", exchange.exchange.response.headers.location?.path)
		assertEquals(true, exchange.exchange.response.headers.location?.query?.contains("error=access_denied") == true)
	}

	@Test
	fun `redirects to access denied for empty allowed list`() {
		val emptyHandler = SecurityConfig.LoginSuccessHandler(emptySet())
		val exchange = createExchange("/login/oauth2/code/google")
		val authentication = MockAuthentication("anyone@example.com")

		emptyHandler.onAuthenticationSuccess(exchange, authentication).block()

		assertEquals(HttpStatus.FOUND, exchange.exchange.response.statusCode)
		assertEquals(true, exchange.exchange.response.headers.location?.query?.contains("error=access_denied") == true)
	}

	@Test
	fun `redirects to home for second allowed email`() {
		val exchange = createExchange("/login/oauth2/code/google")
		val authentication = MockAuthentication("admin@example.com")

		handler.onAuthenticationSuccess(exchange, authentication).block()

		assertEquals(HttpStatus.FOUND, exchange.exchange.response.statusCode)
		assertEquals("/", exchange.exchange.response.headers.location?.path)
	}

	private fun createExchange(path: String): WebFilterExchange {
		val request = MockServerHttpRequest.get(path).build()
		val serverExchange = MockServerWebExchange.builder(request).build()
		val chain = object : WebFilterChain {
			override fun filter(exchange: org.springframework.web.server.ServerWebExchange): reactor.core.publisher.Mono<Void> = reactor.core.publisher.Mono.empty()
		}
		return WebFilterExchange(serverExchange, chain)
	}

	private class MockAuthentication(private val email: String) : Authentication {
		override fun getName(): String = email
		override fun getAuthorities(): Collection<GrantedAuthority> = emptyList()
		override fun getCredentials(): Any = ""
		override fun getDetails(): Any? = null
		override fun getPrincipal(): Any = email
		override fun isAuthenticated(): Boolean = true
		override fun setAuthenticated(isAuthenticated: Boolean) {}
	}
}
