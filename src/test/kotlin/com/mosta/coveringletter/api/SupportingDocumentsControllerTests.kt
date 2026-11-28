package com.mosta.coveringletter.api

import com.mosta.coveringletter.api.controller.SupportingDocumentsController
import com.mosta.coveringletter.documents.DocumentTextExtractor
import com.mosta.coveringletter.pdf.CoverLetterPdfGenerator
import com.mosta.coveringletter.supportingdocument.SupportingDocumentStore
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import java.nio.file.Path

class SupportingDocumentsControllerTests {
	private val principal = java.security.Principal { "owner@example.com" }

	@TempDir
	lateinit var temporaryDirectory: Path

	private lateinit var store: SupportingDocumentStore
	private val pdfGenerator = CoverLetterPdfGenerator()
	private lateinit var controller: SupportingDocumentsController

	@BeforeEach
	fun setup() {
		store = SupportingDocumentStore(DocumentTextExtractor(), temporaryDirectory.toString())
		controller = SupportingDocumentsController(store)
	}

	@Test
	fun `viewSupportingDocument returns document view response`() {
		val bytes = pdfGenerator.create("AWS Certification Details.")
		val doc = store.saveBytes("aws-cert.pdf", bytes)

		val view = controller.viewSupportingDocument(doc.id)

		assertEquals(doc.id, view.id)
		assertEquals("aws-cert.pdf", view.filename)
		assertEquals("pdf", view.extension)
		assertTrue(view.viewable)
		assertTrue(view.text.contains("AWS Certification Details"))
	}

	@Test
	fun `getSupportingDocumentRaw returns inline file with proper content type`() {
		val bytes = pdfGenerator.create("AWS Certification Details.")
		val doc = store.saveBytes("aws-cert.pdf", bytes)

		val response = controller.getSupportingDocumentRaw(doc.id)

		assertEquals(HttpStatus.OK, response.statusCode)
		assertEquals(MediaType.APPLICATION_PDF, response.headers.contentType)
		assertTrue(response.headers.getFirst("Content-Disposition")?.contains("inline") == true)
		assertEquals(bytes.size, response.body?.size)
	}

	private fun SupportingDocumentsController.viewSupportingDocument(id: String) = viewSupportingDocument(id, principal)

	private fun SupportingDocumentsController.getSupportingDocumentRaw(id: String) = getSupportingDocumentRaw(id, principal)
}
