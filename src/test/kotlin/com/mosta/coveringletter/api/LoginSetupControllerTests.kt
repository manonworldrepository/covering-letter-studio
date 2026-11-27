package com.mosta.coveringletter.api

import com.mosta.coveringletter.api.controller.LoginSetupController
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

class LoginSetupControllerTests {
	@TempDir
	lateinit var temporaryDirectory: Path

	@Test
	fun `login page serves setup before an allowed list is saved`() {
		val controller = controller()
		val response = MockServerHttpResponse()

		val page = controller.loginPage(response)

		assertEquals("allowed-emails-setup.html", page.filename)
		assertTrue(response.headers.cacheControl?.contains("no-store", ignoreCase = true) == true)
	}

	@Test
	fun `saving allowed emails shows login page afterwards`() {
		val controller = controller()

		val result = controller.saveAllowedEmails(AllowedEmailsSetupRequest("owner@gmail.com,admin@gmail.com"))
		val page = controller.loginPage(MockServerHttpResponse())

		assertEquals(true, result.configured)
		assertEquals("login.html", page.filename)
		assertEquals("ALLOWED_EMAILS=owner@gmail.com,admin@gmail.com", temporaryDirectory.resolve(".env").toFile().readText().trim())
	}

	@Test
	fun `setup submission endpoint is unavailable after saving allowed emails`() {
		val controller = controller()
		controller.saveAllowedEmails(AllowedEmailsSetupRequest("owner@gmail.com"))

		val exception = assertThrows(ResponseStatusException::class.java) {
			controller.saveAllowedEmails(AllowedEmailsSetupRequest("someone-else@gmail.com"))
		}

		assertEquals(HttpStatus.NOT_FOUND, exception.statusCode)
	}

	@Test
	fun `setup status endpoint is unavailable after saving allowed emails`() {
		val controller = controller()
		controller.saveAllowedEmails(AllowedEmailsSetupRequest("owner@gmail.com"))

		val exception = assertThrows(ResponseStatusException::class.java) {
			controller.setupStatus(MockServerHttpResponse())
		}

		assertEquals(HttpStatus.NOT_FOUND, exception.statusCode)
	}

	@Test
	fun `setup is available again after allowed emails are removed from env file`() {
		val controller = controller()
		controller.saveAllowedEmails(AllowedEmailsSetupRequest("owner@gmail.com"))
		Files.writeString(temporaryDirectory.resolve(".env"), "GEMINI_API_KEY=keep-this\n")

		val page = controller.loginPage(MockServerHttpResponse())
		val status = controller.setupStatus(MockServerHttpResponse())
		val result = controller.saveAllowedEmails(AllowedEmailsSetupRequest("new-owner@gmail.com"))

		assertEquals("allowed-emails-setup.html", page.filename)
		assertEquals(false, status.configured)
		assertTrue(result.configured)
		assertTrue(Files.readString(temporaryDirectory.resolve(".env")).contains("ALLOWED_EMAILS=new-owner@gmail.com"))
	}

	private fun controller(): LoginSetupController {
		val allowedEmails = AllowedEmailsService("").also { it.envFile = temporaryDirectory.resolve(".env") }
		return LoginSetupController(allowedEmails)
	}
}