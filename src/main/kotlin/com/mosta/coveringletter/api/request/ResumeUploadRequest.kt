package com.mosta.coveringletter.api.request

import jakarta.validation.constraints.NotEmpty
import org.springframework.http.codec.multipart.FilePart

data class ResumeUploadRequest(
	@field:NotEmpty(message = "Choose at least one resume file.")
	val resume: List<FilePart> = emptyList()
)
