package com.mosta.coveringletter.coverletter

import org.springframework.ai.mcp.annotation.McpTool
import org.springframework.ai.mcp.annotation.McpToolParam
import org.springframework.security.access.AccessDeniedException
import org.springframework.security.core.context.ReactiveSecurityContextHolder
import org.springframework.stereotype.Component
import reactor.core.publisher.Mono

@Component
class CoverLetterMcpTools(
	private val generationService: CoverLetterGenerationService
) {
	@McpTool(
		name = "generate_cover_letter",
		description = "Generate a cover letter or motivation message using the locally selected resume, supporting documents, and configured Gemini model."
	)
	fun generateCoverLetter(
		@McpToolParam(
			description = "The job description to tailor the response to.",
			required = true
		)
		jobDescription: String,
		@McpToolParam(
			description = "Output format: cover-letter or motivation-message. Defaults to cover-letter.",
			required = false
		)
		format: String? = null
	): Mono<GeneratedCoverLetter> =
		ReactiveSecurityContextHolder.getContext()
			.switchIfEmpty(Mono.error(AccessDeniedException("Authentication is required to generate a cover letter.")))
			.flatMap { context ->
				val email = context.authentication?.name
					?: return@flatMap Mono.error(AccessDeniedException("Authentication is required to generate a cover letter."))
				generationService.generate(jobDescription, format, email)
			}
}
