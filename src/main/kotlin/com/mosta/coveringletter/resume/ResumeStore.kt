package com.mosta.coveringletter.resume

import com.mosta.coveringletter.api.response.DocumentViewResponse
import com.mosta.coveringletter.documents.DocumentTextExtractor
import com.mosta.coveringletter.persistence.AccountDataDirectories
import org.springframework.beans.factory.annotation.Value
import org.springframework.core.io.buffer.DataBufferLimitException
import org.springframework.core.io.buffer.DataBufferUtils
import org.springframework.http.HttpStatus
import org.springframework.http.codec.multipart.FilePart
import org.springframework.stereotype.Service
import org.springframework.web.server.ResponseStatusException
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import reactor.core.scheduler.Schedulers
import java.nio.file.Files
import java.nio.file.Paths
import java.nio.file.Path
import java.nio.file.StandardCopyOption

@Service
class ResumeStore(
	private val extractor: DocumentTextExtractor,
	@Value("\${coverletter.storage.directory:.data}") storageDirectory: String
) {
	private val directory = Paths.get(storageDirectory).toAbsolutePath().normalize()
	private val accountDirectories = AccountDataDirectories(directory)
	private val sharedWorkspace = workspace(directory)

	private fun workspace(baseDirectory: Path): ResumeWorkspace {
		val resumesDirectory = baseDirectory.resolve("resumes")
		val reader = ResumeStoreReader(
			extractor,
			baseDirectory,
			resumesDirectory,
			baseDirectory.resolve(".resume-filename"),
			baseDirectory.resolve(".selected-resume")
		)
		return ResumeWorkspace(
			baseDirectory,
			reader,
			ResumeStoreWriter(
				reader,
				extractor,
				baseDirectory,
				resumesDirectory,
				baseDirectory.resolve(".resume-filename"),
				baseDirectory.resolve(".selected-resume")
			)
		)
	}

	private fun accountWorkspace(email: String): ResumeWorkspace = workspace(accountDirectories.forEmail(email))

	fun status(accountEmail: String? = null): ResumeStatus {
		if (accountEmail == null) return sharedWorkspace.reader.status()
		val account = accountWorkspace(accountEmail)
		val personal = account.reader.status()
		val shared = sharedWorkspace.reader.status()
		val resumes = (personal.resumes + shared.resumes.map { it.copy(shared = true) }).distinctBy { it.id }
		val accountSelection = account.directory.resolve(".selected-resume")
		val requestedSelection = if (Files.isRegularFile(accountSelection)) Files.readString(accountSelection).trim() else null
		val selectedId = requestedSelection?.takeIf { id -> resumes.any { it.id == id } }
			?: personal.resumes.firstOrNull { it.selected }?.id
			?: shared.resumes.firstOrNull { it.selected }?.id
		val selected = resumes.firstOrNull { it.id == selectedId }
		return ResumeStatus(
			selected != null,
			selected?.filename,
			resumes.map { it.copy(selected = it.id == selected?.id) }
		)
	}

	fun save(files: Flux<FilePart>, accountEmail: String? = null): Mono<ResumeStatus> {
		val target = accountEmail?.let(::accountWorkspace) ?: sharedWorkspace
		return files.collectList().flatMap { selected ->
			if (selected.isEmpty()) {
				return@flatMap Mono.error(
					ResponseStatusException(HttpStatus.BAD_REQUEST, "Choose at least one resume file.")
				)
			}
			Flux.fromIterable(selected)
				.concatMap { file -> saveFile(file, target) }
				.then(Mono.fromCallable { status(accountEmail) })
		}
	}

	private fun saveFile(file: FilePart, target: ResumeWorkspace): Mono<ResumeStatus> =
		DataBufferUtils.join(file.content(), ResumeStoreLimits.MAX_FILE_BYTES)
			.flatMap { buffer ->
				val bytes = try {
					ByteArray(buffer.readableByteCount()).also(buffer::read)
				} finally {
					DataBufferUtils.release(buffer)
				}
				Mono.fromCallable { target.writer.saveBytes(file.filename(), bytes) }
					.subscribeOn(Schedulers.boundedElastic())
			}
			.onErrorMap(DataBufferLimitException::class.java) {
				ResponseStatusException(HttpStatus.PAYLOAD_TOO_LARGE, "Resume must be 10 MB or smaller.", it)
			}
			.switchIfEmpty(Mono.error(ResponseStatusException(HttpStatus.BAD_REQUEST, "Choose a resume file.")))

	fun select(id: String, accountEmail: String? = null): ResumeStatus {
		if (accountEmail == null) return sharedWorkspace.writer.select(id)
		val account = accountWorkspace(accountEmail)
		val resume = status(accountEmail).resumes.firstOrNull { it.id == id }
			?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "Resume was not found.")
		if (account.reader.find(id) != null) {
			account.writer.select(id)
		} else {
			Files.createDirectories(account.directory)
			writeSelection(account.directory.resolve(".selected-resume"), resume.id)
		}
		return status(accountEmail)
	}

	fun delete(id: String, accountEmail: String? = null): ResumeOption {
		if (accountEmail == null) return sharedWorkspace.writer.delete(id)
		val sharedResume = sharedWorkspace.reader.find(id)
		if (sharedResume != null) {
			val removed = sharedWorkspace.writer.delete(id)
			deleteSharedCopies(id)
			return removed.copy(shared = true)
		}
		val account = accountWorkspace(accountEmail)
		if (account.reader.find(id) == null) {
			throw ResponseStatusException(HttpStatus.NOT_FOUND, "Resume not found.")
		}
		return account.writer.delete(id)
	}

	fun clear(accountEmail: String? = null): List<ResumeOption> {
		if (accountEmail == null) return sharedWorkspace.writer.clear()
		val account = accountWorkspace(accountEmail)
		val personal = account.reader.status().resumes
		account.writer.clear()
		Files.deleteIfExists(account.directory.resolve(".selected-resume"))
		val removedShared = sharedWorkspace.writer.clear()
		removedShared.forEach { deleteSharedCopies(it.id) }
		return (personal + removedShared).distinctBy { it.id }
	}

	fun extractStored(accountEmail: String? = null): Mono<String> =
		Mono.fromCallable {
			val selected = status(accountEmail).resumes.firstOrNull { it.selected }
				?: throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Upload and select a resume before generating a letter.")
			val target = resolve(selected, accountEmail)
			extractor.extractText(selected.filename, Files.readAllBytes(target))
		}.subscribeOn(Schedulers.boundedElastic())

	fun getDocumentView(id: String, accountEmail: String? = null): DocumentViewResponse {
		val resume = status(accountEmail).resumes.firstOrNull { it.id == id }
			?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "Resume not found.")
		val path = resolve(resume, accountEmail)
		if (!Files.isRegularFile(path)) {
			throw ResponseStatusException(HttpStatus.NOT_FOUND, "Resume file not found.")
		}
		val bytes = Files.readAllBytes(path)
		val ext = resume.filename.substringAfterLast('.', "").lowercase()
		val text = try {
			extractor.extractText(resume.filename, bytes)
		} catch (e: Exception) {
			""
		}
		return DocumentViewResponse(
			id = resume.id,
			filename = resume.filename,
			extension = ext,
			viewable = ext in setOf("pdf", "txt", "text", "md", "csv", "log"),
			rawUrl = "/api/resume/${resume.id}/raw",
			text = text
		)
	}

	fun getDocumentBytes(id: String, accountEmail: String? = null): Pair<String, ByteArray> {
		val resume = status(accountEmail).resumes.firstOrNull { it.id == id }
			?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "Resume not found.")
		val path = resolve(resume, accountEmail)
		if (!Files.isRegularFile(path)) {
			throw ResponseStatusException(HttpStatus.NOT_FOUND, "Resume file not found.")
		}
		return resume.filename to Files.readAllBytes(path)
	}

	internal fun saveBytes(originalName: String, bytes: ByteArray, accountEmail: String? = null): ResumeStatus {
		val target = accountEmail?.let(::accountWorkspace) ?: sharedWorkspace
		target.writer.saveBytes(originalName, bytes)
		return status(accountEmail)
	}

	private fun resolve(resume: ResumeOption, accountEmail: String?): Path {
		if (accountEmail != null) {
			val account = accountWorkspace(accountEmail)
			account.reader.find(resume.id)?.let { return account.reader.resolve(it) }
		}
		val sharedResume = sharedWorkspace.reader.find(resume.id)
			?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "Resume not found.")
		return sharedWorkspace.reader.resolve(sharedResume)
	}

	private fun deleteSharedCopies(id: String) {
		val accountsDirectory = directory.resolve("accounts")
		if (!Files.isDirectory(accountsDirectory)) return
		Files.list(accountsDirectory).use { accounts ->
			accounts.filter(Files::isDirectory).forEach { accountDirectory ->
				val account = workspace(accountDirectory)
				if (account.reader.find(id) != null) account.writer.delete(id)
			}
		}
	}

	private fun writeSelection(target: Path, value: String) {
		val temporary = Files.createTempFile(target.parent, ".resume-selection-", ".tmp")
		try {
			Files.writeString(temporary, value)
			try {
				Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
			} catch (_: java.nio.file.AtomicMoveNotSupportedException) {
				Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING)
			}
		} finally {
			Files.deleteIfExists(temporary)
		}
	}

	private data class ResumeWorkspace(
		val directory: Path,
		val reader: ResumeStoreReader,
		val writer: ResumeStoreWriter
	)
}
