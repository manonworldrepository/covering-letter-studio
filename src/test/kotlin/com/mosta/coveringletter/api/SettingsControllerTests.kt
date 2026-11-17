package com.mosta.coveringletter.api

import com.mosta.coveringletter.activity.UserActionLogBuffer
import com.mosta.coveringletter.resume.ResumeStore
import com.mosta.coveringletter.resume.ResumeStatus
import com.mosta.coveringletter.settings.AppSettingsService
import com.mosta.coveringletter.settings.AppSettingsStatus
import com.mosta.coveringletter.settings.CurrentGeminiSettings
import com.mosta.coveringletter.documents.DocumentTextExtractor
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.springframework.mock.http.server.reactive.MockServerHttpResponse
import java.nio.file.Path

class SettingsControllerTests {

	@TempDir
	lateinit var temporaryDirectory: Path

	private lateinit var mockSettings: MockAppSettingsService
	private lateinit var resumeStore: ResumeStore
	private val logs = UserActionLogBuffer()
	private lateinit var controller: SettingsController

	@BeforeEach
	fun setup() {
		resumeStore = ResumeStore(DocumentTextExtractor(), temporaryDirectory.resolve("data").toString())
		mockSettings = MockAppSettingsService()
		controller = SettingsController(mockSettings, resumeStore, logs)
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

		override fun apiKey(): String = configuredApiKey
	}
}
