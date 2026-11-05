package com.mosta.coveringletter

import org.apache.pdfbox.Loader
import org.apache.pdfbox.text.PDFTextStripper
import org.apache.poi.xwpf.usermodel.XWPFDocument
import org.springframework.http.HttpStatus
import org.springframework.http.codec.multipart.FilePart
import org.springframework.stereotype.Component
import org.springframework.web.server.ResponseStatusException
import org.springframework.core.io.buffer.DataBufferUtils
import reactor.core.scheduler.Schedulers
import reactor.core.publisher.Mono
import java.io.ByteArrayInputStream

@Component
class DocumentTextExtractor {
	fun extract(file: FilePart): Mono<String> =
		DataBufferUtils.join(file.content())
			.map { buffer ->
				try {
					val bytes = ByteArray(buffer.readableByteCount())
					buffer.read(bytes)
					bytes
				} finally {
					DataBufferUtils.release(buffer)
				}
			}
			.defaultIfEmpty(ByteArray(0))
			.flatMap { bytes -> Mono.fromCallable { extractText(file.filename(), bytes) }.subscribeOn(Schedulers.boundedElastic()) }

	fun extractText(filename: String, bytes: ByteArray): String {
		if (bytes.size > MAX_FILE_BYTES) {
			throw ResponseStatusException(HttpStatus.PAYLOAD_TOO_LARGE, "Each file must be 10 MB or smaller.")
		}

		return try {
			when (filename.substringAfterLast('.', "").lowercase()) {
				"pdf" -> Loader.loadPDF(bytes).use { PDFTextStripper().getText(it) }
				"docx" -> XWPFDocument(ByteArrayInputStream(bytes)).use { document ->
					buildString {
						document.paragraphs.forEach { appendLine(it.text) }
						document.tables.forEach { table ->
							table.rows.forEach { row ->
								appendLine(row.tableCells.joinToString(" | ") { it.text })
							}
						}
					}
				}
				else -> throw ResponseStatusException(
					HttpStatus.BAD_REQUEST,
					"Unsupported file type for '$filename'. Upload PDF or DOCX files."
				)
			}
		} catch (exception: ResponseStatusException) {
			throw exception
		} catch (exception: Exception) {
			throw ResponseStatusException(
				HttpStatus.BAD_REQUEST,
				"Could not read '$filename'. Make sure it is a valid, text-based PDF or DOCX file.",
				exception
			)
		}
	}

	private companion object {
		const val MAX_FILE_BYTES = 10 * 1024 * 1024
	}
}
