package com.mosta.coveringletter.resume.tailoring

import com.mosta.coveringletter.activity.UserActionLogBuffer
import com.mosta.coveringletter.aspects.GeminiServiceLoggingAspect
import com.mosta.coveringletter.documents.DocumentTextExtractor
import com.mosta.coveringletter.generation.GenerationInputService
import com.mosta.coveringletter.pdf.CoverLetterPdfGenerator
import com.mosta.coveringletter.resume.ResumeStore
import com.mosta.coveringletter.supportingdocument.SupportingDocumentStore
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path

class ResumeTailoringServiceTests {
	@TempDir
	lateinit var temporaryDirectory: Path

	private val pdfGenerator = CoverLetterPdfGenerator()
	private lateinit var resumeStore: ResumeStore
	private lateinit var supportingDocumentStore: SupportingDocumentStore
	private lateinit var mockGenerator: MockResumeGenerator
	private lateinit var service: ResumeTailoringService

	@BeforeEach
	fun setup() {
		resumeStore = ResumeStore(DocumentTextExtractor(), temporaryDirectory.resolve("resume-data").toString())
		supportingDocumentStore = SupportingDocumentStore(
			DocumentTextExtractor(),
			temporaryDirectory.resolve("supporting-data").toString()
		)
		mockGenerator = MockResumeGenerator()
		service = ResumeTailoringService(
			GenerationInputService(resumeStore, supportingDocumentStore),
			mockGenerator,
			GeminiServiceLoggingAspect(UserActionLogBuffer())
		)
	}

	@Test
	fun `tailors resume using selected resume and supporting documents`() {
		resumeStore.saveBytes("resume.pdf", pdfGenerator.create("John Doe. Software engineer."))
		supportingDocumentStore.saveBytes("project.pdf", pdfGenerator.create("Built agentic workflows for customers."))
		supportingDocumentStore.select(setOf(supportingDocumentStore.list().single().id))

		val result = service.tailor("Forward Deployed Engineer at Acme").block()

		assertEquals("Forward Deployed Engineer at Acme", mockGenerator.lastJobDescription)
		assertTrue(mockGenerator.lastResume.contains("John Doe"))
		assertTrue(mockGenerator.lastSupportingDocuments.contains("agentic workflows"))
		assertEquals("Tailored resume content", result?.resume)
	}

	@Test
	fun `rejects resume tailoring when no resume is stored`() {
		val error = org.junit.jupiter.api.Assertions.assertThrows(
			org.springframework.web.server.ResponseStatusException::class.java
		) {
			service.tailor("Forward Deployed Engineer").block()
		}

		assertEquals(org.springframework.http.HttpStatus.BAD_REQUEST, error.statusCode)
	}

	private class MockResumeGenerator : ResumeGenerator {
		var lastJobDescription = ""
		var lastResume = ""
		var lastSupportingDocuments = ""

		override fun generate(
			jobDescription: String,
			resume: String,
			supportingDocuments: String
		) = reactor.core.publisher.Mono.just(
			GeneratedResume("Tailored resume content", "Forward Deployed Engineer", "John Doe", "Acme")
		).also {
			lastJobDescription = jobDescription
			lastResume = resume
			lastSupportingDocuments = supportingDocuments
		}
	}
}