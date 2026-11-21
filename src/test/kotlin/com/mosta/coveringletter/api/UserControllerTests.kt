package com.mosta.coveringletter.api

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.security.core.Authentication
import org.springframework.security.core.GrantedAuthority
import org.springframework.security.core.context.SecurityContext
import org.springframework.security.oauth2.core.user.DefaultOAuth2User
import org.springframework.security.oauth2.core.user.OAuth2User
import reactor.core.publisher.Mono

class UserControllerTests {

	@Test
	fun `getCurrentUser returns email and name from OAuth2 user`() {
		val controller = UserController(clientId = "test-client-id")
		val oAuth2User = createMockOAuth2User(
			email = "test@example.com",
			name = "Test User",
			givenName = "Test"
		)
		val authentication = createMockAuthentication("test@example.com", oAuth2User)
		val securityContext = createMockSecurityContext(authentication)

		val result = controller.getCurrentUser(Mono.just(securityContext)).block()

		assertEquals("test@example.com", result?.email)
		assertEquals("Test User", result?.name)
	}

	@Test
	fun `getCurrentUser falls back to given_name when name is null`() {
		val controller = UserController(clientId = "test-client-id")
		val oAuth2User = createMockOAuth2User(
			email = "test@example.com",
			name = null,
			givenName = "Given Name"
		)
		val authentication = createMockAuthentication("test@example.com", oAuth2User)
		val securityContext = createMockSecurityContext(authentication)

		val result = controller.getCurrentUser(Mono.just(securityContext)).block()

		assertEquals("test@example.com", result?.email)
		assertEquals("Given Name", result?.name)
	}

	@Test
	fun `getCurrentUser falls back to email when both name and given_name are null`() {
		val controller = UserController(clientId = "test-client-id")
		val oAuth2User = createMockOAuth2User(
			email = "test@example.com",
			name = null,
			givenName = null
		)
		val authentication = createMockAuthentication("test@example.com", oAuth2User)
		val securityContext = createMockSecurityContext(authentication)

		val result = controller.getCurrentUser(Mono.just(securityContext)).block()

		assertEquals("test@example.com", result?.email)
		assertEquals("test@example.com", result?.name)
	}

	@Test
	fun `getCurrentUser throws when authentication is null`() {
		val controller = UserController(clientId = "test-client-id")
		val securityContext = createMockSecurityContext(null)

		assertThrows<org.springframework.security.access.AccessDeniedException> {
			controller.getCurrentUser(Mono.just(securityContext)).block()
		}
	}

	@Test
	fun `getCurrentUser uses email as name when principal is not OAuth2User`() {
		val controller = UserController(clientId = "test-client-id")
		val authentication = createMockAuthentication("simple@example.com", "simple@example.com")
		val securityContext = createMockSecurityContext(authentication)

		val result = controller.getCurrentUser(Mono.just(securityContext)).block()

		assertEquals("simple@example.com", result?.email)
		assertEquals("simple@example.com", result?.name)
	}

	private fun createMockOAuth2User(email: String, name: String?, givenName: String?): OAuth2User {
		val attrs = mutableMapOf<String, Any>("email" to email)
		if (name != null) attrs["name"] = name
		if (givenName != null) attrs["given_name"] = givenName
		return DefaultOAuth2User(emptyList<GrantedAuthority>(), attrs, "email")
	}

	private fun createMockAuthentication(name: String, principal: Any): Authentication {
		return object : Authentication {
			override fun getName(): String = name
			override fun getPrincipal(): Any = principal
			override fun getCredentials(): Any = ""
			override fun getDetails(): Any? = null
			override fun getAuthorities(): Collection<GrantedAuthority> = emptyList()
			override fun isAuthenticated(): Boolean = true
			override fun setAuthenticated(isAuthenticated: Boolean) {}
		}
	}

	private fun createMockSecurityContext(authentication: Authentication?): SecurityContext {
		return object : SecurityContext {
			override fun getAuthentication(): Authentication? = authentication
			override fun setAuthentication(auth: Authentication?) {}
		}
	}
}
