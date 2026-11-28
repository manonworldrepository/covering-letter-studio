package com.mosta.coveringletter.api.response

data class UserInfoResponse(
	val email: String,
	val name: String,
	val isAdmin: Boolean = false
)
