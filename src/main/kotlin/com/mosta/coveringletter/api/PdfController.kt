package com.mosta.coveringletter.api

import com.mosta.coveringletter.activity.UserActionLogBuffer
import com.mosta.coveringletter.api.request.CoverLetterRequest
import com.mosta.coveringletter.pdf.CoverLetterFilename
import com.mosta.coveringletter.pdf.CoverLetterPdfGenerator
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.http.server.reactive.ServerHttpResponse
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.server.ResponseStatusException

@RestController
class PdfController(
	private val logs: UserActionLogBuffer,
	private val pdfGenerator: CoverLetterPdfGenerator
) {
	@PostMapping(
		"/api/pdf",
		consumes = [MediaType.APPLICATION_JSON_VALUE],
		produces = [MediaType.APPLICATION_PDF_VALUE]
	)
	fun downloadPdf(
		@Valid @RequestBody request: CoverLetterRequest,
		response: ServerHttpResponse
	): ByteArray {
		val filename = try {
			CoverLetterFilename.create(request.personName, request.companyName, request.jobTitle)
		} catch (exception: IllegalArgumentException) {
			throw ResponseStatusException(HttpStatus.BAD_REQUEST, exception.message, exception)
		}
		val pdf = try {
			pdfGenerator.create(request.letter.trim())
		} catch (exception: IllegalArgumentException) {
			throw ResponseStatusException(HttpStatus.BAD_REQUEST, exception.message, exception)
		}
		logs.record("Downloaded a cover-letter PDF")
		response.headers.contentType = MediaType.APPLICATION_PDF
		response.headers.set(
			HttpHeaders.CONTENT_DISPOSITION,
			"attachment; filename=\"$filename\""
		)
		return pdf
	}
}
