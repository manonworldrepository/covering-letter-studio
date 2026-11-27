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
	fun tailor(jobDescription: String): Mono<GeneratedResume> =
		inputService.load(jobDescription)
			.flatMap { input ->
				generator.generate(jobDescription.trim(), input.resume, input.supportingDocuments)
					.doOnSuccess { loggingAspect.logResumeTailored() }
			}
}