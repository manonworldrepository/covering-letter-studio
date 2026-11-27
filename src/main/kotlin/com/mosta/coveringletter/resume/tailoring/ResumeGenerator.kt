package com.mosta.coveringletter.resume.tailoring

import reactor.core.publisher.Mono

interface ResumeGenerator {
	fun generate(
		jobDescription: String,
		resume: String,
		supportingDocuments: String
	): Mono<GeneratedResume>
}