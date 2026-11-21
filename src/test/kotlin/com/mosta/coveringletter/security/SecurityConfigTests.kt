package com.mosta.coveringletter.security

import com.mosta.coveringletter.activity.UserActionLogBuffer
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class SecurityConfigTests {

	@Test
	fun `allowed emails are trimmed and lowercased`() {
		val allowedEmails = "  UPPER@EXAMPLE.COM  , lower@example.com ".split(",").map { it.trim().lowercase() }.toSet()
		assertTrue("upper@example.com" in allowedEmails)
		assertTrue("lower@example.com" in allowedEmails)
	}

	@Test
	fun `LoginSuccessHandler is correctly instantiated`() {
		val config = SecurityConfig(UserActionLogBuffer())
		val handler = config.LoginSuccessHandler()
		assertTrue(handler is SecurityConfig.LoginSuccessHandler)
	}

	@Test
	fun `LoginFailureHandler is correctly instantiated`() {
		val handler = SecurityConfig.LoginFailureHandler()
		assertTrue(handler is SecurityConfig.LoginFailureHandler)
	}
}
