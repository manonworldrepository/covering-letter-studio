package com.mosta.coveringletter.api.request

import jakarta.validation.constraints.Size

data class GeminiModelsRequest(
	@field:Size(max = 4096, message = "Gemini API key must be 4,096 characters or fewer.")
	val apiKey: String = ""
)
