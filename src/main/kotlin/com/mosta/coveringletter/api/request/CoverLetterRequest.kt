package com.mosta.coveringletter.api.request

import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size

data class CoverLetterRequest(
	@field:NotBlank(message = "Letter, person name, or job title is missing or exceeds its allowed length.")
	@field:Size(max = CoverLetterRequestLimits.MAX_LETTER_LENGTH, message = "Letter, person name, or job title is missing or exceeds its allowed length.")
	val letter: String,
	@field:NotBlank(message = "Letter, person name, or job title is missing or exceeds its allowed length.")
	@field:Size(max = 120, message = "Letter, person name, or job title is missing or exceeds its allowed length.")
	val personName: String,
	@field:NotBlank(message = "Letter, person name, or job title is missing or exceeds its allowed length.")
	@field:Size(max = 160, message = "Letter, person name, or job title is missing or exceeds its allowed length.")
	val jobTitle: String,
	@field:Size(max = 120, message = "Company name must be 120 characters or fewer.")
	val companyName: String = ""
)
