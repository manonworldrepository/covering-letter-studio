package com.mosta.coveringletter.api

import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity

object DocumentResponseHelper {
	fun createInlineResponseEntity(filename: String, bytes: ByteArray): ResponseEntity<ByteArray> {
		val ext = filename.substringAfterLast('.', "").lowercase()
		val mediaType = when (ext) {
			"pdf" -> MediaType.APPLICATION_PDF
			"docx" -> MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.wordprocessingml.document")
			"txt", "text", "md", "csv" -> MediaType.TEXT_PLAIN
			else -> MediaType.APPLICATION_OCTET_STREAM
		}
		val sanitizedFilename = filename.replace("\"", "")
		return ResponseEntity.ok()
			.contentType(mediaType)
			.header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"$sanitizedFilename\"")
			.body(bytes)
	}
}
