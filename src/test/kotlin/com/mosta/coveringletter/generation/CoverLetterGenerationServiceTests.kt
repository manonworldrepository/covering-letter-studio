package com.mosta.coveringletter.generation

import com.mosta.coveringletter.activity.UserActionLogBuffer
import com.mosta.coveringletter.resume.ResumeStore
import com.mosta.coveringletter.supportingdocument.SupportingDocumentStore
import com.mosta.coveringletter.documents.DocumentTextExtractor
import com.mosta.coveringletter.pdf.CoverLetterPdfGenerator
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.springframework.http.HttpStatus
import org.springframework.web.server.ResponseStatusException
import reactor.core.publisher.Mono
import java.nio.file.Path

class CoverLetterGenerationServiceTests {

	@TempDir
	lateinit var temporaryDirectory: Path

	private val pdfGenerator = CoverLetterPdfGenerator()
	private lateinit var resumeStore: ResumeStore
	private lateinit var supportingDocumentStore: SupportingDocumentStore
	private val logs = UserActionLogBuffer()
	private lateinit var mockGenerator: MockCoverLetterGenerator
	private lateinit var service: CoverLetterGenerationService

	@BeforeEach
	fun setup() {
		resumeStore = ResumeStore(DocumentTextExtractor(), temporaryDirectory.resolve("resume-data").toString())
		supportingDocumentStore = SupportingDocumentStore(DocumentTextExtractor(), temporaryDirectory.resolve("supporting-data").toString())
		mockGenerator = MockCoverLetterGenerator()
		service = CoverLetterGenerationService(
			resumeStore,
			supportingDocumentStore,
			mockGenerator,
			logs
		)
	}

	@Test
	fun `rejects blank job description`() {
		val error = org.junit.jupiter.api.Assertions.assertThrows(ResponseStatusException::class.java) {
			service.generate("", "cover-letter").block()
		}

		assertEquals(HttpStatus.BAD_REQUEST, error.statusCode)
		assertTrue(error.message!!.contains("job description"))
	}

	@Test
	fun `rejects job description exceeding maximum length`() {
		val longDescription = "x".repeat(CoverLetterGenerationLimits.MAX_JOB_DESCRIPTION_LENGTH + 1)

		val error = org.junit.jupiter.api.Assertions.assertThrows(ResponseStatusException::class.java) {
			service.generate(longDescription, "cover-letter").block()
		}

		assertEquals(HttpStatus.BAD_REQUEST, error.statusCode)
		assertTrue(error.message!!.contains("20,000"))
	}

	@Test
	fun `rejects invalid generation format`() {
		val error = org.junit.jupiter.api.Assertions.assertThrows(ResponseStatusException::class.java) {
			service.generate("Software Engineer role", "invalid-format").block()
		}

		assertEquals(HttpStatus.BAD_REQUEST, error.statusCode)
		assertTrue(error.message!!.contains("valid generation format"))
	}

	@Test
	fun `accepts null format as default cover letter`() {
		resumeStore.saveBytes("resume.pdf", pdfGenerator.create("John Doe. Software engineer."))

		service.generate("Software Engineer at Acme", null).block()

		assertEquals(GenerationFormat.COVER_LETTER, mockGenerator.lastFormat)
	}

	@Test
	fun `accepts empty string format as default cover letter`() {
		resumeStore.saveBytes("resume.pdf", pdfGenerator.create("John Doe. Software engineer."))

		service.generate("Software Engineer at Acme", "").block()

		assertEquals(GenerationFormat.COVER_LETTER, mockGenerator.lastFormat)
	}

	@Test
	fun `accepts motivation-message format`() {
		resumeStore.saveBytes("resume.pdf", pdfGenerator.create("John Doe. Software engineer."))

		val result = service.generate("Software Engineer at Acme", "motivation-message").block()

		assertEquals("Software Engineer", result?.jobTitle)
		assertEquals(GenerationFormat.MOTIVATION_MESSAGE, mockGenerator.lastFormat)
	}

	@Test
	fun `accepts cover-letter format`() {
		resumeStore.saveBytes("resume.pdf", pdfGenerator.create("John Doe. Software engineer."))

		val result = service.generate("Software Engineer at Acme", "cover-letter").block()

		assertEquals("Software Engineer", result?.jobTitle)
		assertEquals(GenerationFormat.COVER_LETTER, mockGenerator.lastFormat)
	}

	@Test
	fun `rejects when no resume is stored`() {
		val errorMono = service.generate("Software Engineer role", "cover-letter")
		val error = org.junit.jupiter.api.Assertions.assertThrows(ResponseStatusException::class.java) {
			errorMono.block()
		}

		assertEquals(HttpStatus.BAD_REQUEST, error.statusCode)
	}

	@Test
	fun `includes supporting document content in generation`() {
		resumeStore.saveBytes("resume.pdf", pdfGenerator.create("John Doe. Software engineer."))
		supportingDocumentStore.saveBytes("cert.pdf", pdfGenerator.create("AWS Certified Developer."))
		supportingDocumentStore.select(setOf(supportingDocumentStore.list().single().id))

		service.generate("Software Engineer at Acme", "cover-letter").block()

		assertTrue(mockGenerator.lastSupportingDocuments.contains("AWS Certified Developer"))
	}

	@Test
	fun `records user action log on successful cover letter generation`() {
		resumeStore.saveBytes("resume.pdf", pdfGenerator.create("John Doe. Software engineer."))

		service.generate("Software Engineer at Acme", "cover-letter").block()

		val entries = logs.latest()
		assertTrue(entries.any { it.action.contains("Generated a cover letter") })
	}

	@Test
	fun `records motivation message generation in log`() {
		resumeStore.saveBytes("resume.pdf", pdfGenerator.create("John Doe. Software engineer."))

		service.generate("Software Engineer at Acme", "motivation-message").block()

		val entries = logs.latest()
		assertTrue(entries.any { it.action.contains("motivation message") })
	}

	@Test
	fun `trims job description before passing to generator`() {
		resumeStore.saveBytes("resume.pdf", pdfGenerator.create("John Doe. Software engineer."))

		service.generate("  Software Engineer at Acme  ", "cover-letter").block()

		assertEquals("Software Engineer at Acme", mockGenerator.lastJobDescription)
	}

	@Test
	fun `passes resume content to generator`() {
		resumeStore.saveBytes("resume.pdf", pdfGenerator.create("John Doe. Product designer with 5 years experience."))

		service.generate("Software Engineer at Acme", "cover-letter").block()

		assertTrue(mockGenerator.lastResume.contains("John Doe"))
		assertTrue(mockGenerator.lastResume.contains("Product designer"))
	}

	// Mock implementation of the CoverLetterGenerator interface
	private inner class MockCoverLetterGenerator : CoverLetterGenerator {
		var lastJobDescription: String = ""
		var lastResume: String = ""
		var lastSupportingDocuments: String = ""
		var lastFormat: GenerationFormat = GenerationFormat.COVER_LETTER

		override fun generate(
			jobDescription: String,
			resume: String,
			supportingDocuments: String,
			format: GenerationFormat
		): Mono<GeneratedCoverLetter> {
			lastJobDescription = jobDescription
			lastResume = resume
			lastSupportingDocuments = supportingDocuments
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
}
