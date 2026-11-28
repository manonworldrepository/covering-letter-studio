package com.mosta.coveringletter.api.controller

import com.mosta.coveringletter.api.request.SupportingDocumentSelectionRequest
import com.mosta.coveringletter.api.request.SupportingDocumentsUploadRequest
import com.mosta.coveringletter.api.response.DocumentViewResponse
import com.mosta.coveringletter.supportingdocument.SupportingDocumentStore
import jakarta.validation.Valid
import java.security.Principal
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
	private val supportingDocumentStore: SupportingDocumentStore
) {
	@GetMapping("/api/supporting-documents")
	fun getSupportingDocuments(principal: Principal) = supportingDocumentStore.list(principal.name)

	@PostMapping("/api/supporting-documents", consumes = [MediaType.MULTIPART_FORM_DATA_VALUE])
	fun saveSupportingDocuments(@Valid @ModelAttribute request: SupportingDocumentsUploadRequest, principal: Principal) =
		supportingDocumentStore.save(Flux.fromIterable(request.documents), principal.name)

	@PostMapping("/api/supporting-documents/selection", consumes = [MediaType.APPLICATION_JSON_VALUE])
	fun selectSupportingDocuments(@Valid @RequestBody request: SupportingDocumentSelectionRequest, principal: Principal) =
		supportingDocumentStore.select(request.ids, principal.name)

	@DeleteMapping("/api/supporting-documents")
	fun clearSupportingDocuments(principal: Principal) = supportingDocumentStore.clear(principal.name)

	@GetMapping("/api/supporting-documents/{id}/view")
	fun viewSupportingDocument(@PathVariable id: String, principal: Principal): DocumentViewResponse =
		supportingDocumentStore.getDocumentView(id, principal.name)

	@GetMapping("/api/supporting-documents/{id}/raw")
	fun getSupportingDocumentRaw(@PathVariable id: String, principal: Principal): org.springframework.http.ResponseEntity<ByteArray> {
		val (filename, bytes) = supportingDocumentStore.getDocumentBytes(id, principal.name)
		return DocumentResponseHelper.createInlineResponseEntity(filename, bytes)
	}

	@DeleteMapping("/api/supporting-documents/{id}")
	fun deleteSupportingDocument(@PathVariable id: String, principal: Principal) =
		supportingDocumentStore.delete(id, principal.name)
}
