package com.mosta.coveringletter.settings

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class AppSettingsServiceDefaultsTests {

	@Test
	fun `default model is gemini flash lite`() {
		assertEquals("gemini-3.1-flash-lite", AppSettingsServiceDefaults.DEFAULT_MODEL)
	}

	@Test
	fun `model id regex accepts valid model names`() {
		assertTrue(AppSettingsServiceDefaults.MODEL_ID.matches("gemini-3.1-flash-lite"))
		assertTrue(AppSettingsServiceDefaults.MODEL_ID.matches("gemini-pro"))
		assertTrue(AppSettingsServiceDefaults.MODEL_ID.matches("gemini_2.0-pro"))
		assertTrue(AppSettingsServiceDefaults.MODEL_ID.matches("gemini.2.0.pro"))
	}

	@Test
	fun `model id regex rejects invalid characters`() {
		assertTrue(!AppSettingsServiceDefaults.MODEL_ID.matches("gemini/pro"))
		assertTrue(!AppSettingsServiceDefaults.MODEL_ID.matches("gemini pro"))
		assertTrue(!AppSettingsServiceDefaults.MODEL_ID.matches("gemini@pro"))
		assertTrue(!AppSettingsServiceDefaults.MODEL_ID.matches(""))
	}

	@Test
	fun `env line regex parses key value pairs`() {
		val match = AppSettingsServiceDefaults.ENV_LINE.matchEntire("GEMINI_API_KEY=abc123")
		assertTrue(match != null)
		assertEquals("GEMINI_API_KEY", match!!.groupValues[1])
		assertEquals("abc123", match.groupValues[2])
	}

	@Test
	fun `env line regex handles quoted values`() {
		val match = AppSettingsServiceDefaults.ENV_LINE.matchEntire("GEMINI_MODEL=\"gemini-3.1-flash-lite\"")
		assertTrue(match != null)
		assertEquals("GEMINI_MODEL", match!!.groupValues[1])
		assertEquals("\"gemini-3.1-flash-lite\"", match.groupValues[2])
	}

	@Test
	fun `env line regex handles whitespace around equals`() {
		val match = AppSettingsServiceDefaults.ENV_LINE.matchEntire("  GEMINI_API_KEY   =   abc123  ")
		assertTrue(match != null)
		assertEquals("GEMINI_API_KEY", match!!.groupValues[1])
	}

	@Test
	fun `env line regex rejects invalid lines`() {
		assertTrue(AppSettingsServiceDefaults.ENV_LINE.matchEntire("=value") == null)
		assertTrue(AppSettingsServiceDefaults.ENV_LINE.matchEntire("123KEY=value") == null)
	}

	@Test
	fun `env file path is inside data directory`() {
		assertTrue(AppSettingsServiceDefaults.envFile.isAbsolute)
		assertEquals(".env", AppSettingsServiceDefaults.envFile.fileName.toString())
		assertEquals(".data", AppSettingsServiceDefaults.envFile.parent?.fileName?.toString())
	}
}
