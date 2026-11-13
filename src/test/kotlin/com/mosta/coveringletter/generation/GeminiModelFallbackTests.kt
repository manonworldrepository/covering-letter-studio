package com.mosta.coveringletter.generation

import com.mosta.coveringletter.settings.AppSettingsServiceDefaults
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.web.reactive.function.client.WebClientResponseException
import org.springframework.web.server.ResponseStatusException

class GeminiModelFallbackTests {
	@Test
	fun `puts preferred model first and limits attempts`() {
		val candidates = GeminiModelFallback.candidates(
			"gemini-pro",
			listOf(
				"gemini-2.5-pro",
				"gemini-2.5-flash",
				"gemini-2.5-flash-lite",
				AppSettingsServiceDefaults.DEFAULT_MODEL,
				"gemini-pro"
			)
		)

		assertEquals("gemini-pro", candidates.first())
		assertTrue(candidates.size <= GeminiModelFallback.MAX_ATTEMPTS)
		assertTrue(candidates.contains(AppSettingsServiceDefaults.DEFAULT_MODEL) || candidates.size == 1)
	}

	@Test
	fun `falls back to default when available list is empty`() {
		val candidates = GeminiModelFallback.candidates("custom-model", emptyList())

		assertEquals(
			listOf("custom-model", AppSettingsServiceDefaults.DEFAULT_MODEL).take(GeminiModelFallback.MAX_ATTEMPTS),
			candidates
		)
	}

	@Test
	fun `prefers flash lite models among fallbacks`() {
		val candidates = GeminiModelFallback.candidates(
			"broken-model",
			listOf("gemini-2.5-pro", "gemini-2.5-flash", "gemini-2.5-flash-lite")
		)

		assertEquals("broken-model", candidates[0])
		assertTrue(candidates.drop(1).first().contains("flash", ignoreCase = true))
	}

	@Test
	fun `retries transient provider and empty-response failures`() {
		assertTrue(
			GeminiModelFallback.isRetryable(
				WebClientResponseException.create(503, "Unavailable", HttpHeaders.EMPTY, ByteArray(0), null)
			)
		)
		assertTrue(
			GeminiModelFallback.isRetryable(
				ResponseStatusException(HttpStatus.BAD_GATEWAY, "empty response")
			)
		)
		assertTrue(GeminiModelFallback.isRetryable(RuntimeException("connection reset")))
	}

	@Test
	fun `does not retry auth or bad-request failures`() {
		assertFalse(
			GeminiModelFallback.isRetryable(
				WebClientResponseException.create(401, "Unauthorized", HttpHeaders.EMPTY, ByteArray(0), null)
			)
		)
		assertFalse(
			GeminiModelFallback.isRetryable(
				ResponseStatusException(HttpStatus.BAD_REQUEST, "invalid prompt")
			)
		)
	}
}
