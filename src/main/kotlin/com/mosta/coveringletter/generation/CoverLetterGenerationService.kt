package com.mosta.coveringletter.generation

import com.mosta.coveringletter.activity.UserActionLogBuffer
import com.mosta.coveringletter.resume.ResumeStore
import com.mosta.coveringletter.supportingdocument.SupportingDocumentStore

import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.web.server.ResponseStatusException
import reactor.core.publisher.Mono

@Service
class CoverLetterGenerationService(
	private val resumeStore: ResumeStore,
	private val supportingDocumentStore: SupportingDocumentStore,
	private val generator: GeminiCoverLetterService,
	private val logs: UserActionLogBuffer
) {
	fun generate(jobDescription: String, requestedFormat: String?): Mono<GeneratedCoverLetter> {
		val format = when (requestedFormat?.trim()?.lowercase()) {
			null, "", "cover-letter" -> GenerationFormat.COVER_LETTER
			"motivation-message" -> GenerationFormat.MOTIVATION_MESSAGE
			else -> return Mono.error(
				ResponseStatusException(HttpStatus.BAD_REQUEST, "Choose a valid generation format.")
			)
		}
		if (jobDescription.isBlank()) {
			return Mono.error(ResponseStatusException(HttpStatus.BAD_REQUEST, "Enter the job description."))
		}
		if (jobDescription.length > CoverLetterGenerationLimits.MAX_JOB_DESCRIPTION_LENGTH) {
			return Mono.error(
				ResponseStatusException(
					HttpStatus.BAD_REQUEST,
					"Job description must be 20,000 characters or fewer."
				)
			)
		}

		return Mono.zip(resumeStore.extractStored(), supportingDocumentStore.extractAll())
			.flatMap { contents ->
				val resumeContent = contents.t1
				val supportingContent = contents.t2
				if (resumeContent.isBlank()) {
					return@flatMap Mono.error(
						ResponseStatusException(HttpStatus.BAD_REQUEST, "The resume contains no extractable text.")
					)
				}
				if (resumeContent.length + supportingContent.length > CoverLetterGenerationLimits.MAX_EXTRACTED_TEXT_LENGTH) {
					return@flatMap Mono.error(
						ResponseStatusException(
							HttpStatus.PAYLOAD_TOO_LARGE,
							"Extracted documents are too long. Use documents totaling no more than 100,000 characters."
						)
					)
				}
				generator.generate(jobDescription.trim(), resumeContent, supportingContent, format)
					.doOnSuccess {
						logs.record(
							if (format == GenerationFormat.MOTIVATION_MESSAGE) {
								"Generated a motivation message"
							} else {
								"Generated a cover letter"
							}
						)
					}
			}
	}
}
