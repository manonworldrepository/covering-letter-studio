package com.mosta.coveringletter.settings

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.springframework.mock.env.MockEnvironment
import tools.jackson.databind.ObjectMapper
import java.nio.file.Files
import java.nio.file.Path

class AppSettingsServiceTests {
	@TempDir
	lateinit var temporaryDirectory: Path

	@Test
	fun `reset keeps the selected model and removes the Gemini key from the settings file`() {
		val envFile = temporaryDirectory.resolve(".env")
		Files.writeString(
			envFile,
			"GEMINI_API_KEY=stored-key\nGEMINI_MODEL=gemini-2.0-pro\nOTHER_SETTING=remove-me\n"
		)
		val environment = MockEnvironment().withProperty("gemini.api-key", "environment-key")
		val settings = AppSettingsService(ObjectMapper(), environment).also { it.envFile = envFile }

		val preservedModel = settings.resetApplicationSettings()

		assertEquals("gemini-2.0-pro", preservedModel)
		assertEquals(listOf("GEMINI_MODEL=gemini-2.0-pro"), Files.readAllLines(envFile))
		assertTrue(settings.apiKey().isBlank())
		assertEquals("gemini-2.0-pro", settings.model())
	}
}