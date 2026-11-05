package com.mosta.coveringletter

import org.springframework.beans.factory.annotation.Value
import org.springframework.core.io.buffer.DataBufferUtils
import org.springframework.core.io.buffer.DataBufferLimitException
import org.springframework.http.HttpStatus
import org.springframework.http.codec.multipart.FilePart
import org.springframework.stereotype.Service
import org.springframework.web.server.ResponseStatusException
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import reactor.core.scheduler.Schedulers
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.nio.file.StandardCopyOption
import java.util.UUID

data class SupportingDocument(val id: String, val filename: String)

@Service
class SupportingDocumentStore(
	private val extractor: DocumentTextExtractor,
	@Value("\${coverletter.storage.directory:.data}") storageDirectory: String
) {
	private val directory: Path = Paths.get(storageDirectory).toAbsolutePath().normalize().resolve("supporting-documents")

	fun list(): List<SupportingDocument> {
		if (!Files.isDirectory(directory)) return emptyList()
		return Files.list(directory).use { paths ->
			paths.filter(Files::isRegularFile)
				.map { path ->
					val storedName = path.fileName.toString()
					val separator = storedName.indexOf("__")
					if (separator <= 0) null else SupportingDocument(
						storedName.substring(0, separator),
						storedName.substring(separator + 2)
					)
				}
				.filter { it != null }
				.map { it!! }
				.sorted(compareBy(String.CASE_INSENSITIVE_ORDER) { it.filename })
				.toList()
		}
	}

	fun save(files: Flux<FilePart>): Mono<List<SupportingDocument>> =
		files.collectList().flatMap { selected ->
			val existingCount = list().size
			if (selected.isEmpty()) {
				return@flatMap Mono.error(
					ResponseStatusException(HttpStatus.BAD_REQUEST, "Choose at least one supporting file.")
				)
			}
			if (existingCount + selected.size > MAX_FILES) {
				return@flatMap Mono.error(
					ResponseStatusException(
						HttpStatus.BAD_REQUEST,
						"You can store up to $MAX_FILES supporting documents. Clear some before adding more."
					)
				)
			}
			Flux.fromIterable(selected)
				.flatMap({ file -> save(file) }, 2)
				.collectList()
		}

	fun save(file: FilePart): Mono<SupportingDocument> =
		DataBufferUtils.join(file.content(), MAX_FILE_BYTES)
			.flatMap { buffer ->
				val bytes = try {
					ByteArray(buffer.readableByteCount()).also(buffer::read)
				} finally {
					DataBufferUtils.release(buffer)
				}
				Mono.fromCallable { saveBytes(file.filename(), bytes) }
					.subscribeOn(Schedulers.boundedElastic())
			}
			.onErrorMap(DataBufferLimitException::class.java) {
				ResponseStatusException(HttpStatus.PAYLOAD_TOO_LARGE, "Each supporting file must be 10 MB or smaller.", it)
			}
			.switchIfEmpty(Mono.error(ResponseStatusException(HttpStatus.BAD_REQUEST, "Choose a supporting file.")))

	fun extractAll(): Mono<String> =
		Flux.fromIterable(list())
			.flatMap({ document ->
				Mono.fromCallable {
					val path = resolve(document)
					val text = extractor.extractText(document.filename, Files.readAllBytes(path))
					"${document.filename}:\n$text"
				}.subscribeOn(Schedulers.boundedElastic())
			}, 2)
			.collectList()
			.map { documents ->
				val text = documents.joinToString("\n\n")
				if (text.length > MAX_EXTRACTED_TEXT_LENGTH) {
					throw ResponseStatusException(
						HttpStatus.PAYLOAD_TOO_LARGE,
						"Supporting documents are too long. Use documents totaling no more than 100,000 characters."
					)
				}
				text
			}

	fun clear(): Mono<List<SupportingDocument>> =
		Mono.fromCallable {
			Files.createDirectories(directory)
			list().forEach { Files.deleteIfExists(resolve(it)) }
			list()
		}.subscribeOn(Schedulers.boundedElastic())

	fun delete(id: String): Mono<SupportingDocument> =
		Mono.fromCallable {
			val document = list().firstOrNull { it.id == id }
				?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "Supporting document was not found.")
			Files.delete(resolve(document))
			document
		}.subscribeOn(Schedulers.boundedElastic())

	@Synchronized
	internal fun saveBytes(originalName: String, bytes: ByteArray): SupportingDocument {
		if (list().size >= MAX_FILES) {
			throw ResponseStatusException(
				HttpStatus.BAD_REQUEST,
				"You can store up to $MAX_FILES supporting documents. Clear some before adding more."
			)
		}
		val extension = originalName.substringAfterLast('.', "").lowercase()
		if (extension !in setOf("pdf", "docx")) {
			throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Supporting files must be PDF or DOCX.")
		}
		val safeName = originalName.substringAfterLast('/', originalName.substringAfterLast('\\'))
			.replace(Regex("[^\\p{L}\\p{N}._ -]"), "_")
			.trim()
			.take(100)
			.takeIf(String::isNotBlank)
			?: "document.$extension"
		val extracted = extractor.extractText(safeName, bytes)
		if (extracted.isBlank()) {
			throw ResponseStatusException(HttpStatus.BAD_REQUEST, "'$safeName' contains no extractable text.")
		}

		try {
			Files.createDirectories(directory)
			val id = UUID.randomUUID().toString()
			val document = SupportingDocument(id, safeName)
			val target = resolve(document)
			val temporary = Files.createTempFile(directory, "document-", ".$extension.tmp")
			try {
				Files.write(temporary, bytes)
				try {
					Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
				} catch (_: java.nio.file.AtomicMoveNotSupportedException) {
					Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING)
				}
			} finally {
				Files.deleteIfExists(temporary)
			}
			return document
		} catch (exception: ResponseStatusException) {
			throw exception
		} catch (exception: Exception) {
			throw ResponseStatusException(
				HttpStatus.INTERNAL_SERVER_ERROR,
				"Could not save '$safeName'. Check the application directory permissions.",
				exception
			)
		}
	}

	private fun resolve(document: SupportingDocument): Path {
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

	private companion object {
		const val MAX_FILES = 5
		const val MAX_FILE_BYTES = 10 * 1024 * 1024
		const val MAX_EXTRACTED_TEXT_LENGTH = 100_000
	}
}
