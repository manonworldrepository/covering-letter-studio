package com.mosta.coveringletter.resume.tailoring

import com.mosta.coveringletter.aspects.GeminiServiceLoggingAspect
import com.mosta.coveringletter.generation.GenerationInputService
import org.springframework.stereotype.Service
import reactor.core.publisher.Mono

@Service
class ResumeTailoringService(
	private val inputService: GenerationInputService,
	private val generator: ResumeGenerator,
	private val loggingAspect: GeminiServiceLoggingAspect
) {
	fun tailor(jobDescription: String, accountEmail: String? = null): Mono<GeneratedResume> =
		inputService.load(jobDescription, accountEmail)
			.flatMap { input ->
				val generation = if (accountEmail == null) {
					generator.generate(jobDescription.trim(), input.resume, input.supportingDocuments)
				} else {
					generator.generateForAccount(jobDescription.trim(), input.resume, input.supportingDocuments, accountEmail)
				}
				generation.doOnSuccess { loggingAspect.logResumeTailored(accountEmail) }
			}
}