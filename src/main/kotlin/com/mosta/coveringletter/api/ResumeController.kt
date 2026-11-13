package com.mosta.coveringletter.api

import com.mosta.coveringletter.activity.UserActionLogBuffer
import com.mosta.coveringletter.api.request.ResumeSelectionRequest
import com.mosta.coveringletter.api.request.ResumeUploadRequest
import com.mosta.coveringletter.resume.ResumeStatus
import com.mosta.coveringletter.resume.ResumeStore
import jakarta.validation.Valid
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
	private val resumeStore: ResumeStore,
	private val logs: UserActionLogBuffer
) {
	@GetMapping("/api/resume")
	fun getResumeStatus() = resumeStore.status()

	@PostMapping("/api/resume", consumes = [MediaType.MULTIPART_FORM_DATA_VALUE])
	fun saveResume(@Valid @ModelAttribute request: ResumeUploadRequest): Mono<ResumeStatus> =
		resumeStore.save(Flux.fromIterable(request.resume))
			.doOnSuccess { status -> status?.let { logs.record("Saved resume: ${it.filename}") } }

	@PostMapping("/api/resume/select", consumes = [MediaType.APPLICATION_JSON_VALUE])
	fun selectResume(@Valid @RequestBody request: ResumeSelectionRequest): ResumeStatus =
		resumeStore.select(request.id)
			.also { logs.record("Selected resume: ${it.filename}") }

	@DeleteMapping("/api/resume")
	fun clearResumes() = resumeStore.clear()
		.also { logs.record("Cleared resumes") }

	@GetMapping("/api/resume/{id}/view")
	fun viewResume(@PathVariable id: String): DocumentViewResponse =
		resumeStore.getDocumentView(id)
			.also { logs.record("Viewed resume: ${it.filename}") }

	@GetMapping("/api/resume/{id}/raw")
	fun getResumeRaw(@PathVariable id: String): org.springframework.http.ResponseEntity<ByteArray> {
		val (filename, bytes) = resumeStore.getDocumentBytes(id)
		return DocumentResponseHelper.createInlineResponseEntity(filename, bytes)
	}

	@DeleteMapping("/api/resume/{id}")
	fun deleteResume(@PathVariable id: String) =
		resumeStore.delete(id)
			.also { logs.record("Removed resume: ${it.filename}") }
}
