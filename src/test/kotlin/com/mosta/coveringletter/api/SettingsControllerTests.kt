package com.mosta.coveringletter.api

import com.mosta.coveringletter.api.controller.LoginSetupController
import com.mosta.coveringletter.api.controller.SettingsController
import com.mosta.coveringletter.api.request.AppSettingsRequest
import com.mosta.coveringletter.resume.ResumeStore
import com.mosta.coveringletter.resume.ResumeStatus
import com.mosta.coveringletter.settings.AllowedEmailsService
import com.mosta.coveringletter.settings.AppSettingsService
import com.mosta.coveringletter.settings.AppSettingsStatus
import com.mosta.coveringletter.settings.CurrentGeminiSettings
import com.mosta.coveringletter.documents.DocumentTextExtractor
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.springframework.mock.http.server.reactive.MockServerHttpResponse
import org.springframework.web.server.ResponseStatusException
import reactor.core.publisher.Mono
import java.nio.file.Files
import java.nio.file.Path

class SettingsControllerTests {
	private val principal = java.security.Principal { "owner@example.com" }

	@TempDir
	lateinit var temporaryDirectory: Path

	private lateinit var mockSettings: MockAppSettingsService
	private lateinit var resumeStore: ResumeStore
	private lateinit var allowedEmails: AllowedEmailsService
	private lateinit var controller: SettingsController

	@BeforeEach
	fun setup() {
		resumeStore = ResumeStore(DocumentTextExtractor(), temporaryDirectory.resolve("data").toString())
		mockSettings = MockAppSettingsService()
		val emailFile = temporaryDirectory.resolve(".env")
		Files.writeString(emailFile, "ALLOWED_EMAILS=owner@example.com\n")
		allowedEmails = AllowedEmailsService("owner@example.com").also { it.envFile = emailFile }
		controller = SettingsController(mockSettings, resumeStore, allowedEmails)
	}

	@Test
	fun `getSettings returns app settings status`() {
		val status = controller.getSettings()

		assertEquals(false, status.configured)
		assertEquals(false, status.apiKeyConfigured)
	}

	@Test
	fun `getCurrentSettings returns current Gemini settings with no-store cache header`() {
		val response = MockServerHttpResponse()
		val settings = controller.getCurrentSettings(response)

		assertEquals("gemini-3.1-flash-lite", settings.model)
		assertTrue(response.headers.cacheControl?.contains("no-store", ignoreCase = true) == true)
	}

	@Test
	fun `getCurrentSettings includes API key when configured`() {
		mockSettings.configuredApiKey = "test-api-key-123"

		val settings = controller.getCurrentSettings(MockServerHttpResponse())

		assertEquals("test-api-key-123", settings.apiKey)
		assertEquals("gemini-3.1-flash-lite", settings.model)
	}

	@Test
	fun `getCurrentSettings excludes blank API key`() {
		mockSettings.configuredApiKey = ""

		val settings = controller.getCurrentSettings(MockServerHttpResponse())

		assertTrue(settings.apiKey.isNullOrEmpty())
	}

	@Test
	fun `save settings includes allowed emails in the combined save`() {
		controller.saveSettings(
			AppSettingsRequest(
				apiKey = "",
				model = "gemini-3.1-flash-lite",
				allowedEmails = " NEW@example.com, owner@example.com,new@example.com "
			)
		).block()

		assertEquals(
			" NEW@example.com, owner@example.com,new@example.com ",
			mockSettings.savedAllowedEmails
		)
		assertEquals(
			listOf("ALLOWED_EMAILS=new@example.com,owner@example.com", "ADMIN_EMAIL=new@example.com"),
			Files.readAllLines(temporaryDirectory.resolve(".env"))
		)
	}

	@Test
	fun `save settings rejects invalid allowed emails before saving Gemini settings`() {
		val exception = org.junit.jupiter.api.Assertions.assertThrows(ResponseStatusException::class.java) {
			controller.saveSettings(
				AppSettingsRequest(
					apiKey = "",
					model = "gemini-3.1-flash-lite",
					allowedEmails = "invalid-email"
				)
			)
		}

		assertEquals(400, exception.statusCode.value())
		assertEquals(null, mockSettings.savedAllowedEmails)
	}

