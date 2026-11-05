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
import java.util.UUID

data class ResumeOption(val id: String, val filename: String, val selected: Boolean)
data class ResumeStatus(
	val configured: Boolean,
	val filename: String?,
	val resumes: List<ResumeOption> = emptyList()
)

@Service
class ResumeStore(
	private val extractor: DocumentTextExtractor,
	@Value("\${coverletter.storage.directory:.data}") storageDirectory: String
) {
	private val directory: Path = Paths.get(storageDirectory).toAbsolutePath().normalize()
	private val resumesDirectory = directory.resolve("resumes")
	private val filenameMetadata = directory.resolve(".resume-filename")
	private val selectionMetadata = directory.resolve(".selected-resume")

	fun status(): ResumeStatus {
		val resumes = storedResumes().toMutableList()
		legacyResume()?.let { legacy ->
			if (resumes.none { it.id == legacy.id }) resumes.add(legacy)
		}
		val selectedId = if (Files.isRegularFile(selectionMetadata)) {
			Files.readString(selectionMetadata).trim()
		} else {
			legacyResume()?.id ?: resumes.firstOrNull()?.id
		}
		val selected = resumes.firstOrNull { it.id == selectedId } ?: resumes.firstOrNull()
		return ResumeStatus(
			selected != null,
			selected?.filename,
			resumes.map { it.copy(selected = it.id == selected?.id) }
		)
	}

	fun save(files: reactor.core.publisher.Flux<FilePart>): Mono<ResumeStatus> =
		files.collectList().flatMap { selected ->
			if (selected.isEmpty()) {
				return@flatMap Mono.error(
					ResponseStatusException(HttpStatus.BAD_REQUEST, "Choose at least one resume file.")
				)
			}
			reactor.core.publisher.Flux.fromIterable(selected)
				.concatMap(::saveFile)
				.then(Mono.fromCallable { status() })
		}

	private fun saveFile(file: FilePart): Mono<ResumeStatus> =
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

	fun select(id: String): ResumeStatus = synchronized(this) {
		val resume = status().resumes.firstOrNull { it.id == id }
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
		status()
	}

	fun extractStored(): Mono<String> =
		Mono.fromCallable {
			val resume = status().resumes.firstOrNull { it.selected }
				?: throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Upload and select a resume before generating a letter.")
			extractor.extractText(resume.filename, Files.readAllBytes(resolve(resume)))
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
			Files.createDirectories(resumesDirectory)
			val resume = ResumeOption(UUID.randomUUID().toString(), originalName, selected = true)
			val target = resolve(resume)
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
			return status()
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

	private fun storedResumes(): List<ResumeOption> {
		if (!Files.isDirectory(resumesDirectory)) return emptyList()
		return Files.list(resumesDirectory).use { paths ->
			paths.filter(Files::isRegularFile)
				.map { path ->
					val storedName = path.fileName.toString()
					val separator = storedName.indexOf("__")
					if (separator <= 0) null else {
						val id = storedName.substring(0, separator)
						val filename = storedName.substring(separator + 2)
						if (!id.matches(UUID_PATTERN) || !isSafeFilename(filename)) null
						else ResumeOption(id, filename, selected = false)
					}
				}
				.filter { it != null }
				.map { it!! }
				.sorted(compareBy(String.CASE_INSENSITIVE_ORDER) { it.filename })
				.toList()
		}
	}

	private fun legacyResume(): ResumeOption? {
		val savedFilename = if (Files.isRegularFile(filenameMetadata)) {
			Files.readString(filenameMetadata).takeIf(::isSafeFilename)
		} else {
			null
		}
		val filename = savedFilename?.takeIf { Files.isRegularFile(directory.resolve(it)) }
			?: listOf("resume.pdf", "resume.docx").firstOrNull { Files.isRegularFile(directory.resolve(it)) }
			?: return null
		return ResumeOption("legacy:$filename", filename, selected = false)
	}

	private fun resolve(resume: ResumeOption): Path {
		if (resume.id.startsWith("legacy:")) {
			val filename = resume.id.removePrefix("legacy:")
			if (filename != resume.filename || !isSafeFilename(filename)) {
				throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid resume.")
			}
			return directory.resolve(filename).normalize().also { path ->
				if (!path.startsWith(directory)) throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid resume path.")
			}
		}
		if (!resume.id.matches(UUID_PATTERN) || !isSafeFilename(resume.filename)) {
			throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid resume.")
		}
		return resumesDirectory.resolve("${resume.id}__${resume.filename}").normalize().also { path ->
			if (!path.startsWith(resumesDirectory)) throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid resume path.")
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
		val UUID_PATTERN = Regex("[0-9a-fA-F-]{36}")
	}
}
