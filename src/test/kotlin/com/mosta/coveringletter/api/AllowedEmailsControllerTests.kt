package com.mosta.coveringletter.api

import com.mosta.coveringletter.api.controller.AllowedEmailsController
import com.mosta.coveringletter.api.request.AllowedEmailsSetupRequest
import com.mosta.coveringletter.settings.AllowedEmailsService
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.springframework.http.HttpStatus
import org.springframework.mock.http.server.reactive.MockServerHttpResponse
import org.springframework.web.server.ResponseStatusException
import java.nio.file.Files
import java.nio.file.Path

class AllowedEmailsControllerTests {
	@TempDir
	lateinit var temporaryDirectory: Path

	@Test
	fun `lists configured emails without caching`() {
		val envFile = temporaryDirectory.resolve(".env")
		Files.writeString(envFile, "ALLOWED_EMAILS=z@example.com,a@example.com\n")
		val controller = AllowedEmailsController(allowedEmails(envFile))
		val response = MockServerHttpResponse()

		val result = controller.getAllowedEmails(response)

		assertEquals(listOf("a@example.com", "z@example.com"), result.emails)
		assertTrue(response.headers.cacheControl?.contains("no-store", ignoreCase = true) == true)
	}

	@Test
	fun `updates allowed emails in the env file`() {
		val envFile = temporaryDirectory.resolve(".env")
		Files.writeString(envFile, "GEMINI_API_KEY=keep-this\nALLOWED_EMAILS=old@example.com\n")
		val controller = AllowedEmailsController(allowedEmails(envFile))
		val response = MockServerHttpResponse()

		val result = controller.updateAllowedEmails(
			AllowedEmailsSetupRequest("New@example.com,admin@example.com"),
			response
		)

		assertEquals(listOf("admin@example.com", "new@example.com"), result.emails)
		assertEquals(
			listOf("GEMINI_API_KEY=keep-this", "ALLOWED_EMAILS=new@example.com,admin@example.com"),
			Files.readAllLines(envFile)
		)
		assertTrue(response.headers.cacheControl?.contains("no-store", ignoreCase = true) == true)
	}

	@Test
	fun `update endpoint remains unavailable before setup is complete`() {
		val controller = AllowedEmailsController(allowedEmails(temporaryDirectory.resolve(".env")))

		val exception = assertThrows(ResponseStatusException::class.java) {
			controller.updateAllowedEmails(AllowedEmailsSetupRequest("owner@example.com"), MockServerHttpResponse())
		}

		assertEquals(HttpStatus.NOT_FOUND.value(), exception.statusCode.value())
	}

	private fun allowedEmails(envFile: Path) = AllowedEmailsService("").also { it.envFile = envFile }
}