package com.mosta.coveringletter.api.request

import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size

data class AllowedEmailsSetupRequest(
	@field:NotBlank(message = "Enter at least one email address.")
	@field:Size(max = 4096, message = "Email addresses must be 4,096 characters or fewer.")
	val emails: String
)