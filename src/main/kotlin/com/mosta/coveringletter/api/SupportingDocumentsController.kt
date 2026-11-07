package com.mosta.coveringletter.api

import com.mosta.coveringletter.activity.UserActionLogBuffer
import com.mosta.coveringletter.api.request.SupportingDocumentSelectionRequest
import com.mosta.coveringletter.api.request.SupportingDocumentsUploadRequest
import com.mosta.coveringletter.supportingdocument.SupportingDocumentStore
import jakarta.validation.Valid
import org.springframework.http.MediaType
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.ModelAttribute
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RestController
import reactor.core.publisher.Flux

@RestController
class SupportingDocumentsController(
	private val supportingDocumentStore: SupportingDocumentStore,
	private val logs: UserActionLogBuffer
) {
	@GetMapping("/api/supporting-documents")
	fun getSupportingDocuments() = supportingDocumentStore.list()

	@PostMapping("/api/supporting-documents", consumes = [MediaType.MULTIPART_FORM_DATA_VALUE])
	fun saveSupportingDocuments(@Valid @ModelAttribute request: SupportingDocumentsUploadRequest) =
		supportingDocumentStore.save(Flux.fromIterable(request.documents))
			.doOnSuccess { logs.record("Saved supporting document(s)") }

	@PostMapping("/api/supporting-documents/selection", consumes = [MediaType.APPLICATION_JSON_VALUE])
	fun selectSupportingDocuments(@Valid @RequestBody request: SupportingDocumentSelectionRequest) =
		supportingDocumentStore.select(request.ids)
			.also { logs.record("Updated supporting document selections") }

	@DeleteMapping("/api/supporting-documents")
	fun clearSupportingDocuments() = supportingDocumentStore.clear()
		.doOnSuccess { logs.record("Cleared supporting documents") }

	@DeleteMapping("/api/supporting-documents/{id}")
	fun deleteSupportingDocument(@PathVariable id: String) =
		supportingDocumentStore.delete(id)
			.doOnSuccess { document -> document?.let { logs.record("Removed supporting document: ${it.filename}") } }
}
