package com.mosta.coveringletter.api

import com.mosta.coveringletter.api.request.TailorResumeRequest
import com.mosta.coveringletter.resume.tailoring.GeneratedResume
import com.mosta.coveringletter.resume.tailoring.ResumeTailoringService
import jakarta.validation.Valid
import org.springframework.http.MediaType
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RestController
import reactor.core.publisher.Mono

@RestController
class ResumeTailoringController(
	private val resumeTailoringService: ResumeTailoringService
) {
	@PostMapping("/api/tailor-resume", consumes = [MediaType.APPLICATION_JSON_VALUE])
	fun tailorResume(@Valid @RequestBody request: TailorResumeRequest): Mono<GeneratedResume> =
		resumeTailoringService.tailor(request.jobDescription)
}