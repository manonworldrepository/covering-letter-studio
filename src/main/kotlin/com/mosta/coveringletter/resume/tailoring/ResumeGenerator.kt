package com.mosta.coveringletter.resume.tailoring

import reactor.core.publisher.Mono

interface ResumeGenerator {
	fun generate(
		jobDescription: String,
		resume: String,
		supportingDocuments: String
	): Mono<GeneratedResume>

	fun generateForAccount(
		jobDescription: String,
		resume: String,
		supportingDocuments: String,
		accountEmail: String
	): Mono<GeneratedResume> = generate(jobDescription, resume, supportingDocuments)
}