package com.mosta.coveringletter.settings

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.springframework.mock.env.MockEnvironment
import com.mosta.coveringletter.persistence.AccountDataDirectories
import com.mosta.coveringletter.resume.ResumeStatus
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

	@Test
	fun `Gemini settings are isolated by account and do not inherit the shared API key`() {
		val sharedEnvFile = temporaryDirectory.resolve(".env")
		Files.writeString(sharedEnvFile, "GEMINI_API_KEY=legacy-shared-key\nGEMINI_MODEL=gemini-shared\n")
		val settings = AppSettingsService(ObjectMapper(), MockEnvironment().withProperty("gemini.api-key", "deployment-key"))
			.also { it.envFile = sharedEnvFile }
		val accounts = AccountDataDirectories(temporaryDirectory)
		val aliceEnv = accounts.forEmail("alice@example.com").resolve(".env")
		val bobEnv = accounts.forEmail("bob@example.com").resolve(".env")
		Files.createDirectories(aliceEnv.parent)
		Files.createDirectories(bobEnv.parent)
		Files.writeString(aliceEnv, "GEMINI_API_KEY=alice-key\nGEMINI_MODEL=gemini-alice\n")
		Files.writeString(bobEnv, "GEMINI_API_KEY=bob-key\nGEMINI_MODEL=gemini-bob\n")

		assertEquals("alice-key", settings.currentSettings("alice@example.com").apiKey)
		assertEquals("gemini-alice", settings.model("alice@example.com"))
		assertEquals("bob-key", settings.currentSettings("bob@example.com").apiKey)
		assertEquals("", settings.apiKey("unconfigured@example.com"))
		assertEquals("legacy-shared-key", settings.currentSettings().apiKey)
		assertTrue(!settings.status(ResumeStatus(false, null), "unconfigured@example.com").apiKeyConfigured)
	}
}