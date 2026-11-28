package com.mosta.coveringletter.api

import com.mosta.coveringletter.api.controller.GenerationController
import com.mosta.coveringletter.api.request.GenerateLetterRequest
import com.mosta.coveringletter.aspects.GeminiServiceLoggingAspect
import com.mosta.coveringletter.coverletter.CoverLetterGenerationService
import com.mosta.coveringletter.coverletter.CoverLetterGenerator
import com.mosta.coveringletter.coverletter.GeneratedCoverLetter
import com.mosta.coveringletter.coverletter.GenerationFormat
import com.mosta.coveringletter.generation.GenerationInputService
import com.mosta.coveringletter.resume.ResumeStore
import com.mosta.coveringletter.supportingdocument.SupportingDocumentStore
import com.mosta.coveringletter.documents.DocumentTextExtractor
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.springframework.http.HttpStatus
import org.springframework.web.server.ResponseStatusException
import reactor.core.publisher.Mono
import java.nio.file.Path

class GenerationControllerTests {
	private val principal = java.security.Principal { "owner@example.com" }

	@TempDir
	lateinit var temporaryDirectory: Path

	private lateinit var mockGenerator: MockCoverLetterGenerator
	private lateinit var resumeStore: ResumeStore
	private lateinit var generationService: CoverLetterGenerationService
	private lateinit var controller: GenerationController

	@BeforeEach
	fun setup() {
		resumeStore = ResumeStore(DocumentTextExtractor(), temporaryDirectory.resolve("resume-data").toString())
		val supportingDocumentStore = SupportingDocumentStore(DocumentTextExtractor(), temporaryDirectory.resolve("supporting-data").toString())
		val loggingAspect = GeminiServiceLoggingAspect(com.mosta.coveringletter.activity.UserActionLogBuffer())
		mockGenerator = MockCoverLetterGenerator()
		generationService = CoverLetterGenerationService(
			GenerationInputService(resumeStore, supportingDocumentStore),
			mockGenerator,
			loggingAspect
		)
		controller = GenerationController(generationService)
	}

	@Test
	fun `generate delegates to service with correct parameters`() {
		val pdfGenerator = com.mosta.coveringletter.pdf.CoverLetterPdfGenerator()
		resumeStore.saveBytes("resume.pdf", pdfGenerator.create("John Doe. Software engineer."))
		val request = GenerateLetterRequest("Software Engineer at Acme", "cover-letter")

		val result = controller.generate(request).block()

		assertEquals("Software Engineer", result?.jobTitle)
		assertEquals("Software Engineer at Acme", mockGenerator.lastJobDescription)
	}

	@Test
	fun `generate propagates validation error from service`() {
		val request = GenerateLetterRequest("", "cover-letter")

		val error = org.junit.jupiter.api.Assertions.assertThrows(ResponseStatusException::class.java) {
			controller.generate(request).block()
		}

		assertEquals(HttpStatus.BAD_REQUEST, error.statusCode)
	}

	private inner class MockCoverLetterGenerator : CoverLetterGenerator {
		var lastJobDescription: String = ""
		var lastFormat: GenerationFormat = GenerationFormat.COVER_LETTER

		override fun generate(
			jobDescription: String,
			resume: String,
			supportingDocuments: String,
			format: GenerationFormat
		): Mono<GeneratedCoverLetter> {
			lastJobDescription = jobDescription
			lastFormat = format
			return Mono.just(
				GeneratedCoverLetter(
					"Dear Hiring Manager, I am interested.",
					"Software Engineer",
					"John Doe",
					"Acme Corp"
				)
			)
		}
	}

	private fun GenerationController.generate(request: GenerateLetterRequest) = generate(request, principal)
}
