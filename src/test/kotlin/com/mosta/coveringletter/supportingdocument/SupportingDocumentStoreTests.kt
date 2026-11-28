package com.mosta.coveringletter.supportingdocument

import com.mosta.coveringletter.documents.DocumentTextExtractor
import com.mosta.coveringletter.pdf.CoverLetterPdfGenerator
import com.mosta.coveringletter.persistence.AccountDataDirectories

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.springframework.http.HttpStatus
import org.springframework.web.server.ResponseStatusException
import java.nio.file.Files
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

	@Test
	fun `retrieves document view and raw bytes for a stored supporting document`() {
		val store = store()
		val filename = "certificate.pdf"
		val bytes = pdfGenerator.create("Certificate of completion.")
		val saved = store.saveBytes(filename, bytes)

		val view = store.getDocumentView(saved.id)
		assertEquals(saved.id, view.id)
		assertEquals(filename, view.filename)
		assertEquals("pdf", view.extension)
		assertTrue(view.viewable)
		assertEquals("/api/supporting-documents/${saved.id}/raw", view.rawUrl)
		assertTrue(view.text.contains("Certificate of completion"))

		val (rawName, rawBytes) = store.getDocumentBytes(saved.id)
		assertEquals(filename, rawName)
		assertEquals(bytes.size, rawBytes.size)
	}

	@Test
	fun `throws 404 when viewing or reading unknown supporting document`() {
		val store = store()
		val exception = org.junit.jupiter.api.Assertions.assertThrows(ResponseStatusException::class.java) {
			store.getDocumentView("00000000-0000-0000-0000-000000000000")
		}
		assertEquals(HttpStatus.NOT_FOUND, exception.statusCode)

		val rawException = org.junit.jupiter.api.Assertions.assertThrows(ResponseStatusException::class.java) {
			store.getDocumentBytes("00000000-0000-0000-0000-000000000000")
		}
		assertEquals(HttpStatus.NOT_FOUND, rawException.statusCode)
	}

	@Test
	fun `each account sees shared documents and only its own private supporting documents`() {
		val store = store()
		val shared = store.saveBytes("legacy.pdf", pdfGenerator.create("Shared legacy document."))
		val alice = store.saveBytes("alice.pdf", pdfGenerator.create("Alice private document."), "alice@example.com")

		assertEquals(setOf(shared.id, alice.id), store.list("alice@example.com").map { it.id }.toSet())
		assertEquals(setOf(shared.id), store.list("bob@example.com").map { it.id }.toSet())
		assertTrue(store.list("bob@example.com").single().shared)
	}

	@Test
	fun `any account can delete shared documents but cannot delete another account's private documents`() {
		val store = store()
		val shared = store.saveBytes("legacy.pdf", pdfGenerator.create("Shared legacy document."))
		val alice = store.saveBytes("alice.pdf", pdfGenerator.create("Alice private document."), "alice@example.com")

		assertEquals(shared.id, store.delete(shared.id, "alice@example.com").block()?.id)
		assertEquals(alice.id, store.list("alice@example.com").single { it.id == alice.id }.id)
		assertTrue(store.clear("alice@example.com").block()!!.any { it.id == alice.id })
		assertTrue(store.list("bob@example.com").isEmpty())
	}

	@Test
	fun `clearing documents from one account deletes shared legacy documents for all accounts`() {
		val store = store()
		val firstShared = store.saveBytes("first-shared.pdf", pdfGenerator.create("First shared document."))
		store.saveBytes("second-shared.pdf", pdfGenerator.create("Second shared document."))
		store.saveBytes("private.pdf", pdfGenerator.create("Private document."), "alice@example.com")
		store.select(setOf(firstShared.id), "alice@example.com")

		val removed = store.clear("alice@example.com").block()!!

		assertEquals(3, removed.size)
		assertTrue(store.list("alice@example.com").isEmpty())
		assertTrue(store.list("bob@example.com").isEmpty())
	}

	@Test
	fun `deleting shared legacy document removes migration copies`() {
		val store = store()
		val shared = store.saveBytes("legacy.pdf", pdfGenerator.create("Shared legacy document."))
		val accountDirectory = AccountDataDirectories(temporaryDirectory.resolve("data")).forEmail("info@example.com")
		val sharedFile = temporaryDirectory.resolve("data/supporting-documents/${shared.id}__legacy.pdf")
		val copiedFile = accountDirectory.resolve("supporting-documents/${shared.id}__legacy.pdf")
		Files.createDirectories(copiedFile.parent)
		Files.copy(sharedFile, copiedFile)

		store.delete(shared.id, "alice@example.com").block()

		assertTrue(store.list("bob@example.com").isEmpty())
		assertFalse(Files.exists(copiedFile))
	}

	private fun store() = SupportingDocumentStore(
		DocumentTextExtractor(),
		temporaryDirectory.resolve("data").toString()
	)
}
