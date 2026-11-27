package com.mosta.coveringletter.aspects

import com.mosta.coveringletter.activity.UserActionLogBuffer
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class GeminiServiceLoggingAspectTests {

	private val userActions = UserActionLogBuffer()
	private lateinit var aspect: GeminiServiceLoggingAspect

	@BeforeEach
	fun setup() {
		aspect = GeminiServiceLoggingAspect(userActions)
	}

	@Test
	fun `logModelContacted records contacting Gemini message`() {
		aspect.logModelContacted("gemini-3.1-flash-lite")

		val entries = userActions.latest()
		assertEquals(1, entries.size)
		assertEquals("Contacting Gemini with model gemini-3.1-flash-lite", entries.single().action)
	}

	@Test
	fun `logModelReplied records reply received message`() {
		aspect.logModelReplied("gemini-3.1-flash-lite")

		val entries = userActions.latest()
		assertEquals(1, entries.size)
		assertEquals("Received a reply from Gemini using model gemini-3.1-flash-lite", entries.single().action)
	}

	@Test
	fun `logModelSwitched records model switch message`() {
		aspect.logModelSwitched("gemini-3.1-flash")

		val entries = userActions.latest()
		assertEquals(1, entries.size)
		assertEquals("Switched to Gemini model gemini-3.1-flash after a previous model failed", entries.single().action)
	}

	@Test
	fun `logModelFailed records model failure message`() {
		aspect.logModelFailed("gemini-3.1-flash-lite")

		val entries = userActions.latest()
		assertEquals(1, entries.size)
		assertEquals("Gemini model gemini-3.1-flash-lite failed to respond; trying another model", entries.single().action)
	}

	@Test
	fun `logRetry records retry message with attempt count and reason`() {
		aspect.logRetry("gemini-3.1-flash-lite", 1, 2, "Network error")

		val entries = userActions.latest()
		assertEquals(1, entries.size)
		assertEquals("Retry 1/2: Network error", entries.single().action)
	}

	@Test
	fun `logRetry records second retry with updated attempt`() {
		aspect.logRetry("gemini-3.1-flash-lite", 1, 2, "Network error")
		aspect.logRetry("gemini-3.1-flash-lite", 2, 2, "Gemini is busy (rate limited)")

		val entries = userActions.latest()
		assertEquals(2, entries.size)
		assertEquals("Retry 1/2: Network error", entries[0].action)
		assertEquals("Retry 2/2: Gemini is busy (rate limited)", entries[1].action)
	}

	@Test
	fun `logCoverLetterGenerated records full cover letter format`() {
		aspect.logCoverLetterGenerated("Generated a full cover letter")

		val entries = userActions.latest()
		assertEquals(1, entries.size)
		assertEquals("Generated a full cover letter", entries.single().action)
	}

	@Test
	fun `logCoverLetterGenerated records motivation message format`() {
		aspect.logCoverLetterGenerated("Generated a motivation message")

		val entries = userActions.latest()
		assertEquals(1, entries.size)
		assertEquals("Generated a motivation message", entries.single().action)
	}

	@Test
	fun `logResumeTailored records tailored resume action`() {
		aspect.logResumeTailored()

		val entries = userActions.latest()
		assertEquals(1, entries.size)
		assertEquals("Tailored a resume", entries.single().action)
	}

	@Test
	fun `multiple Gemini actions are recorded in order`() {
		aspect.logModelContacted("gemini-3.1-flash-lite")
		aspect.logModelReplied("gemini-3.1-flash-lite")
		aspect.logCoverLetterGenerated("Generated a full cover letter")

		val entries = userActions.latest()
		assertEquals(3, entries.size)
		assertTrue(entries[0].action.contains("Contacting Gemini"))
		assertTrue(entries[1].action.contains("Received a reply"))
		assertTrue(entries[2].action.contains("Generated a full cover letter"))
	}

	@Test
	fun `model failure followed by switch records both entries`() {
		aspect.logModelFailed("gemini-3.1-flash-lite")
		aspect.logModelSwitched("gemini-3.1-flash")
		aspect.logModelContacted("gemini-3.1-flash")

		val entries = userActions.latest()
		assertEquals(3, entries.size)
		assertTrue(entries[0].action.contains("failed to respond"))
		assertTrue(entries[1].action.contains("Switched to Gemini model gemini-3.1-flash"))
		assertTrue(entries[2].action.contains("Contacting Gemini with model gemini-3.1-flash"))
	}
}
