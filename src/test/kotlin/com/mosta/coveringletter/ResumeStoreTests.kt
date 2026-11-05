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

		assertEquals(saved, store.status())
		assertEquals(filename, saved.filename)
		assertTrue(saved.resumes.single().selected)
		assertTrue(Files.isRegularFile(
			temporaryDirectory.resolve("data").resolve("resumes").resolve("${saved.resumes.single().id}__$filename")
		))
		assertTrue(store.extractStored().block()!!.contains("Alex Morgan"))
	}

	@Test
	fun `keeps multiple resumes and generates from the selected resume`() {
		val store = store()
		val first = "First Resume.pdf"
		val second = "Updated Resume.pdf"
		val firstSaved = store.saveBytes(first, pdfGenerator.create("First resume content."))
		val saved = store.saveBytes(second, pdfGenerator.create("Updated resume content."))

		assertEquals(second, saved.filename)
		assertEquals(2, saved.resumes.size)
		assertTrue(saved.resumes.single { it.filename == second }.selected)
		assertTrue(Files.exists(temporaryDirectory.resolve("data").resolve("resumes")
			.resolve("${firstSaved.resumes.single().id}__$first")))
		assertTrue(store.extractStored().block()!!.contains("Updated resume content"))

		val selected = store.select(firstSaved.resumes.single().id)
		assertEquals(first, selected.filename)
		assertTrue(store().extractStored().block()!!.contains("First resume content"))
	}

	@Test
	fun `removing a selected resume selects and persists another saved resume`() {
		val store = store()
		val first = store.saveBytes("First Resume.pdf", pdfGenerator.create("First resume content."))
		val selected = store.saveBytes("Selected Resume.pdf", pdfGenerator.create("Selected resume content."))
		val selectedOption = selected.resumes.first { it.selected }

		assertEquals(selectedOption, store.delete(selectedOption.id))

		val updated = store().status()
		assertEquals(1, updated.resumes.size)
		assertEquals("First Resume.pdf", updated.filename)
		assertTrue(updated.resumes.single().selected)
		assertTrue(store().extractStored().block()!!.contains("First resume content"))
		assertFalse(Files.exists(
			temporaryDirectory.resolve("data").resolve("resumes")
				.resolve("${selectedOption.id}__${selectedOption.filename}")
		))
		assertEquals(first.resumes.single().id, updated.resumes.single().id)
	}

	@Test
	fun `clears stored and legacy resumes`() {
		val store = store()
		store.saveBytes("Saved Resume.pdf", pdfGenerator.create("Saved resume content."))
		val directory = temporaryDirectory.resolve("data")
		val legacyFilename = "Legacy Resume.pdf"
		Files.write(directory.resolve(legacyFilename), pdfGenerator.create("Legacy resume content."))
		Files.writeString(directory.resolve(".resume-filename"), legacyFilename)

		assertEquals(2, store.clear().size)
		assertFalse(store.status().configured)
		assertTrue(store.status().resumes.isEmpty())
		assertFalse(Files.exists(directory.resolve(".resume-filename")))
		assertFalse(Files.exists(directory.resolve(legacyFilename)))
		assertTrue(Files.list(directory.resolve("resumes")).use { paths -> !paths.findAny().isPresent })
	}

	@Test
	fun `continues using a resume saved by the previous storage format`() {
		val directory = temporaryDirectory.resolve("data")
		Files.createDirectories(directory)
		val filename = "Existing Resume.pdf"
		Files.write(directory.resolve(filename), pdfGenerator.create("Existing resume content."))
		Files.writeString(directory.resolve(".resume-filename"), filename)

		val status = store().status()

		assertEquals(filename, status.filename)
		assertTrue(status.resumes.single().selected)
		assertTrue(store().extractStored().block()!!.contains("Existing resume content"))
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
