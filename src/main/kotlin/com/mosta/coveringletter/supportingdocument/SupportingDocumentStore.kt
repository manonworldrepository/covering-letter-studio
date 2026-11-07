package com.mosta.coveringletter.supportingdocument

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
class SupportingDocumentStore(
	extractor: DocumentTextExtractor,
	@Value("\${coverletter.storage.directory:.data}") storageDirectory: String
) {
	private val directory = Paths.get(storageDirectory).toAbsolutePath().normalize().resolve("supporting-documents")
	private val selectionMetadata = directory.resolve(".selected-documents")
	private val reader = SupportingDocumentStoreReader(extractor, directory, selectionMetadata)
	private val writer = SupportingDocumentStoreWriter(reader, extractor, directory, selectionMetadata)

	fun list(): List<SupportingDocument> = reader.list()

	fun save(files: Flux<FilePart>): Mono<List<SupportingDocument>> =
		files.collectList().flatMap { selected ->
			if (selected.isEmpty()) {
				return@flatMap Mono.error(
					ResponseStatusException(HttpStatus.BAD_REQUEST, "Choose at least one supporting file.")
				)
			}
			if (list().size + selected.size > SupportingDocumentStoreLimits.MAX_FILES) {
				return@flatMap Mono.error(
					ResponseStatusException(
						HttpStatus.BAD_REQUEST,
						"You can store up to ${SupportingDocumentStoreLimits.MAX_FILES} supporting documents. Clear some before adding more."
					)
				)
			}
			Flux.fromIterable(selected)
				.concatMap(::save)
				.collectList()
				.flatMap { saved ->
					Mono.fromCallable {
						writer.addToSelection(saved.map { it.id }.toSet())
						list()
					}.subscribeOn(Schedulers.boundedElastic())
				}
		}

	fun save(file: FilePart): Mono<SupportingDocument> =
		DataBufferUtils.join(file.content(), SupportingDocumentStoreLimits.MAX_FILE_BYTES)
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
				ResponseStatusException(HttpStatus.PAYLOAD_TOO_LARGE, "Each supporting file must be 10 MB or smaller.", it)
			}
			.switchIfEmpty(Mono.error(ResponseStatusException(HttpStatus.BAD_REQUEST, "Choose a supporting file.")))

	fun extractAll(): Mono<String> = reader.extractAll()

	fun clear(): Mono<List<SupportingDocument>> =
		Mono.fromCallable { writer.clear() }.subscribeOn(Schedulers.boundedElastic())

	fun delete(id: String): Mono<SupportingDocument> =
		Mono.fromCallable { writer.delete(id) }.subscribeOn(Schedulers.boundedElastic())

	fun select(ids: Set<String>): List<SupportingDocument> = writer.select(ids)

	internal fun saveBytes(originalName: String, bytes: ByteArray): SupportingDocument =
		writer.saveBytes(originalName, bytes)
}
