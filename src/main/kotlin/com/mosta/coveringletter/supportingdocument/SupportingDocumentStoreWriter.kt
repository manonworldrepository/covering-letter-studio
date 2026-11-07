package com.mosta.coveringletter.supportingdocument

import com.mosta.coveringletter.documents.DocumentTextExtractor
import org.springframework.http.HttpStatus
import org.springframework.web.server.ResponseStatusException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.util.UUID
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

internal class SupportingDocumentStoreWriter(
	private val reader: SupportingDocumentStoreReader,
	private val extractor: DocumentTextExtractor,
	private val directory: Path,
	private val selectionMetadata: Path
) {
	private val writeLock = ReentrantLock()

	fun saveBytes(originalName: String, bytes: ByteArray): SupportingDocument = writeLock.withLock {
		if (reader.list().size >= SupportingDocumentStoreLimits.MAX_FILES) {
			throw ResponseStatusException(
				HttpStatus.BAD_REQUEST,
				"You can store up to ${SupportingDocumentStoreLimits.MAX_FILES} supporting documents. Clear some before adding more."
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
			val target = reader.resolve(document)
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
			reader.list().first { it.id == document.id }
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

	fun addToSelection(ids: Set<String>) = writeLock.withLock {
		val selectedIds = reader.list().filter { it.selected }.map { it.id }.toSet() + ids
		setSelection(selectedIds)
	}

	fun clear(): List<SupportingDocument> = writeLock.withLock {
		Files.createDirectories(directory)
		reader.list().forEach { Files.deleteIfExists(reader.resolve(it)) }
		Files.deleteIfExists(selectionMetadata)
		reader.list()
	}

	fun delete(id: String): SupportingDocument = writeLock.withLock {
		try {
			val document = reader.list().firstOrNull { it.id == id }
				?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "Supporting document was not found.")
			Files.delete(reader.resolve(document))
			if (Files.isRegularFile(selectionMetadata)) {
				setSelection(reader.list().filter { it.selected }.map { it.id }.toSet())
			}
			document
		} catch (exception: ResponseStatusException) {
			throw exception
		} catch (exception: Exception) {
			throw ResponseStatusException(
				HttpStatus.INTERNAL_SERVER_ERROR,
				"Could not remove the supporting document. Check the application directory permissions.",
				exception
			)
		}
	}

	fun select(ids: Set<String>): List<SupportingDocument> = writeLock.withLock {
		val documents = reader.list()
		if (ids.any { id -> documents.none { it.id == id } }) {
			throw ResponseStatusException(HttpStatus.BAD_REQUEST, "One or more supporting documents were not found.")
		}
		try {
			Files.createDirectories(directory)
			setSelection(ids)
			reader.list()
		} catch (exception: ResponseStatusException) {
			throw exception
		} catch (exception: Exception) {
			throw ResponseStatusException(
				HttpStatus.INTERNAL_SERVER_ERROR,
				"Could not save supporting document selections. Check the application directory permissions.",
				exception
			)
		}
	}

	private fun setSelection(ids: Set<String>) {
		val temporary = Files.createTempFile(directory, ".selected-documents-", ".tmp")
		try {
			Files.writeString(temporary, ids.joinToString("\n"))
			try {
				Files.move(
					temporary,
					selectionMetadata,
					StandardCopyOption.REPLACE_EXISTING,
					StandardCopyOption.ATOMIC_MOVE
				)
			} catch (_: java.nio.file.AtomicMoveNotSupportedException) {
				Files.move(temporary, selectionMetadata, StandardCopyOption.REPLACE_EXISTING)
			}
		} finally {
			Files.deleteIfExists(temporary)
		}
	}
}
