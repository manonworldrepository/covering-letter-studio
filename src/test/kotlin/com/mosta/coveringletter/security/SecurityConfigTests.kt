package com.mosta.coveringletter.security

import com.mosta.coveringletter.activity.UserActionLogBuffer
import com.mosta.coveringletter.settings.AllowedEmailsService
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class SecurityConfigTests {

	@Test
	fun `allowed emails are trimmed and lowercased`() {
		val allowedEmails = AllowedEmailsService("  UPPER@EXAMPLE.COM  , lower@example.com ")
		assertTrue(allowedEmails.allows("upper@example.com"))
		assertTrue(allowedEmails.allows("LOWER@example.com"))
	}

	@Test
	fun `EmailCheckingSuccessHandler is correctly instantiated`() {
		val jwtService = JwtService(900, 604800, "", "").also { it.init() }
		val allowedEmails = AllowedEmailsService("person@example.com")
		val config = SecurityConfig(
			UserActionLogBuffer(),
			jwtService,
			JwtAuthenticationConverter(jwtService, allowedEmails),
			allowedEmails
		)
		val handler = config.EmailCheckingSuccessHandler()
		assertTrue(handler is SecurityConfig.EmailCheckingSuccessHandler)
	}

	@Test
	fun `LoginFailureHandler is correctly instantiated`() {
		val handler = SecurityConfig.LoginFailureHandler()
		assertTrue(handler is SecurityConfig.LoginFailureHandler)
	}
}
