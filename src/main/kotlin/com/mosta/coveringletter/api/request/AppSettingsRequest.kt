package com.mosta.coveringletter.api.request

import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Pattern
import jakarta.validation.constraints.Size

data class AppSettingsRequest(
	@field:Size(max = 4096, message = "Gemini API key must be 4,096 characters or fewer.")
	@field:Pattern(regexp = "^[^\\r\\n]*$", message = "Gemini API key must not contain line breaks.")
	val apiKey: String = "",
	@field:NotBlank(message = "Select a valid Gemini model.")
	@field:Pattern(regexp = "[A-Za-z0-9._-]+", message = "Select a valid Gemini model.")
	val model: String
)
