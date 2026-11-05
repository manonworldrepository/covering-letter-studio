package com.mosta.coveringletter

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.springframework.http.HttpStatus
import org.springframework.web.server.ResponseStatusException
import java.nio.file.Files
import java.nio.file.Path

class ResumeStoreTests {
	@TempDir
	lateinit var temporaryDirectory: Path

	private val pdfGenerator = CoverLetterPdfGenerator()

	@Test
	fun `stores and reports the original uploaded filename`() {
		val store = store()
		val filename = "Alex Morgan - Resume 2026.pdf"
		val bytes = pdfGenerator.create("Alex Morgan. Product designer resume.")

		val saved = store.saveBytes(filename, bytes)

		assertEquals(ResumeStatus(true, filename), saved)
		assertEquals(saved, store.status())
		assertTrue(Files.isRegularFile(temporaryDirectory.resolve("data").resolve(filename)))
		assertTrue(store.extractStored().block()!!.contains("Alex Morgan"))
	}

	@Test
	fun `replacing resume preserves new upload name and removes prior saved resume`() {
		val store = store()
		val first = "First Resume.pdf"
		val second = "Updated Resume.pdf"
		store.saveBytes(first, pdfGenerator.create("First resume content."))

		val saved = store.saveBytes(second, pdfGenerator.create("Updated resume content."))

		assertEquals(ResumeStatus(true, second), saved)
		assertEquals(saved, store.status())
		assertFalse(Files.exists(temporaryDirectory.resolve("data").resolve(first)))
		assertTrue(store.extractStored().block()!!.contains("Updated resume content"))
	}

	@Test
	fun `rejects resume filenames that contain paths or unsafe filesystem characters`() {
		val store = store()

		val exception = assertThrows(ResponseStatusException::class.java) {
			store.saveBytes("..\\outside.pdf", pdfGenerator.create("Resume content."))
		}

		assertEquals(HttpStatus.BAD_REQUEST, exception.statusCode)
	}

	private fun store() = ResumeStore(
		DocumentTextExtractor(),
		temporaryDirectory.resolve("data").toString()
	)
}
