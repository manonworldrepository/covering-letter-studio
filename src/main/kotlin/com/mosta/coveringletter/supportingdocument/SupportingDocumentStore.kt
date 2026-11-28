package com.mosta.coveringletter.supportingdocument

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
class SupportingDocumentStore(
	private val extractor: DocumentTextExtractor,
	@Value("\${coverletter.storage.directory:.data}") storageDirectory: String
) {
	private val rootDirectory = Paths.get(storageDirectory).toAbsolutePath().normalize()
	private val sharedDirectory = rootDirectory.resolve("supporting-documents")
	private val accountDirectories = AccountDataDirectories(rootDirectory)
	private val sharedWorkspace = workspace(sharedDirectory)

	private fun workspace(directory: Path): SupportingDocumentWorkspace {
		val selectionMetadata = directory.resolve(".selected-documents")
		val reader = SupportingDocumentStoreReader(extractor, directory, selectionMetadata)
		return SupportingDocumentWorkspace(
			directory,
			reader,
			SupportingDocumentStoreWriter(reader, extractor, directory, selectionMetadata)
		)
	}

	private fun accountWorkspace(email: String): SupportingDocumentWorkspace =
		workspace(accountDirectories.forEmail(email).resolve("supporting-documents"))

	fun list(accountEmail: String? = null): List<SupportingDocument> {
		if (accountEmail == null) return sharedWorkspace.reader.list()
		val account = accountWorkspace(accountEmail)
		val shared = sharedWorkspace.reader.list().map { it.copy(shared = true) }
		val personal = account.reader.list()
		val all = (personal + shared).distinctBy { it.id }
		val selectionFile = account.directory.resolve(".selected-documents")
		if (!Files.isRegularFile(selectionFile)) return all
		val selectedIds = Files.readAllLines(selectionFile).toSet()
		return all.map { it.copy(selected = it.id in selectedIds) }
	}

	fun save(files: Flux<FilePart>, accountEmail: String? = null): Mono<List<SupportingDocument>> =
		files.collectList().flatMap { selected ->
			if (selected.isEmpty()) {
				return@flatMap Mono.error(
					ResponseStatusException(HttpStatus.BAD_REQUEST, "Choose at least one supporting file.")
				)
			}
			val target = accountEmail?.let(::accountWorkspace) ?: sharedWorkspace
			if (target.reader.list().size + selected.size > SupportingDocumentStoreLimits.MAX_FILES) {
				return@flatMap Mono.error(
					ResponseStatusException(
						HttpStatus.BAD_REQUEST,
						"You can store up to ${SupportingDocumentStoreLimits.MAX_FILES} supporting documents. Clear some before adding more."
					)
				)
			}
			val wasSelected = list(accountEmail).filter { it.selected }.map { it.id }.toSet()
			Flux.fromIterable(selected)
				.concatMap { file -> save(file, target) }
				.collectList()
				.flatMap { saved ->
					Mono.fromCallable {
						val selectedIds = wasSelected + saved.map { it.id }
						if (accountEmail == null) {
							target.writer.addToSelection(saved.map { it.id }.toSet())
						} else {
							writeSelection(target.directory.resolve(".selected-documents"), selectedIds)
						}
						list(accountEmail)
					}.subscribeOn(Schedulers.boundedElastic())
				}
		}

	private fun save(file: FilePart, target: SupportingDocumentWorkspace): Mono<SupportingDocument> =
		DataBufferUtils.join(file.content(), SupportingDocumentStoreLimits.MAX_FILE_BYTES)
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
				ResponseStatusException(HttpStatus.PAYLOAD_TOO_LARGE, "Each supporting file must be 10 MB or smaller.", it)
			}
			.switchIfEmpty(Mono.error(ResponseStatusException(HttpStatus.BAD_REQUEST, "Choose a supporting file.")))

	fun extractAll(accountEmail: String? = null): Mono<String> =
		Flux.fromIterable(list(accountEmail).filter { it.selected })
			.flatMap({ document ->
				Mono.fromCallable {
					val path = resolve(document, accountEmail)
					val text = extractor.extractText(document.filename, Files.readAllBytes(path))
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

	fun clear(accountEmail: String? = null): Mono<List<SupportingDocument>> =
		Mono.fromCallable {
			if (accountEmail == null) return@fromCallable sharedWorkspace.writer.clear()
			val account = accountWorkspace(accountEmail)
			val privateDocuments = account.reader.list()
			val sharedDocuments = sharedWorkspace.reader.list()
			account.writer.clear()
			sharedWorkspace.writer.clear()
			sharedDocuments.forEach { deleteSharedCopies(it.id) }
			(privateDocuments + sharedDocuments).distinctBy { it.id }
		}.subscribeOn(Schedulers.boundedElastic())

	fun delete(id: String, accountEmail: String? = null): Mono<SupportingDocument> =
		Mono.fromCallable {
			if (accountEmail == null) return@fromCallable sharedWorkspace.writer.delete(id)
			val sharedDocument = sharedWorkspace.reader.find(id)
			if (sharedDocument != null) {
				val removed = sharedWorkspace.writer.delete(id)
				deleteSharedCopies(id)
				return@fromCallable removed.copy(shared = true)
			}
			val account = accountWorkspace(accountEmail)
			if (account.reader.find(id) == null) {
				throw ResponseStatusException(HttpStatus.NOT_FOUND, "Supporting document not found.")
			}
			account.writer.delete(id)
		}.subscribeOn(Schedulers.boundedElastic())

	fun select(ids: Set<String>, accountEmail: String? = null): List<SupportingDocument> {
		if (accountEmail == null) return sharedWorkspace.writer.select(ids)
		val available = list(accountEmail)
		if (ids.any { id -> available.none { it.id == id } }) {
			throw ResponseStatusException(HttpStatus.BAD_REQUEST, "One or more supporting documents were not found.")
		}
		val selectionFile = accountWorkspace(accountEmail).directory.resolve(".selected-documents")
		Files.createDirectories(selectionFile.parent)
		writeSelection(selectionFile, ids)
		return list(accountEmail)
	}

	fun getDocumentView(id: String, accountEmail: String? = null): DocumentViewResponse {
		val doc = list(accountEmail).firstOrNull { it.id == id }
			?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "Supporting document not found.")
		val path = resolve(doc, accountEmail)
		if (!Files.isRegularFile(path)) {
			throw ResponseStatusException(HttpStatus.NOT_FOUND, "Supporting document file not found.")
		}
		val bytes = Files.readAllBytes(path)
		val ext = doc.filename.substringAfterLast('.', "").lowercase()
		val text = try {
			extractor.extractText(doc.filename, bytes)
		} catch (e: Exception) {
			""
		}
		return DocumentViewResponse(
			id = doc.id,
			filename = doc.filename,
			extension = ext,
			viewable = ext in setOf("pdf", "txt", "text", "md", "csv", "log"),
			rawUrl = "/api/supporting-documents/${doc.id}/raw",
			text = text
		)
	}

	fun getDocumentBytes(id: String, accountEmail: String? = null): Pair<String, ByteArray> {
		val doc = list(accountEmail).firstOrNull { it.id == id }
			?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "Supporting document not found.")
		val path = resolve(doc, accountEmail)
		if (!Files.isRegularFile(path)) {
			throw ResponseStatusException(HttpStatus.NOT_FOUND, "Supporting document file not found.")
		}
		return doc.filename to Files.readAllBytes(path)
	}

	internal fun saveBytes(originalName: String, bytes: ByteArray, accountEmail: String? = null): SupportingDocument {
		val target = accountEmail?.let(::accountWorkspace) ?: sharedWorkspace
		val saved = target.writer.saveBytes(originalName, bytes)
		if (accountEmail != null) {
			val selected = list(accountEmail).filter { it.selected }.map { it.id }.toSet() + saved.id
			writeSelection(target.directory.resolve(".selected-documents"), selected)
		}
		return saved
	}

	private fun resolve(document: SupportingDocument, accountEmail: String?): Path {
		if (accountEmail != null) {
			val account = accountWorkspace(accountEmail)
			account.reader.find(document.id)?.let { return account.reader.resolve(it) }
		}
		val sharedDocument = sharedWorkspace.reader.find(document.id)
			?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "Supporting document not found.")
		return sharedWorkspace.reader.resolve(sharedDocument)
	}

	private fun deleteSharedCopies(id: String) {
		val accountsDirectory = rootDirectory.resolve("accounts")
		if (!Files.isDirectory(accountsDirectory)) return
		Files.list(accountsDirectory).use { accounts ->
			accounts.filter(Files::isDirectory).forEach { accountDirectory ->
				val account = workspace(accountDirectory.resolve("supporting-documents"))
				if (account.reader.find(id) != null) account.writer.delete(id)
			}
		}
	}

	private fun writeSelection(target: Path, ids: Set<String>) {
		Files.createDirectories(target.parent)
		val temporary = Files.createTempFile(target.parent, ".selected-documents-", ".tmp")
		try {
			Files.writeString(temporary, ids.joinToString("\n"))
			try {
				Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
			} catch (_: java.nio.file.AtomicMoveNotSupportedException) {
				Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING)
			}
		} finally {
			Files.deleteIfExists(temporary)
		}
	}

	private data class SupportingDocumentWorkspace(
		val directory: Path,
		val reader: SupportingDocumentStoreReader,
		val writer: SupportingDocumentStoreWriter
	)
}
