package com.mosta.coveringletter.api

import com.mosta.coveringletter.api.request.GenerateLetterRequest
import com.mosta.coveringletter.generation.CoverLetterGenerationService
import com.mosta.coveringletter.generation.GeneratedCoverLetter
import jakarta.validation.Valid
import org.springframework.http.MediaType
import org.springframework.web.bind.annotation.ModelAttribute
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RestController
import reactor.core.publisher.Mono

@RestController
class GenerationController(
	private val generationService: CoverLetterGenerationService
) {
	@PostMapping("/api/generate", consumes = [MediaType.MULTIPART_FORM_DATA_VALUE])
	fun generate(@Valid @ModelAttribute request: GenerateLetterRequest): Mono<GeneratedCoverLetter> =
		generationService.generate(request.jobDescription, request.format)
}
