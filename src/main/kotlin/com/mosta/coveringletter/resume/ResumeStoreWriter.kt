package com.mosta.coveringletter.resume

import com.mosta.coveringletter.documents.DocumentTextExtractor
import org.springframework.http.HttpStatus
import org.springframework.web.server.ResponseStatusException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.util.UUID
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

internal class ResumeStoreWriter(
	private val reader: ResumeStoreReader,
	private val extractor: DocumentTextExtractor,
	private val directory: Path,
	private val resumesDirectory: Path,
	private val filenameMetadata: Path,
	private val selectionMetadata: Path
) {
	private val writeLock = ReentrantLock()

	fun select(id: String): ResumeStatus = writeLock.withLock {
		val resume = reader.status().resumes.firstOrNull { it.id == id }
			?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "Resume was not found.")
		try {
			Files.createDirectories(directory)
			writeAtomically(selectionMetadata, resume.id)
		} catch (exception: Exception) {
			throw ResponseStatusException(
				HttpStatus.INTERNAL_SERVER_ERROR,
				"Could not select the resume. Check the application directory permissions.",
				exception
			)
		}
		reader.status()
	}

	fun delete(id: String): ResumeOption = writeLock.withLock {
		val resume = reader.status().resumes.firstOrNull { it.id == id }
			?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "Resume was not found.")
		try {
			Files.delete(reader.resolve(resume))
			if (resume.id.startsWith("legacy:") &&
				Files.isRegularFile(filenameMetadata) &&
				Files.readString(filenameMetadata).trim() == resume.filename
			) {
				Files.deleteIfExists(filenameMetadata)
			}
			if (resume.selected) {
				val remaining = reader.status().resumes.firstOrNull()
				if (remaining == null) {
					Files.deleteIfExists(selectionMetadata)
				} else {
					Files.createDirectories(directory)
					writeAtomically(selectionMetadata, remaining.id)
				}
			}
			resume
		} catch (exception: ResponseStatusException) {
			throw exception
		} catch (exception: Exception) {
			throw ResponseStatusException(
				HttpStatus.INTERNAL_SERVER_ERROR,
				"Could not remove the resume. Check the application directory permissions.",
				exception
			)
		}
	}

	fun clear(): List<ResumeOption> = writeLock.withLock {
		val resumes = reader.status().resumes.toMutableList()
		reader.legacyResumeCandidates().forEach { filename ->
			val legacy = ResumeOption("legacy:$filename", filename, selected = false)
			if (resumes.none { it.id == legacy.id }) resumes.add(legacy)
		}
		try {
			resumes.forEach { Files.deleteIfExists(reader.resolve(it)) }
			Files.deleteIfExists(selectionMetadata)
			Files.deleteIfExists(filenameMetadata)
			resumes
		} catch (exception: ResponseStatusException) {
			throw exception
		} catch (exception: Exception) {
			throw ResponseStatusException(
				HttpStatus.INTERNAL_SERVER_ERROR,
				"Could not clear resumes. Check the application directory permissions.",
				exception
			)
		}
	}

	fun saveBytes(originalName: String, bytes: ByteArray): ResumeStatus = writeLock.withLock {
		if (!reader.isSafeFilename(originalName)) {
			throw ResponseStatusException(
				HttpStatus.BAD_REQUEST,
				"Resume filename must be a plain filename without path characters and no longer than 180 characters."
			)
		}
		val extension = originalName.substringAfterLast('.', "").lowercase()
		if (extension !in setOf("pdf", "docx")) {
			throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Resume must be a PDF or DOCX file.")
		}
		val extracted = extractor.extractText(originalName, bytes)
		if (extracted.isBlank()) {
			throw ResponseStatusException(HttpStatus.BAD_REQUEST, "The resume contains no extractable text.")
		}

		try {
			Files.createDirectories(resumesDirectory)
			val resume = ResumeOption(UUID.randomUUID().toString(), originalName, selected = true)
			val target = reader.resolve(resume)
			val temporary = Files.createTempFile(resumesDirectory, "resume-", ".$extension.tmp")
			try {
				Files.write(temporary, bytes)
				moveAtomically(temporary, target)
			} finally {
				Files.deleteIfExists(temporary)
			}
			try {
				writeAtomically(selectionMetadata, resume.id)
			} catch (exception: Exception) {
				Files.deleteIfExists(target)
				throw exception
			}
			reader.status()
		} catch (exception: ResponseStatusException) {
			throw exception
		} catch (exception: Exception) {
			throw ResponseStatusException(
				HttpStatus.INTERNAL_SERVER_ERROR,
				"Could not save the resume. Check the application directory permissions.",
				exception
			)
		}
	}

	private fun writeAtomically(target: Path, value: String) {
		val temporary = Files.createTempFile(directory, ".resume-selection-", ".tmp")
		try {
			Files.writeString(temporary, value)
			moveAtomically(temporary, target)
		} finally {
			Files.deleteIfExists(temporary)
		}
	}

	private fun moveAtomically(source: Path, target: Path) {
		try {
			Files.move(source, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
		} catch (_: java.nio.file.AtomicMoveNotSupportedException) {
			Files.move(source, target, StandardCopyOption.REPLACE_EXISTING)
		}
	}
}
