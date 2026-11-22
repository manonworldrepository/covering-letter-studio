package com.mosta.coveringletter.security

import com.mosta.coveringletter.activity.UserActionLogBuffer
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
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

	private val userActions = UserActionLogBuffer()
	private val jwtService = JwtService(900, 604800)
	private lateinit var handler: SecurityConfig.CookieIssuingSuccessHandler

	@BeforeEach
	fun setup() {
		val config = SecurityConfig(userActions, jwtService, JwtAuthenticationConverter(jwtService))
		val field = SecurityConfig::class.java.getDeclaredField("allowedEmailsProperty")
		field.isAccessible = true
		field.set(config, "info@manonworld.de,admin@example.com")
		handler = config.CookieIssuingSuccessHandler()
	}

	@Test
	fun `sets temp_email cookie and redirects to login-success endpoint`() {
		val exchange = createExchange("/login/oauth2/code/google")
		val authentication = MockAuthentication("info@manonworld.de")

		handler.onAuthenticationSuccess(exchange, authentication).block()

		val setCookieHeaders = exchange.exchange.response.headers.get("Set-Cookie") ?: emptyList()
		assertTrue(setCookieHeaders.any { it.contains("temp_email=") })
		assertEquals(HttpStatus.FOUND, exchange.exchange.response.statusCode)
		assertEquals("/api/auth/login-success", exchange.exchange.response.headers.location?.path)
	}

	@Test
	fun `sets temp_email cookie and redirects to login-success with refresh param`() {
		val exchange = createExchange("/login/oauth2/code/google?refresh=true")
		val authentication = MockAuthentication("info@manonworld.de")

		handler.onAuthenticationSuccess(exchange, authentication).block()

		assertEquals("/api/auth/login-success", exchange.exchange.response.headers.location?.path)
		assertTrue(exchange.exchange.response.headers.location?.query?.contains("refresh=true") == true)
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
		val emptyConfig = SecurityConfig(UserActionLogBuffer(), jwtService, JwtAuthenticationConverter(jwtService))
		val field = SecurityConfig::class.java.getDeclaredField("allowedEmailsProperty")
		field.isAccessible = true
		field.set(emptyConfig, "")
		val emptyHandler = emptyConfig.CookieIssuingSuccessHandler()
		val exchange = createExchange("/login/oauth2/code/google")
		val authentication = MockAuthentication("anyone@example.com")

		emptyHandler.onAuthenticationSuccess(exchange, authentication).block()

		assertEquals(HttpStatus.FOUND, exchange.exchange.response.statusCode)
		assertEquals(true, exchange.exchange.response.headers.location?.query?.contains("error=access_denied") == true)
	}

	@Test
	fun `redirects to login-success for second allowed email`() {
		val exchange = createExchange("/login/oauth2/code/google")
		val authentication = MockAuthentication("admin@example.com")

		handler.onAuthenticationSuccess(exchange, authentication).block()

		assertEquals(HttpStatus.FOUND, exchange.exchange.response.statusCode)
		assertEquals("/api/auth/login-success", exchange.exchange.response.headers.location?.path)
	}

	@Test
	fun `sets temp_email cookie with correct email`() {
		val exchange = createExchange("/login/oauth2/code/google")
		val authentication = MockAuthentication("info@manonworld.de")

		handler.onAuthenticationSuccess(exchange, authentication).block()

		val setCookieHeaders = exchange.exchange.response.headers.get("Set-Cookie") ?: emptyList()
		val tempCookie = setCookieHeaders.find { it.contains("temp_email=") }
		assertTrue(tempCookie != null)
		assertTrue(tempCookie!!.contains("info@manonworld.de"))
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
