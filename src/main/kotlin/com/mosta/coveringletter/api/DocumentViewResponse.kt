package com.mosta.coveringletter.api

data class DocumentViewResponse(
	val id: String,
	val filename: String,
	val extension: String,
	val viewable: Boolean,
	val rawUrl: String,
	val text: String
)
