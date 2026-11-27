package com.mosta.coveringletter.settings

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.springframework.http.HttpStatus
import org.springframework.web.server.ResponseStatusException
import java.nio.file.Files
import java.nio.file.Path

class AllowedEmailsServiceTests {
	@TempDir
	lateinit var temporaryDirectory: Path

	@Test
	fun `is not configured until an allowed email list exists`() {
		val service = service("")

		assertFalse(service.isConfigured())
		assertTrue(service.allowedEmails().isEmpty())
	}

	@Test
	fun `saves normalized emails and preserves other env values`() {
		val envFile = temporaryDirectory.resolve(".env")
		Files.writeString(envFile, "GEMINI_API_KEY=keep-this\nALLOWED_EMAILS=\n")
		val service = service("", envFile)

		service.save("  PERSON@GMAIL.COM , admin@example.com, person@gmail.com ")

		assertTrue(service.isConfigured())
		assertTrue(service.allows("person@gmail.com"))
		assertTrue(service.allows("ADMIN@example.com"))
		assertEquals(
			listOf("GEMINI_API_KEY=keep-this", "ALLOWED_EMAILS=person@gmail.com,admin@example.com"),
			Files.readAllLines(envFile)
		)
	}

	@Test
	fun `uses configured property before env file`() {
		val envFile = temporaryDirectory.resolve(".env")
		Files.writeString(envFile, "ALLOWED_EMAILS=from-file@example.com\n")
		val service = service("from-environment@example.com", envFile)

		assertTrue(service.allows("from-environment@example.com"))
		assertFalse(service.allows("from-file@example.com"))
	}

	@Test
	fun `rejects invalid addresses without writing env file`() {
		val envFile = temporaryDirectory.resolve(".env")
		val service = service("", envFile)

		val exception = assertThrows(ResponseStatusException::class.java) {
			service.save("valid@example.com,not-an-email")
		}

		assertEquals(HttpStatus.BAD_REQUEST, exception.statusCode)
		assertFalse(Files.exists(envFile))
	}

	@Test
	fun `setup is unavailable while a saved list exists`() {
		val service = service("")
		service.save("owner@example.com")

		val exception = assertThrows(ResponseStatusException::class.java) {
			service.save("someone-else@example.com")
		}

		assertEquals(HttpStatus.NOT_FOUND, exception.statusCode)
		assertTrue(service.allows("owner@example.com"))
		assertFalse(service.allows("someone-else@example.com"))
	}

	@Test
	fun `updates normalized emails and preserves other env values`() {
		val envFile = temporaryDirectory.resolve(".env")
		Files.writeString(envFile, "GEMINI_API_KEY=keep-this\nALLOWED_EMAILS=old@example.com\n")
		val service = service("", envFile)

		val updatedEmails = service.update(" OWNER@example.com , admin@example.com,owner@example.com ")

		assertEquals(setOf("owner@example.com", "admin@example.com"), updatedEmails)
		assertTrue(service.allows("OWNER@example.com"))
		assertTrue(service.allows("admin@example.com"))
		assertFalse(service.allows("old@example.com"))
		assertEquals(
			listOf("GEMINI_API_KEY=keep-this", "ALLOWED_EMAILS=owner@example.com,admin@example.com"),
			Files.readAllLines(envFile)
		)
	}

	@Test
	fun `updates active emails when configuration came from an environment property`() {
		val envFile = temporaryDirectory.resolve(".env")
		val service = service("owner@example.com", envFile)

		service.update("new-owner@example.com")

		assertTrue(service.allows("new-owner@example.com"))
		assertFalse(service.allows("owner@example.com"))
		assertEquals("ALLOWED_EMAILS=new-owner@example.com", Files.readString(envFile).trim())
	}

	@Test
	fun `settings can clear the active allowlist`() {
		val envFile = temporaryDirectory.resolve(".env")
		val service = service("owner@example.com", envFile)

		val emails = service.update("", allowEmpty = true)

		assertTrue(emails.isEmpty())
		assertFalse(service.isConfigured())
		assertTrue(service.allowedEmails().isEmpty())
		assertEquals("ALLOWED_EMAILS=", Files.readString(envFile).trim())
	}

	@Test
	fun `reset removes allowed emails and unrelated values but preserves OAuth credentials and model`() {
		val envFile = temporaryDirectory.resolve(".env")
		Files.writeString(
			envFile,
			"ALLOWED_EMAILS=owner@example.com\nGOOGLE_CLIENT_ID=client-id\nGOOGLE_CLIENT_SECRET=client-secret\nGEMINI_API_KEY=remove-me\nOTHER=value\n"
		)
		val service = service("owner@example.com", envFile)

		service.reset("gemini-3.1-flash-lite")

		assertFalse(service.isConfigured())
		assertTrue(service.allowedEmails().isEmpty())
		assertEquals(
			listOf("GOOGLE_CLIENT_ID=client-id", "GOOGLE_CLIENT_SECRET=client-secret", "GEMINI_MODEL=gemini-3.1-flash-lite"),
			Files.readAllLines(envFile)
		)
	}

	@Test
	fun `rejects invalid updates without replacing the existing list`() {
		val envFile = temporaryDirectory.resolve(".env")
		Files.writeString(envFile, "ALLOWED_EMAILS=owner@example.com\n")
		val service = service("", envFile)

		val exception = assertThrows(ResponseStatusException::class.java) {
			service.update("new-owner@example.com,not-an-email")
		}

		assertEquals(HttpStatus.BAD_REQUEST, exception.statusCode)
		assertTrue(service.allows("owner@example.com"))
		assertEquals("ALLOWED_EMAILS=owner@example.com\n", Files.readString(envFile))
	}

	@Test
	fun `placeholder email is treated as unconfigured`() {
		val service = service("your.email@gmail.com")

		assertFalse(service.isConfigured())
	}

	private fun service(configuredEmails: String, envFile: Path = temporaryDirectory.resolve("missing.env")) =
		AllowedEmailsService(configuredEmails).also { it.envFile = envFile }
}