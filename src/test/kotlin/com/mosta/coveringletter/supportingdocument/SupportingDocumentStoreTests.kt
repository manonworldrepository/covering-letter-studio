package com.mosta.coveringletter.supportingdocument

import com.mosta.coveringletter.documents.DocumentTextExtractor
import com.mosta.coveringletter.pdf.CoverLetterPdfGenerator

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.springframework.http.HttpStatus
import org.springframework.web.server.ResponseStatusException
import java.nio.file.Path

class SupportingDocumentStoreTests {
	@TempDir
	lateinit var temporaryDirectory: Path

	private val pdfGenerator = CoverLetterPdfGenerator()

	@Test
	fun `stores lists extracts and clears reusable supporting documents`() {
		val store = store()
		val bytes = pdfGenerator.create("Certified in secure application design.")

		val saved = store.saveBytes("security-certificate.pdf", bytes)

		assertEquals("security-certificate.pdf", saved.filename)
		assertEquals(listOf(saved), store.list())
		assertTrue(store.extractAll().block()!!.contains("secure application design"))
		assertEquals(saved, store.delete(saved.id).block())
		assertTrue(store.list().isEmpty())

		val second = store.saveBytes("another-certificate.pdf", bytes)
		assertTrue(store.list().contains(second))
		assertTrue(store.clear().block()!!.isEmpty())
		assertTrue(store.list().isEmpty())
	}

	@Test
	fun `only selected supporting documents contribute to extraction and selection persists`() {
		val store = store()
		val selected = store.saveBytes("selected.pdf", pdfGenerator.create("Relevant project experience."))
		store.saveBytes("not-selected.pdf", pdfGenerator.create("Unrelated personal information."))

		val updated = store.select(setOf(selected.id))

		assertTrue(updated.single { it.id == selected.id }.selected)
		assertFalse(updated.single { it.filename == "not-selected.pdf" }.selected)
		assertTrue(store.extractAll().block()!!.contains("Relevant project experience"))
		assertFalse(store.extractAll().block()!!.contains("Unrelated personal information"))
		assertTrue(store().extractAll().block()!!.contains("Relevant project experience"))
		assertFalse(store().extractAll().block()!!.contains("Unrelated personal information"))
	}

	@Test
	fun `rejects deletion of unknown document ids`() {
		val exception = org.junit.jupiter.api.Assertions.assertThrows(ResponseStatusException::class.java) {
			store().delete("00000000-0000-0000-0000-000000000000").block()
		}

		assertEquals(HttpStatus.NOT_FOUND, exception.statusCode)
	}

	@Test
	fun `enforces five-file limit and rejects unsupported extensions`() {
		val store = store()
		val bytes = pdfGenerator.create("Supporting document text.")
		repeat(5) { store.saveBytes("certificate-$it.pdf", bytes) }
		assertEquals(5, store.list().size)

		val tooMany = org.junit.jupiter.api.Assertions.assertThrows(ResponseStatusException::class.java) {
			store.saveBytes("certificate-extra.pdf", bytes)
		}
		assertEquals(HttpStatus.BAD_REQUEST, tooMany.statusCode)

		val unsupported = org.junit.jupiter.api.Assertions.assertThrows(ResponseStatusException::class.java) {
			store.clear().block()
			store.saveBytes("certificate.txt", bytes)
		}
		assertEquals(HttpStatus.BAD_REQUEST, unsupported.statusCode)
		assertFalse(store.list().any { it.filename == "certificate.txt" })
	}

	@Test
	fun `rejects documents larger than ten megabytes`() {
		val exception = org.junit.jupiter.api.Assertions.assertThrows(ResponseStatusException::class.java) {
			store().saveBytes("large.pdf", ByteArray(10 * 1024 * 1024 + 1))
		}

		assertEquals(HttpStatus.PAYLOAD_TOO_LARGE, exception.statusCode)
	}

	private fun store() = SupportingDocumentStore(
		DocumentTextExtractor(),
		temporaryDirectory.resolve("data").toString()
	)
}
