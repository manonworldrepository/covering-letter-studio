package com.mosta.coveringletter.api.request

import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size

data class ResumeSelectionRequest(
	@field:NotBlank(message = "Resume id is required.")
	@field:Size(max = 187, message = "Resume id is invalid.")
	val id: String
)
