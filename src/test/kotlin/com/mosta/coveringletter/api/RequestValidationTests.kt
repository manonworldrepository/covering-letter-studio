package com.mosta.coveringletter.api

import com.mosta.coveringletter.api.request.*

import jakarta.validation.Validation
import com.mosta.coveringletter.api.request.AppSettingsRequest
import com.mosta.coveringletter.api.request.CoverLetterRequest
import com.mosta.coveringletter.api.request.GenerateLetterRequest
import com.mosta.coveringletter.api.request.ResumeSelectionRequest
import com.mosta.coveringletter.api.request.ResumeUploadRequest
import com.mosta.coveringletter.api.request.SupportingDocumentSelectionRequest
import com.mosta.coveringletter.api.request.SupportingDocumentsUploadRequest
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class RequestValidationTests {
	private val validator = Validation.buildDefaultValidatorFactory().validator

	@Test
	fun `rejects invalid settings fields`() {
		val violations = validator.validate(AppSettingsRequest(apiKey = "", model = ""))

		assertTrue(violations.any { it.propertyPath.toString() == "model" })
	}

	@Test
	fun `allows an omitted api key when selecting a model`() {
		assertTrue(validator.validate(AppSettingsRequest(apiKey = "", model = "gemini-3.1-flash-lite")).isEmpty())
	}

	@Test
	fun `rejects incomplete or oversized pdf requests`() {
		assertTrue(validator.validate(CoverLetterRequest("", "Alex Morgan", "Designer")).isNotEmpty())
		assertTrue(
			validator.validate(
				CoverLetterRequest(
					letter = "x".repeat(PdfControllerLimits.MAX_LETTER_LENGTH + 1),
					personName = "Alex Morgan",
					jobTitle = "Designer"
				)
			).isNotEmpty()
		)
	}

	@Test
	fun `rejects invalid resume and supporting document selections`() {
		assertTrue(validator.validate(ResumeSelectionRequest(" ")).isNotEmpty())
		val tooManyIds = (1..6).map { it.toString().padStart(36, '0') }.toSet()
		assertTrue(
			validator.validate(SupportingDocumentSelectionRequest(tooManyIds)).isNotEmpty(),
			"Expected more than five selected document ids to fail validation."
		)
	}

	@Test
	fun `rejects missing multipart files and invalid generation requests`() {
		assertTrue(validator.validate(ResumeUploadRequest()).isNotEmpty())
		assertTrue(validator.validate(SupportingDocumentsUploadRequest()).isNotEmpty())
		assertTrue(validator.validate(GenerateLetterRequest()).isNotEmpty())
		assertTrue(
			validator.validate(
				GenerateLetterRequest(jobDescription = "Designer role", format = "unknown")
			).isNotEmpty()
		)
		assertTrue(
			validator.validate(
				GenerateLetterRequest(jobDescription = "Designer role", format = "motivation-message")
			).isEmpty()
		)
	}
}
