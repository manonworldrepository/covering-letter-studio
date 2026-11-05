package com.mosta.coveringletter

import org.springframework.core.io.buffer.DataBufferUtils
import org.springframework.core.io.buffer.DataBufferLimitException
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.HttpStatus
import org.springframework.http.codec.multipart.FilePart
import org.springframework.stereotype.Service
import org.springframework.web.server.ResponseStatusException
import reactor.core.publisher.Mono
import reactor.core.scheduler.Schedulers
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.nio.file.StandardCopyOption

data class ResumeStatus(val configured: Boolean, val filename: String?)

@Service
class ResumeStore(
	private val extractor: DocumentTextExtractor,
	@Value("\${coverletter.storage.directory:.data}") storageDirectory: String
) {
	private val directory: Path = Paths.get(storageDirectory).toAbsolutePath().normalize()
	private val filenameMetadata = directory.resolve(".resume-filename")

	fun status(): ResumeStatus {
		val savedFilename = if (Files.isRegularFile(filenameMetadata)) {
			Files.readString(filenameMetadata).takeIf(::isSafeFilename)
		} else {
			null
		}
		val existing = savedFilename?.takeIf { Files.isRegularFile(directory.resolve(it)) }
			?: listOf("resume.pdf", "resume.docx").firstOrNull { Files.isRegularFile(directory.resolve(it)) }
		return ResumeStatus(existing != null, existing)
	}

	fun save(file: FilePart): Mono<ResumeStatus> =
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
				ResponseStatusException(HttpStatus.PAYLOAD_TOO_LARGE, "Resume must be 10 MB or smaller.", it)
			}
			.switchIfEmpty(Mono.error(ResponseStatusException(HttpStatus.BAD_REQUEST, "Choose a resume file.")))

	fun extractStored(): Mono<String> =
		Mono.fromCallable {
			val filename = status().filename
				?: throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Upload your resume before generating a letter.")
			val path = directory.resolve(filename)
			extractor.extractText(filename, Files.readAllBytes(path))
		}.subscribeOn(Schedulers.boundedElastic())

	@Synchronized
	internal fun saveBytes(originalName: String, bytes: ByteArray): ResumeStatus {
		if (!isSafeFilename(originalName)) {
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
			Files.createDirectories(directory)
			val previousFilename = status().filename
			val target = directory.resolve(originalName).normalize()
			if (!target.startsWith(directory)) {
				throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid resume filename.")
			}
			val temporary = Files.createTempFile(directory, "resume-", ".$extension.tmp")
			val temporaryMetadata = Files.createTempFile(directory, ".resume-filename-", ".tmp")
			try {
				Files.write(temporary, bytes)
				try {
					Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
				} catch (_: java.nio.file.AtomicMoveNotSupportedException) {
					Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING)
				}
				Files.writeString(temporaryMetadata, originalName)
				try {
					Files.move(
						temporaryMetadata,
						filenameMetadata,
						StandardCopyOption.REPLACE_EXISTING,
						StandardCopyOption.ATOMIC_MOVE
					)
				} catch (_: java.nio.file.AtomicMoveNotSupportedException) {
					Files.move(temporaryMetadata, filenameMetadata, StandardCopyOption.REPLACE_EXISTING)
				}
			} finally {
				Files.deleteIfExists(temporary)
				Files.deleteIfExists(temporaryMetadata)
			}
			previousFilename?.takeIf { it != originalName }?.let { previous ->
				val previousPath = directory.resolve(previous).normalize()
				if (previousPath.startsWith(directory)) Files.deleteIfExists(previousPath)
			}
			return ResumeStatus(true, originalName)
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

	private fun isSafeFilename(filename: String): Boolean =
		filename.isNotBlank() &&
			filename.length <= MAX_FILENAME_LENGTH &&
			filename != "." &&
			filename != ".." &&
			!filename.contains('/') &&
			!filename.contains('\\') &&
			!filename.contains(':') &&
			!filename.contains('*') &&
			!filename.contains('?') &&
			!filename.contains('"') &&
			!filename.contains('<') &&
			!filename.contains('>') &&
			!filename.contains('|') &&
			!filename.endsWith('.') &&
			!filename.endsWith(' ') &&
			filename != filenameMetadata.fileName.toString()

	private companion object {
		const val MAX_FILE_BYTES = 10 * 1024 * 1024
		const val MAX_FILENAME_LENGTH = 180
	}
}
