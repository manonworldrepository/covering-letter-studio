package com.mosta.coveringletter.api.request

import com.mosta.coveringletter.supportingdocument.SupportingDocumentStoreLimits

import jakarta.validation.constraints.NotEmpty
import jakarta.validation.constraints.Size
import org.springframework.http.codec.multipart.FilePart

data class SupportingDocumentsUploadRequest(
	@field:NotEmpty(message = "Choose at least one supporting file.")
	@field:Size(max = SupportingDocumentStoreLimits.MAX_FILES, message = "Choose no more than five supporting files.")
	val documents: List<FilePart> = emptyList()
)
