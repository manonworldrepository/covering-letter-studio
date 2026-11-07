package com.mosta.coveringletter.activity

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class UserActionLogBufferTests {
	@Test
	fun `retains only the latest two hundred user actions`() {
		val buffer = UserActionLogBuffer()
		repeat(205) { buffer.record("action-$it") }

		val entries = buffer.latest()
		assertEquals(200, entries.size)
		assertEquals("action-5", entries.first().action)
		assertEquals("action-204", entries.last().action)
	}

	@Test
	fun `limits each action to two hundred characters`() {
		val buffer = UserActionLogBuffer()

		buffer.record("a".repeat(250))

		assertEquals(200, buffer.latest().single().action.length)
	}
}
