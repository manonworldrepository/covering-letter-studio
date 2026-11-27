package com.mosta.coveringletter.api.request

import com.mosta.coveringletter.generation.GenerationLimits
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size

data class TailoredResumePdfRequest(
	@field:NotBlank(message = "Resume content is required.")
	@field:Size(max = TailoredResumePdfRequestLimits.MAX_RESUME_LENGTH, message = "Resume content must be 100,000 characters or fewer.")
	val resume: String = "",
	@field:Size(max = 120, message = "Applicant name must be 120 characters or fewer.")
	val applicantName: String = "",
	@field:Size(max = 120, message = "Company name must be 120 characters or fewer.")
	val companyName: String = "",
	@field:Size(max = 160, message = "Job title must be 160 characters or fewer.")
	val jobTitle: String = ""
)

internal object TailoredResumePdfRequestLimits {
	const val MAX_RESUME_LENGTH = GenerationLimits.MAX_EXTRACTED_TEXT_LENGTH
}