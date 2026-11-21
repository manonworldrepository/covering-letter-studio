package com.mosta.coveringletter.security

import com.mosta.coveringletter.activity.UserActionLogBuffer
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.security.oauth2.core.AuthorizationGrantType
import org.springframework.security.oauth2.core.ClientAuthenticationMethod

class SecurityConfigTests {

	private lateinit var securityConfig: SecurityConfig

	@BeforeEach
	fun setup() {
		securityConfig = SecurityConfig(
			allowedEmailsProperty = "info@manonworld.de, admin@example.com",
			clientId = "test-client-id.apps.googleusercontent.com",
			clientSecret = "test-client-secret",
			userActions = UserActionLogBuffer()
		)
	}

	@Test
	fun `clientRegistrationRepository creates Google registration`() {
		val repo = securityConfig.clientRegistrationRepository()
		val registration = repo.findByRegistrationId("google").block()

		assertEquals("google", registration?.registrationId)
		assertEquals("test-client-id.apps.googleusercontent.com", registration?.clientId)
		assertEquals("test-client-secret", registration?.clientSecret)
	}

	@Test
	fun `clientRegistration uses correct authentication method`() {
		val repo = securityConfig.clientRegistrationRepository()
		val registration = repo.findByRegistrationId("google").block()

		assertEquals(ClientAuthenticationMethod.CLIENT_SECRET_POST, registration?.clientAuthenticationMethod)
	}

	@Test
	fun `clientRegistration uses authorization code grant type`() {
		val repo = securityConfig.clientRegistrationRepository()
		val registration = repo.findByRegistrationId("google").block()

		assertEquals(AuthorizationGrantType.AUTHORIZATION_CODE, registration?.authorizationGrantType)
	}

	@Test
	fun `clientRegistration has correct Google URIs`() {
		val repo = securityConfig.clientRegistrationRepository()
		val registration = repo.findByRegistrationId("google").block()

		assertEquals("https://accounts.google.com/o/oauth2/v2/auth", registration?.providerDetails?.authorizationUri?.toString())
		assertEquals("https://oauth2.googleapis.com/token", registration?.providerDetails?.tokenUri?.toString())
	}

	@Test
	fun `clientRegistration has correct scopes`() {
		val repo = securityConfig.clientRegistrationRepository()
		val registration = repo.findByRegistrationId("google").block()

		assertTrue(registration?.scopes?.contains("email") == true)
		assertTrue(registration?.scopes?.contains("profile") == true)
		assertTrue(registration?.scopes?.contains("openid") == true)
	}

	@Test
	fun `clientRegistration uses email as user name attribute`() {
		val repo = securityConfig.clientRegistrationRepository()
		val registration = repo.findByRegistrationId("google").block()

		assertEquals("email", registration?.providerDetails?.userInfoEndpoint?.userNameAttributeName)
	}

	@Test
	fun `clientRegistration has correct redirect URI`() {
		val repo = securityConfig.clientRegistrationRepository()
		val registration = repo.findByRegistrationId("google").block()

		assertEquals("https://coveringletter-ga7tig2oka-uc.a.run.app/login/oauth2/code/google", registration?.redirectUri)
	}

	@Test
	fun `single allowed email is parsed correctly`() {
		val singleConfig = SecurityConfig(
			allowedEmailsProperty = "single@example.com",
			clientId = "client-id",
			clientSecret = "client-secret",
			userActions = UserActionLogBuffer()
		)
		val repo = singleConfig.clientRegistrationRepository()
		val registration = repo.findByRegistrationId("google").block()

		assertEquals("client-id", registration?.clientId)
	}

	@Test
	fun `allowed emails are trimmed and lowercased`() {
		val allowedEmails = "  UPPER@EXAMPLE.COM  , lower@example.com ".split(",").map { it.trim().lowercase() }.toSet()
		assertEquals(setOf("upper@example.com", "lower@example.com"), allowedEmails)
	}

	@Test
	fun `LoginSuccessHandler is correctly instantiated`() {
		val config = SecurityConfig(
			allowedEmailsProperty = "info@manonworld.de,admin@example.com",
			clientId = "test",
			clientSecret = "test",
			userActions = UserActionLogBuffer()
		)
		val handler = config.LoginSuccessHandler(setOf("info@manonworld.de", "admin@example.com"))
		assertTrue(handler is SecurityConfig.LoginSuccessHandler)
	}

	@Test
	fun `LoginFailureHandler is correctly instantiated`() {
		val handler = SecurityConfig.LoginFailureHandler()
		assertTrue(handler is SecurityConfig.LoginFailureHandler)
	}
}
