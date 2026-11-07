package com.mosta.coveringletter.generation

import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class GeminiFailureMessageTests {
	@Test
	fun `gives busy model guidance for service unavailable responses`() {
		val message = GeminiFailureMessage.fromHttpResponse(503, "provider busy")

		assertTrue(message.contains("busy or temporarily unavailable"))
		assertTrue(message.contains("Settings"))
		assertTrue(message.contains("another available Gemini model"))
	}

	@Test
	fun `adds model selection guidance to other Gemini response errors`() {
		val message = GeminiFailureMessage.fromHttpResponse(429, "Quota exceeded.")

		assertTrue(message.contains("Quota exceeded."))
		assertTrue(message.contains("Settings"))
		assertTrue(message.contains("another available Gemini model"))
	}
}
