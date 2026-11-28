package com.mosta.coveringletter.api.controller

import com.mosta.coveringletter.api.request.TailoredResumePdfRequest
import com.mosta.coveringletter.pdf.ResumePdfGenerator
import com.mosta.coveringletter.pdf.TailoredResumeFilename
import jakarta.validation.Valid
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.http.server.reactive.ServerHttpResponse
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RestController
import java.security.Principal

@RestController
class ResumePdfController(
	private val pdfGenerator: ResumePdfGenerator
) {
	@PostMapping(
		"/api/tailor-resume/pdf",
		consumes = [MediaType.APPLICATION_JSON_VALUE],
		produces = [MediaType.APPLICATION_PDF_VALUE]
	)
	fun downloadPdf(
		@Valid @RequestBody request: TailoredResumePdfRequest,
		response: ServerHttpResponse,
		principal: Principal
	): ByteArray {
		val filename = TailoredResumeFilename.create(
			request.applicantName,
			request.companyName,
			request.jobTitle
		)
		val pdf = pdfGenerator.create(request.resume.trim())
		response.headers.contentType = MediaType.APPLICATION_PDF
		response.headers.set(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"$filename\"")
		return pdf
	}
}