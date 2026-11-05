package com.mosta.coveringletter

import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.http.CacheControl
import org.springframework.http.codec.multipart.FilePart
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestPart
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.server.ResponseStatusException
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono

data class CoverLetterRequest(val letter: String, val personName: String, val jobTitle: String)
data class ResumeSelectionRequest(val id: String)
data class SupportingDocumentSelectionRequest(val ids: Set<String> = emptySet())

@RestController
class CoverLetterController(
	private val resumeStore: ResumeStore,
	private val supportingDocumentStore: SupportingDocumentStore,
	private val settings: AppSettingsService,
	private val logs: UserActionLogBuffer,
	private val generator: GeminiCoverLetterService,
	private val pdfGenerator: CoverLetterPdfGenerator
) {
	@GetMapping("/api/settings")
	fun getSettings() = settings.status(resumeStore.status())

	@GetMapping("/api/settings/current")
	fun getCurrentSettings(): ResponseEntity<CurrentGeminiSettings> =
		ResponseEntity.ok()
			.cacheControl(CacheControl.noStore())
			.body(settings.currentSettings())

	@PostMapping("/api/settings/models", consumes = [MediaType.APPLICATION_JSON_VALUE])
	fun getModels(@RequestBody request: GeminiModelsRequest) = settings.availableModels(request.apiKey)

	@PostMapping("/api/settings", consumes = [MediaType.APPLICATION_JSON_VALUE])
	fun saveSettings(@RequestBody request: AppSettingsRequest): Mono<AppSettingsStatus> =
		settings.save(request)
			.doOnSuccess { logs.record("Updated Gemini API settings") }
			.map { settings.status(resumeStore.status()) }

	@GetMapping("/api/resume")
	fun getResumeStatus() = resumeStore.status()

	@PostMapping("/api/resume", consumes = [MediaType.MULTIPART_FORM_DATA_VALUE])
	fun saveResume(@RequestPart("resume") resumes: Flux<FilePart>): Mono<ResumeStatus> =
		resumeStore.save(resumes)
			.doOnSuccess { status -> status?.let { logs.record("Saved resume: ${it.filename}") } }

	@PostMapping("/api/resume/select", consumes = [MediaType.APPLICATION_JSON_VALUE])
	fun selectResume(@RequestBody request: ResumeSelectionRequest): ResumeStatus =
		resumeStore.select(request.id)
			.also { logs.record("Selected resume: ${it.filename}") }

	@DeleteMapping("/api/resume")
	fun clearResumes() = resumeStore.clear()
		.also { logs.record("Cleared resumes") }

	@DeleteMapping("/api/resume/{id}")
	fun deleteResume(@org.springframework.web.bind.annotation.PathVariable id: String) =
		resumeStore.delete(id)
			.also { logs.record("Removed resume: ${it.filename}") }

	@GetMapping("/api/supporting-documents")
	fun getSupportingDocuments() = supportingDocumentStore.list()

	@PostMapping("/api/supporting-documents", consumes = [MediaType.MULTIPART_FORM_DATA_VALUE])
	fun saveSupportingDocuments(@RequestPart("documents") documents: Flux<FilePart>) =
		supportingDocumentStore.save(documents)
			.doOnSuccess { logs.record("Saved supporting document(s)") }

	@PostMapping("/api/supporting-documents/selection", consumes = [MediaType.APPLICATION_JSON_VALUE])
	fun selectSupportingDocuments(@RequestBody request: SupportingDocumentSelectionRequest) =
		supportingDocumentStore.select(request.ids)
			.also { logs.record("Updated supporting document selections") }

	@DeleteMapping("/api/supporting-documents")
	fun clearSupportingDocuments() = supportingDocumentStore.clear()
		.doOnSuccess { logs.record("Cleared supporting documents") }

	@DeleteMapping("/api/supporting-documents/{id}")
	fun deleteSupportingDocument(@org.springframework.web.bind.annotation.PathVariable id: String) =
		supportingDocumentStore.delete(id)
			.doOnSuccess { document -> document?.let { logs.record("Removed supporting document: ${it.filename}") } }

	@GetMapping("/api/logs")
	fun getUserActions() = logs.latest()

	@PostMapping("/api/generate", consumes = [MediaType.MULTIPART_FORM_DATA_VALUE])
	fun generate(
		@RequestPart("jobDescription") jobDescription: String
	): Mono<GeneratedCoverLetter> {
		if (jobDescription.isBlank()) {
			throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Enter the job description.")
		}
		if (jobDescription.length > MAX_JOB_DESCRIPTION_LENGTH) {
			throw ResponseStatusException(
				HttpStatus.BAD_REQUEST,
				"Job description must be 20,000 characters or fewer."
			)
		}
		val resumeText = resumeStore.extractStored()
		val supportingText = supportingDocumentStore.extractAll()

		return Mono.zip(resumeText, supportingText)
			.flatMap { contents ->
				val resumeContent = contents.t1
				if (resumeContent.isBlank()) {
					return@flatMap Mono.error(
						ResponseStatusException(
							HttpStatus.BAD_REQUEST,
							"The resume contains no extractable text."
						)
					)
				}
				if ((resumeContent.length + contents.t2.length) > MAX_EXTRACTED_TEXT_LENGTH) {
					return@flatMap Mono.error(
						ResponseStatusException(
							HttpStatus.PAYLOAD_TOO_LARGE,
							"Extracted documents are too long. Use documents totaling no more than 100,000 characters."
						)
					)
				}
				generator.generate(jobDescription.trim(), resumeContent, contents.t2)
					.doOnSuccess { logs.record("Generated a cover letter") }
			}
	}

	@PostMapping("/api/pdf", consumes = [MediaType.APPLICATION_JSON_VALUE])
	fun downloadPdf(@RequestBody request: CoverLetterRequest): ResponseEntity<ByteArray> {
		if (request.letter.isBlank() || request.letter.length > MAX_LETTER_LENGTH ||
			request.personName.isBlank() || request.personName.length > 120 ||
			request.jobTitle.isBlank() || request.jobTitle.length > 160
		) {
			throw ResponseStatusException(
				HttpStatus.BAD_REQUEST,
				"Letter, person name, or job title is missing or exceeds its allowed length."
			)
		}
		val filename = try {
			CoverLetterFilename.create(request.personName, request.jobTitle)
		} catch (exception: IllegalArgumentException) {
			throw ResponseStatusException(HttpStatus.BAD_REQUEST, exception.message, exception)
		}
		val pdf = pdfGenerator.create(request.letter.trim())
		logs.record("Downloaded a cover-letter PDF")
		return ResponseEntity.ok()
			.contentType(MediaType.APPLICATION_PDF)
			.header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"$filename\"")
			.body(pdf)
	}

	private companion object {
		const val MAX_JOB_DESCRIPTION_LENGTH = 20_000
		const val MAX_LETTER_LENGTH = 20_000
		const val MAX_EXTRACTED_TEXT_LENGTH = 100_000
	}
}
