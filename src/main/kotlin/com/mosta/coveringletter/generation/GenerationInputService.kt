package com.mosta.coveringletter.generation

import com.mosta.coveringletter.resume.ResumeStore
import com.mosta.coveringletter.supportingdocument.SupportingDocumentStore
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.web.server.ResponseStatusException
import reactor.core.publisher.Mono

@Service
class GenerationInputService(
	private val resumeStore: ResumeStore,
	private val supportingDocumentStore: SupportingDocumentStore
) {
	fun load(jobDescription: String): Mono<GenerationInput> {
		if (jobDescription.isBlank()) {
			return Mono.error(ResponseStatusException(HttpStatus.BAD_REQUEST, "Enter the job description."))
		}
		if (jobDescription.length > GenerationLimits.MAX_JOB_DESCRIPTION_LENGTH) {
			return Mono.error(
				ResponseStatusException(
					HttpStatus.BAD_REQUEST,
					"Job description must be 20,000 characters or fewer."
				)
			)
		}

		return Mono.zip(resumeStore.extractStored(), supportingDocumentStore.extractAll())
			.map { contents ->
				val resumeContent = contents.t1
				val supportingContent = contents.t2
				if (resumeContent.isBlank()) {
					throw ResponseStatusException(HttpStatus.BAD_REQUEST, "The resume contains no extractable text.")
				}
				if (resumeContent.length + supportingContent.length > GenerationLimits.MAX_EXTRACTED_TEXT_LENGTH) {
					throw ResponseStatusException(
						HttpStatus.PAYLOAD_TOO_LARGE,
						"Extracted documents are too long. Use documents totaling no more than 100,000 characters."
					)
				}
				GenerationInput(resumeContent, supportingContent)
			}
	}
}