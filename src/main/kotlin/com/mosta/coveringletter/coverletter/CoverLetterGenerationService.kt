package com.mosta.coveringletter.coverletter

import com.mosta.coveringletter.aspects.GeminiServiceLoggingAspect
import com.mosta.coveringletter.generation.GenerationInputService
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.web.server.ResponseStatusException
import reactor.core.publisher.Mono

@Service
class CoverLetterGenerationService(
	private val inputService: GenerationInputService,
	private val generator: CoverLetterGenerator,
	private val loggingAspect: GeminiServiceLoggingAspect
) {
	fun generate(jobDescription: String, requestedFormat: String?): Mono<GeneratedCoverLetter> {
		val format = when (requestedFormat?.trim()?.lowercase()) {
			null, "", "cover-letter" -> GenerationFormat.COVER_LETTER
			"motivation-message" -> GenerationFormat.MOTIVATION_MESSAGE
			else -> return Mono.error(
				ResponseStatusException(HttpStatus.BAD_REQUEST, "Choose a valid generation format.")
			)
		}
		return generate(jobDescription, format)
	}

	private fun generate(jobDescription: String, format: GenerationFormat): Mono<GeneratedCoverLetter> {
		return inputService.load(jobDescription)
			.flatMap { input ->
				generator.generate(jobDescription.trim(), input.resume, input.supportingDocuments, format)
					.doOnSuccess {
						loggingAspect.logCoverLetterGenerated(
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
