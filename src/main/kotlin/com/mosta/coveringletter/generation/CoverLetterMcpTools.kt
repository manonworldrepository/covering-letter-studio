package com.mosta.coveringletter.generation

import org.springframework.ai.mcp.annotation.McpTool
import org.springframework.ai.mcp.annotation.McpToolParam
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
		generationService.generate(jobDescription, format)
}
