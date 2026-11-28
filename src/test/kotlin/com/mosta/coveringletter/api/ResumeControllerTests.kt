package com.mosta.coveringletter.api

import com.mosta.coveringletter.api.controller.ResumeController
import com.mosta.coveringletter.documents.DocumentTextExtractor
import com.mosta.coveringletter.pdf.CoverLetterPdfGenerator
import com.mosta.coveringletter.resume.ResumeStore
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import java.nio.file.Path

class ResumeControllerTests {
	private val principal = java.security.Principal { "owner@example.com" }

	@TempDir
	lateinit var temporaryDirectory: Path

	private lateinit var store: ResumeStore
	private val pdfGenerator = CoverLetterPdfGenerator()
	private lateinit var controller: ResumeController

	@BeforeEach
	fun setup() {
		store = ResumeStore(DocumentTextExtractor(), temporaryDirectory.toString())
		controller = ResumeController(store)
	}

	@Test
	fun `viewResume returns document view response`() {
		val bytes = pdfGenerator.create("Software Engineer Resume Content.")
		val status = store.saveBytes("resume.pdf", bytes)
		val id = status.resumes.single().id

		val view = controller.viewResume(id)

		assertEquals(id, view.id)
		assertEquals("resume.pdf", view.filename)
		assertEquals("pdf", view.extension)
		assertTrue(view.viewable)
		assertTrue(view.text.contains("Software Engineer Resume Content"))
	}

	@Test
	fun `getResumeRaw returns inline file with proper content type`() {
		val bytes = pdfGenerator.create("Software Engineer Resume Content.")
		val status = store.saveBytes("resume.pdf", bytes)
		val id = status.resumes.single().id

		val response = controller.getResumeRaw(id)

		assertEquals(HttpStatus.OK, response.statusCode)
		assertEquals(MediaType.APPLICATION_PDF, response.headers.contentType)
		assertTrue(response.headers.getFirst("Content-Disposition")?.contains("inline") == true)
		assertEquals(bytes.size, response.body?.size)
	}

	private fun ResumeController.viewResume(id: String) = viewResume(id, principal)

	private fun ResumeController.getResumeRaw(id: String) = getResumeRaw(id, principal)
}
