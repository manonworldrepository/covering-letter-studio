package com.mosta.coveringletter.resume

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
import java.nio.file.Paths

@Service
class ResumeStore(
	extractor: DocumentTextExtractor,
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

	internal fun saveBytes(originalName: String, bytes: ByteArray): ResumeStatus =
		writer.saveBytes(originalName, bytes)
}
