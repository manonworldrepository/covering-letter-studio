package com.mosta.coveringletter.api.request

import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size

data class UserActionRequest(
	@field:NotBlank(message = "Action description is required.")
	@field:Size(max = 200, message = "Action description must be 200 characters or fewer.")
	val action: String
)
