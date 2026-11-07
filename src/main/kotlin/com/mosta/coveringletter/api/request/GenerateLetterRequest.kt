package com.mosta.coveringletter.api.request

import com.mosta.coveringletter.generation.CoverLetterGenerationLimits

import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Pattern
import jakarta.validation.constraints.Size

data class GenerateLetterRequest(
	@field:NotBlank(message = "Enter the job description.")
	@field:Size(max = CoverLetterGenerationLimits.MAX_JOB_DESCRIPTION_LENGTH, message = "Job description must be 20,000 characters or fewer.")
	val jobDescription: String = "",
	@field:Pattern(
		regexp = "^(|cover-letter|motivation-message)$",
		message = "Choose a valid generation format."
	)
	val format: String = ""
)
