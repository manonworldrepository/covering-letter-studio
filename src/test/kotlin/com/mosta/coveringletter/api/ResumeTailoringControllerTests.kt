package com.mosta.coveringletter.api

import com.mosta.coveringletter.activity.UserActionLogBuffer
import com.mosta.coveringletter.api.request.TailorResumeRequest
import com.mosta.coveringletter.aspects.GeminiServiceLoggingAspect
import com.mosta.coveringletter.documents.DocumentTextExtractor
import com.mosta.coveringletter.generation.GenerationInputService
import com.mosta.coveringletter.pdf.CoverLetterPdfGenerator
import com.mosta.coveringletter.resume.ResumeStore
import com.mosta.coveringletter.resume.tailoring.GeneratedResume
import com.mosta.coveringletter.resume.tailoring.ResumeGenerator
import com.mosta.coveringletter.resume.tailoring.ResumeTailoringService
import com.mosta.coveringletter.supportingdocument.SupportingDocumentStore
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import reactor.core.publisher.Mono
import java.nio.file.Path

class ResumeTailoringControllerTests {
	@TempDir
	lateinit var temporaryDirectory: Path

	private lateinit var resumeStore: ResumeStore
	private lateinit var generator: MockResumeGenerator
	private lateinit var controller: ResumeTailoringController

	@BeforeEach
	fun setup() {
		resumeStore = ResumeStore(DocumentTextExtractor(), temporaryDirectory.resolve("resume-data").toString())
		val supportingDocumentStore = SupportingDocumentStore(
			DocumentTextExtractor(),
			temporaryDirectory.resolve("supporting-data").toString()
		)
		generator = MockResumeGenerator()
		val service = ResumeTailoringService(
			GenerationInputService(resumeStore, supportingDocumentStore),
			generator,
			GeminiServiceLoggingAspect(UserActionLogBuffer())
		)
		controller = ResumeTailoringController(service)
	}

	@Test
	fun `tailor resume delegates job description and maps generated resume`() {
		resumeStore.saveBytes("resume.pdf", CoverLetterPdfGenerator().create("John Doe. Software engineer."))

		val result = controller.tailorResume(TailorResumeRequest("Forward Deployed Engineer at Acme")).block()

		assertEquals("Forward Deployed Engineer at Acme", generator.lastJobDescription)
		assertEquals("Tailored resume content", result?.resume)
		assertEquals("Forward Deployed Engineer", result?.jobTitle)
	}

	private class MockResumeGenerator : ResumeGenerator {
		var lastJobDescription = ""

		override fun generate(
			jobDescription: String,
			resume: String,
			supportingDocuments: String
		): Mono<GeneratedResume> {
			lastJobDescription = jobDescription
			return Mono.just(GeneratedResume("Tailored resume content", "Forward Deployed Engineer", "John Doe", "Acme"))
		}
	}
}