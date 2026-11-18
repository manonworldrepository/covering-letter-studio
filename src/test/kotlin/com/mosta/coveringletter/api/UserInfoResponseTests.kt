package com.mosta.coveringletter.api

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class UserInfoResponseTests {

	@Test
	fun `UserInfoResponse creates with email and name`() {
		val response = UserInfoResponse(email = "test@example.com", name = "Test User")

		assertEquals("test@example.com", response.email)
		assertEquals("Test User", response.name)
	}

	@Test
	fun `UserInfoResponse equals and hashCode work correctly`() {
		val response1 = UserInfoResponse(email = "test@example.com", name = "Test User")
		val response2 = UserInfoResponse(email = "test@example.com", name = "Test User")
		val response3 = UserInfoResponse(email = "other@example.com", name = "Other User")

		assertEquals(response1, response2)
		assertEquals(response1.hashCode(), response2.hashCode())
		assertEquals(response1, response1)
		assertEquals(false, response1 == response3)
	}

	@Test
	fun `UserInfoResponse toString contains email and name`() {
		val response = UserInfoResponse(email = "test@example.com", name = "Test User")

		val str = response.toString()
		assertEquals(true, str.contains("test@example.com"))
		assertEquals(true, str.contains("Test User"))
	}

	@Test
	fun `UserInfoResponse copy creates modified copy`() {
		val original = UserInfoResponse(email = "test@example.com", name = "Test User")
		val copied = original.copy(name = "New Name")

		assertEquals("test@example.com", copied.email)
		assertEquals("New Name", copied.name)
		assertEquals("Test User", original.name)
	}
}