	@Test
	fun `only the administrator can change the allowed account list`() {
		val user = java.security.Principal { "user@example.com" }
		val exception = org.junit.jupiter.api.Assertions.assertThrows(ResponseStatusException::class.java) {
			controller.saveSettings(
				AppSettingsRequest(
					apiKey = "",
					model = "gemini-3.1-flash-lite",
					allowedEmails = "owner@example.com,user@example.com",
					adminEmail = "owner@example.com"
				),
				user
			)
		}

		assertEquals(403, exception.statusCode.value())
		assertEquals(null, mockSettings.savedAllowedEmails)
	}

	@Test
	fun `saving an empty allowed email list is rejected because an admin is required`() {
		val exception = org.junit.jupiter.api.Assertions.assertThrows(ResponseStatusException::class.java) {
			controller.saveSettings(
				AppSettingsRequest(apiKey = "", model = "gemini-3.1-flash-lite", allowedEmails = ""),
				principal
			)
		}

		assertEquals(400, exception.statusCode.value())
		assertEquals(null, mockSettings.savedAllowedEmails)
		assertTrue(allowedEmails.isConfigured())
	}

	@Test
	fun `reset settings removes local settings and allowed emails while preserving sign in credentials and model`() {
		Files.writeString(
			temporaryDirectory.resolve(".env"),
			"ALLOWED_EMAILS=owner@example.com\nGOOGLE_CLIENT_ID=client-id\nGOOGLE_CLIENT_SECRET=client-secret\nOTHER=value\n"
		)

		val result = controller.resetSettings()

		assertEquals(mapOf("status" to "reset"), result)
		assertTrue(mockSettings.resetCalled)
		assertFalse(allowedEmails.isConfigured())
		assertEquals(
			listOf("GOOGLE_CLIENT_ID=client-id", "GOOGLE_CLIENT_SECRET=client-secret", "GEMINI_MODEL=gemini-3.1-flash-lite"),
			Files.readAllLines(temporaryDirectory.resolve(".env"))
		)
		assertEquals(
			"allowed-emails-setup.html",
			LoginSetupController(allowedEmails).loginPage(MockServerHttpResponse()).filename
		)
	}

	// Mock implementation of AppSettingsService for testing
	private inner class MockAppSettingsService : AppSettingsService(
		tools.jackson.databind.ObjectMapper(),
		object : org.springframework.core.env.Environment {
			override fun getActiveProfiles() = emptyArray<String>()
			override fun getDefaultProfiles() = emptyArray<String>()
			override fun acceptsProfiles(vararg profiles: String) = false
			override fun acceptsProfiles(profiles: org.springframework.core.env.Profiles) = false
			override fun getProperty(name: String) = null
			override fun getProperty(key: String, defaultValue: String): String = defaultValue
			override fun <T : Any> getProperty(name: String, targetType: Class<T>) = null
			override fun <T : Any> getProperty(name: String, targetType: Class<T>, defaultValue: T) = defaultValue
			override fun getRequiredProperty(name: String) = throw IllegalStateException()
			override fun <T : Any> getRequiredProperty(name: String, targetType: Class<T>) = throw IllegalStateException()
			override fun containsProperty(name: String) = false
			override fun resolvePlaceholders(text: String) = text
			override fun resolveRequiredPlaceholders(text: String) = text
		}
	) {
		var configuredApiKey: String = ""
		var savedAllowedEmails: String? = null
		var resetCalled = false

		override fun apiKey(accountEmail: String?): String = configuredApiKey

		override fun resetApplicationSettings(accountEmail: String?): String {
			resetCalled = true
			return "gemini-3.1-flash-lite"
		}

		override fun save(request: AppSettingsRequest, accountEmail: String?): Mono<Unit> {
			savedAllowedEmails = request.allowedEmails
			return Mono.just(Unit)
		}
	}

	private fun SettingsController.getSettings() = getSettings(principal)

	private fun SettingsController.getCurrentSettings(response: MockServerHttpResponse) = getCurrentSettings(response, principal)

	private fun SettingsController.saveSettings(request: AppSettingsRequest) = saveSettings(request, principal)

	private fun SettingsController.resetSettings() = resetSettings(principal)
}
