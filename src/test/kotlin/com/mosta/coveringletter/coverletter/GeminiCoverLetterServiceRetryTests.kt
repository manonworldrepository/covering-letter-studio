package com.mosta.coveringletter.coverletter

import com.mosta.coveringletter.generation.GeminiGenerationClientSupport
import com.mosta.coveringletter.generation.GeminiModelFallback
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.http.HttpStatus
import org.springframework.web.server.ResponseStatusException
import reactor.core.publisher.Mono

class GeminiCoverLetterServiceRetryTests {

	@Test
	fun `max retries constant is set to 2`() {
		assertEquals(2, GeminiGenerationClientSupport.MAX_RETRIES)
	}

	@Test
	fun `retryable errors include connection failures`() {
		assertTrue(GeminiModelFallback.isRetryable(RuntimeException("connection reset")))
	}

	@Test
	fun `non-retryable errors include auth failures`() {
		assertFalse(
			GeminiModelFallback.isRetryable(
				ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid API key")
			)
		)
	}

	@Test
	fun `generator propagates error without retry when not using WebClient`() {
		var callCount = 0
		val generator: CoverLetterGenerator = object : CoverLetterGenerator {
			override fun generate(
				jobDescription: String,
				resume: String,
				supportingDocuments: String,
				format: GenerationFormat
			): Mono<GeneratedCoverLetter> {
				callCount++
				return Mono.error(RuntimeException("connection reset"))
			}
		}

		val error = org.junit.jupiter.api.Assertions.assertThrows(RuntimeException::class.java) {
			generator.generate("Software Engineer", "John Doe resume", "", GenerationFormat.COVER_LETTER)
				.block()
		}

		assertEquals("connection reset", error.message)
		assertEquals(1, callCount)
	}
}
