package com.mosta.coveringletter.supportingdocument

import com.mosta.coveringletter.documents.DocumentTextExtractor
import org.springframework.http.HttpStatus
import org.springframework.web.server.ResponseStatusException
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import reactor.core.scheduler.Schedulers
import java.nio.file.Files
import java.nio.file.Path

internal class SupportingDocumentStoreReader(
	private val extractor: DocumentTextExtractor,
	private val directory: Path,
	private val selectionMetadata: Path
) {
	fun list(): List<SupportingDocument> {
		if (!Files.isDirectory(directory)) return emptyList()
		val selectedIds = if (Files.isRegularFile(selectionMetadata)) {
			Files.readAllLines(selectionMetadata).toSet()
		} else {
			null
		}
		return Files.list(directory).use { paths ->
			paths.filter(Files::isRegularFile)
				.map { path ->
					val storedName = path.fileName.toString()
					val separator = storedName.indexOf("__")
					if (separator <= 0) null else SupportingDocument(
						storedName.substring(0, separator),
						storedName.substring(separator + 2),
						selected = selectedIds?.contains(storedName.substring(0, separator)) ?: true
					)
				}
				.filter { it != null }
				.map { it!! }
				.sorted(compareBy(String.CASE_INSENSITIVE_ORDER) { it.filename })
				.toList()
		}
	}

	fun extractAll(): Mono<String> =
		Flux.fromIterable(list().filter { it.selected })
			.flatMap({ document ->
				Mono.fromCallable {
					val text = extractor.extractText(document.filename, Files.readAllBytes(resolve(document)))
					"${document.filename}:\n$text"
				}.subscribeOn(Schedulers.boundedElastic())
			}, 2)
			.collectList()
			.map { documents ->
				val text = documents.joinToString("\n\n")
				if (text.length > SupportingDocumentStoreLimits.MAX_EXTRACTED_TEXT_LENGTH) {
					throw ResponseStatusException(
						HttpStatus.PAYLOAD_TOO_LARGE,
						"Supporting documents are too long. Use documents totaling no more than 100,000 characters."
					)
				}
				text
			}

	fun resolve(document: SupportingDocument): Path {
		if (!document.id.matches(Regex("[0-9a-fA-F-]{36}")) ||
			document.filename.contains('/') || document.filename.contains('\\')
		) {
			throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid supporting document.")
		}
		val extension = document.filename.substringAfterLast('.', "").lowercase()
		if (extension !in setOf("pdf", "docx")) {
			throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid supporting document type.")
		}
		return directory.resolve("${document.id}__${document.filename}").normalize().also { path ->
			if (!path.startsWith(directory)) {
				throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid supporting document path.")
			}
		}
	}
}
