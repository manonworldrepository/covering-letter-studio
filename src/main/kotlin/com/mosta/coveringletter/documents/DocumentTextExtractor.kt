package com.mosta.coveringletter.documents

import org.apache.pdfbox.Loader
import org.apache.pdfbox.text.PDFTextStripper
import org.apache.poi.xwpf.usermodel.IBodyElement
import org.apache.poi.xwpf.usermodel.XWPFDocument
import org.apache.poi.xwpf.usermodel.XWPFParagraph
import org.apache.poi.xwpf.usermodel.XWPFTable
import org.springframework.http.HttpStatus
import org.springframework.http.codec.multipart.FilePart
import org.springframework.stereotype.Component
import org.springframework.web.server.ResponseStatusException
import org.springframework.core.io.buffer.DataBufferUtils
import reactor.core.scheduler.Schedulers
import reactor.core.publisher.Mono
import java.io.ByteArrayInputStream
import java.nio.charset.Charset
import java.nio.charset.StandardCharsets

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
		if (bytes.size > DocumentTextExtractorLimits.MAX_FILE_BYTES) {
			throw ResponseStatusException(HttpStatus.PAYLOAD_TOO_LARGE, "Each file must be 10 MB or smaller.")
		}

		return try {
			when (filename.substringAfterLast('.', "").lowercase()) {
				"pdf" -> Loader.loadPDF(bytes).use { PDFTextStripper().getText(it) }
				"docx" -> extractDocx(bytes)
				"txt", "text", "md", "csv", "log" -> decodeTextFile(bytes)
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

	private fun extractDocx(bytes: ByteArray): String =
		XWPFDocument(ByteArrayInputStream(bytes)).use { document ->
			buildString {
				document.bodyElements.forEach { element ->
					appendBodyElement(element)
				}
			}.trim()
		}

	private fun StringBuilder.appendBodyElement(element: IBodyElement) {
		when (element) {
			is XWPFParagraph -> {
				val text = element.text.trim()
				if (text.isEmpty()) return
				val style = element.style ?: ""
				if (style.startsWith("Heading", ignoreCase = true)) {
					appendLine("# $text")
				} else {
					appendLine(text)
				}
			}
			is XWPFTable -> {
				element.rows.forEach { row ->
					appendLine(row.tableCells.joinToString(" | ") { it.text.trim() })
				}
				appendLine()
			}
		}
	}

	private fun decodeTextFile(bytes: ByteArray): String {
		val decoded = String(bytes, StandardCharsets.UTF_8)
		return if (decoded.contains('\uFFFD') && bytes.isNotEmpty()) {
			String(bytes, Charset.defaultCharset())
		} else {
			decoded
		}
	}
}
