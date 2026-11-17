package com.mosta.coveringletter.generation

import reactor.core.publisher.Mono

interface CoverLetterGenerator {
	fun generate(
		jobDescription: String,
		resume: String,
		supportingDocuments: String,
		format: GenerationFormat
	): Mono<GeneratedCoverLetter>
}
