package com.mosta.coveringletter.coverletter

import reactor.core.publisher.Mono

interface CoverLetterGenerator {
	fun generate(
		jobDescription: String,
		resume: String,
		supportingDocuments: String,
		format: GenerationFormat
	): Mono<GeneratedCoverLetter>

	fun generateForAccount(
		jobDescription: String,
		resume: String,
		supportingDocuments: String,
		format: GenerationFormat,
		accountEmail: String
	): Mono<GeneratedCoverLetter> = generate(jobDescription, resume, supportingDocuments, format)
}
