package com.mosta.coveringletter.api

import com.mosta.coveringletter.activity.UserActionLogBuffer
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

	@TempDir
	lateinit var temporaryDirectory: Path

	private lateinit var store: SupportingDocumentStore
	private val logs = UserActionLogBuffer()
	private val pdfGenerator = CoverLetterPdfGenerator()
	private lateinit var controller: SupportingDocumentsController

	@BeforeEach
	fun setup() {
		store = SupportingDocumentStore(DocumentTextExtractor(), temporaryDirectory.toString())
		controller = SupportingDocumentsController(store, logs)
	}

	@Test
	fun `viewSupportingDocument returns document view response and records log`() {
		val bytes = pdfGenerator.create("AWS Certification Details.")
		val doc = store.saveBytes("aws-cert.pdf", bytes)

		val view = controller.viewSupportingDocument(doc.id)

		assertEquals(doc.id, view.id)
		assertEquals("aws-cert.pdf", view.filename)
		assertEquals("pdf", view.extension)
		assertTrue(view.viewable)
		assertTrue(view.text.contains("AWS Certification Details"))
		assertTrue(logs.latest().any { it.action.contains("Viewed supporting document: aws-cert.pdf") })
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
}
