package com.mosta.coveringletter.security

import com.mosta.coveringletter.activity.UserActionLogBuffer
import com.mosta.coveringletter.settings.AllowedEmailsService
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.springframework.http.HttpStatus
import org.springframework.mock.http.server.reactive.MockServerHttpRequest
import org.springframework.mock.web.server.MockServerWebExchange
import org.springframework.security.core.Authentication
import org.springframework.security.core.GrantedAuthority
import org.springframework.security.web.server.WebFilterExchange
import org.springframework.web.server.WebFilterChain
import java.nio.file.Path

class LoginSuccessHandlerTests {

	private val userActions = UserActionLogBuffer()
	private val jwtService = JwtService(900, 604800, "", "").also { it.init() }
	@TempDir
	lateinit var temporaryDirectory: Path
	private lateinit var handler: SecurityConfig.EmailCheckingSuccessHandler

	@BeforeEach
	fun setup() {
		val config = createConfig("  person@gmail.com  , admin@example.com ")
		handler = config.EmailCheckingSuccessHandler()
	}

	@Test
	fun `sets JWT cookies and redirects for allowed email`() {
		val exchange = createExchange("/login/oauth2/code/google")
		val authentication = MockAuthentication("person@gmail.com")

		handler.onAuthenticationSuccess(exchange, authentication).block()

		assertEquals(HttpStatus.FOUND, exchange.exchange.response.statusCode)
		assertEquals("/", exchange.exchange.response.headers.location?.path)
	}

	@Test
	fun `sets JWT cookies and redirects case insensitive`() {
		val exchange = createExchange("/login/oauth2/code/google")
		val authentication = MockAuthentication("PERSON@GMAIL.COM")

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
		assertEquals("/login", exchange.exchange.response.headers.location?.path)
		assertEquals(true, exchange.exchange.response.headers.location?.query?.contains("error=access_denied") == true)
	}

	@Test
	fun `redirects to access denied for empty allowed list`() {
		val emptyConfig = createConfig("")
		val emptyHandler = emptyConfig.EmailCheckingSuccessHandler()
		val exchange = createExchange("/login/oauth2/code/google")
		val authentication = MockAuthentication("anyone@example.com")

		emptyHandler.onAuthenticationSuccess(exchange, authentication).block()

		assertEquals(HttpStatus.FOUND, exchange.exchange.response.statusCode)
		assertEquals(true, exchange.exchange.response.headers.location?.query?.contains("error=access_denied") == true)
	}

	@Test
	fun `sets JWT cookies for second allowed email`() {
		val exchange = createExchange("/login/oauth2/code/google")
		val authentication = MockAuthentication("admin@example.com")

		handler.onAuthenticationSuccess(exchange, authentication).block()

		assertEquals(HttpStatus.FOUND, exchange.exchange.response.statusCode)
		assertEquals("/", exchange.exchange.response.headers.location?.path)
	}

	@Test
	fun `records session refresh action log`() {
		val exchange = createExchange("/login/oauth2/code/google?refresh=true")
		val authentication = MockAuthentication("person@gmail.com")

		handler.onAuthenticationSuccess(exchange, authentication).block()

		val logs = userActions.latest("person@gmail.com")
		assertTrue(logs.any { it.action.contains("Session refreshed") })
	}

	private fun createExchange(path: String): WebFilterExchange {
		val request = MockServerHttpRequest.get(path).build()
		val serverExchange = MockServerWebExchange.builder(request).build()
		val chain = object : WebFilterChain {
			override fun filter(exchange: org.springframework.web.server.ServerWebExchange): reactor.core.publisher.Mono<Void> = reactor.core.publisher.Mono.empty()
		}
		return WebFilterExchange(serverExchange, chain)
	}

	private fun createConfig(allowedEmails: String): SecurityConfig {
		val allowedEmailsService = AllowedEmailsService(allowedEmails).also {
			it.envFile = temporaryDirectory.resolve("missing.env")
		}
		return SecurityConfig(
			userActions,
			jwtService,
			JwtAuthenticationConverter(jwtService, allowedEmailsService),
			allowedEmailsService
		)
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
