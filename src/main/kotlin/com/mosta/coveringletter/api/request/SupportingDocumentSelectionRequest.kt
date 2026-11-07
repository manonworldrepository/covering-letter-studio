package com.mosta.coveringletter.api.request

import com.mosta.coveringletter.supportingdocument.SupportingDocumentStoreLimits

import jakarta.validation.constraints.Size

data class SupportingDocumentSelectionRequest(
	@field:Size(max = SupportingDocumentStoreLimits.MAX_FILES, message = "Select no more than five supporting documents.")
	val ids: Set<String> = emptySet()
)
