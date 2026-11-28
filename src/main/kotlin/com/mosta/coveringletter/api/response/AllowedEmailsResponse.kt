package com.mosta.coveringletter.api.response

data class AllowedEmailsResponse(
	val emails: List<String>,
	val adminEmail: String? = null
)