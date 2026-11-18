package com.mosta.coveringletter.generation

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class GeminiFailureMessageTests {
	@Test
	fun `gives busy model guidance for service unavailable responses`() {
		val message = GeminiFailureMessage.fromHttpResponse(503, "provider busy")

		assertTrue(message.contains("busy or temporarily unavailable"))
		assertTrue(message.contains("Settings"))
		assertTrue(message.contains("other available Gemini models"))
	}

	@Test
	fun `adds model selection guidance to other Gemini response errors`() {
		val message = GeminiFailureMessage.fromHttpResponse(429, "Quota exceeded.")

		assertTrue(message.contains("Quota exceeded."))
		assertTrue(message.contains("Settings"))
		assertTrue(message.contains("other available Gemini models"))
	}

	@Test
	fun `handles empty provider message with generic fallback`() {
		val message = GeminiFailureMessage.fromHttpResponse(400, "")

		assertTrue(message.contains("Gemini could not process the request"))
		assertTrue(message.contains("HTTP 400"))
	}

	@Test
	fun `includes provider message for rate limited responses`() {
		val message = GeminiFailureMessage.fromHttpResponse(429, "Rate limit exceeded")

		assertTrue(message.contains("Rate limit exceeded"))
	}

	@Test
	fun `includes provider message for server error responses`() {
		val message = GeminiFailureMessage.fromHttpResponse(500, "Internal error")

		assertTrue(message.contains("Internal error"))
	}

	@Test
	fun `model switch advice contains settings guidance`() {
		assertTrue(GeminiFailureMessage.modelSwitchAdvice.contains("Settings"))
		assertTrue(GeminiFailureMessage.modelSwitchAdvice.contains("other available Gemini models"))
	}
}
