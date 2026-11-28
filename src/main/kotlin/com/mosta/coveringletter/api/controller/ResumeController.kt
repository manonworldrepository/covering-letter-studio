package com.mosta.coveringletter.api.controller

import com.mosta.coveringletter.api.request.ResumeSelectionRequest
import com.mosta.coveringletter.api.request.ResumeUploadRequest
import com.mosta.coveringletter.api.response.DocumentViewResponse
import com.mosta.coveringletter.resume.ResumeStatus
import com.mosta.coveringletter.resume.ResumeStore
import jakarta.validation.Valid
import java.security.Principal
import org.springframework.http.MediaType
import org.springframework.http.codec.multipart.FilePart
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.ModelAttribute
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RestController
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono

@RestController
class ResumeController(
	private val resumeStore: ResumeStore
) {
	@GetMapping("/api/resume")
	fun getResumeStatus(principal: Principal) = resumeStore.status(principal.name)

	@PostMapping("/api/resume", consumes = [MediaType.MULTIPART_FORM_DATA_VALUE])
	fun saveResume(@Valid @ModelAttribute request: ResumeUploadRequest, principal: Principal): Mono<ResumeStatus> =
		resumeStore.save(Flux.fromIterable(request.resume), principal.name)

	@PostMapping("/api/resume/select", consumes = [MediaType.APPLICATION_JSON_VALUE])
	fun selectResume(@Valid @RequestBody request: ResumeSelectionRequest, principal: Principal): ResumeStatus =
		resumeStore.select(request.id, principal.name)

	@DeleteMapping("/api/resume")
	fun clearResumes(principal: Principal) = resumeStore.clear(principal.name)

	@GetMapping("/api/resume/{id}/view")
	fun viewResume(@PathVariable id: String, principal: Principal): DocumentViewResponse =
		resumeStore.getDocumentView(id, principal.name)

	@GetMapping("/api/resume/{id}/raw")
	fun getResumeRaw(@PathVariable id: String, principal: Principal): org.springframework.http.ResponseEntity<ByteArray> {
		val (filename, bytes) = resumeStore.getDocumentBytes(id, principal.name)
		return DocumentResponseHelper.createInlineResponseEntity(filename, bytes)
	}

	@DeleteMapping("/api/resume/{id}")
	fun deleteResume(@PathVariable id: String, principal: Principal) =
		resumeStore.delete(id, principal.name)
}
