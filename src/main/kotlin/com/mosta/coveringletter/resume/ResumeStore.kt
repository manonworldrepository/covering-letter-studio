package com.mosta.coveringletter.resume

import com.mosta.coveringletter.api.DocumentViewResponse
import com.mosta.coveringletter.documents.DocumentTextExtractor
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

@Service
class ResumeStore(
	private val extractor: DocumentTextExtractor,
	@Value("\${coverletter.storage.directory:.data}") storageDirectory: String
) {
	private val directory = Paths.get(storageDirectory).toAbsolutePath().normalize()
	private val resumesDirectory = directory.resolve("resumes")
	private val filenameMetadata = directory.resolve(".resume-filename")
	private val selectionMetadata = directory.resolve(".selected-resume")
	private val reader = ResumeStoreReader(
		extractor,
		directory,
		resumesDirectory,
		filenameMetadata,
		selectionMetadata
	)
	private val writer = ResumeStoreWriter(
		reader,
		extractor,
		directory,
		resumesDirectory,
		filenameMetadata,
		selectionMetadata
	)

	fun status(): ResumeStatus = reader.status()

	fun save(files: Flux<FilePart>): Mono<ResumeStatus> =
		files.collectList().flatMap { selected ->
			if (selected.isEmpty()) {
				return@flatMap Mono.error(
					ResponseStatusException(HttpStatus.BAD_REQUEST, "Choose at least one resume file.")
				)
			}
			Flux.fromIterable(selected)
				.concatMap(::saveFile)
				.then(Mono.fromCallable { status() })
		}

	private fun saveFile(file: FilePart): Mono<ResumeStatus> =
		DataBufferUtils.join(file.content(), ResumeStoreLimits.MAX_FILE_BYTES)
			.flatMap { buffer ->
				val bytes = try {
					ByteArray(buffer.readableByteCount()).also(buffer::read)
				} finally {
					DataBufferUtils.release(buffer)
				}
				Mono.fromCallable { writer.saveBytes(file.filename(), bytes) }
					.subscribeOn(Schedulers.boundedElastic())
			}
			.onErrorMap(DataBufferLimitException::class.java) {
				ResponseStatusException(HttpStatus.PAYLOAD_TOO_LARGE, "Resume must be 10 MB or smaller.", it)
			}
			.switchIfEmpty(Mono.error(ResponseStatusException(HttpStatus.BAD_REQUEST, "Choose a resume file.")))

	fun select(id: String): ResumeStatus = writer.select(id)

	fun delete(id: String): ResumeOption = writer.delete(id)

	fun clear(): List<ResumeOption> = writer.clear()

	fun extractStored(): Mono<String> =
		Mono.fromCallable { reader.extractSelected() }.subscribeOn(Schedulers.boundedElastic())

	fun getDocumentView(id: String): DocumentViewResponse {
		val resume = reader.find(id)
			?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "Resume not found.")
		val path = reader.resolve(resume)
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

	fun getDocumentBytes(id: String): Pair<String, ByteArray> {
		val resume = reader.find(id)
			?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "Resume not found.")
		val path = reader.resolve(resume)
		if (!Files.isRegularFile(path)) {
			throw ResponseStatusException(HttpStatus.NOT_FOUND, "Resume file not found.")
		}
		return resume.filename to Files.readAllBytes(path)
	}

	internal fun saveBytes(originalName: String, bytes: ByteArray): ResumeStatus =
		writer.saveBytes(originalName, bytes)
}
