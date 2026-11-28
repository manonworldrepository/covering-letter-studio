package com.mosta.coveringletter.persistence

import com.google.cloud.storage.BlobInfo
import com.google.cloud.storage.Storage
import com.google.cloud.storage.StorageOptions
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component

@Component
class GoogleCloudStorageArchiveClient(
	@Value("\${coverletter.cloud-storage.bucket:}") private val bucket: String,
	@Value("\${coverletter.cloud-storage.enabled:false}") enabledSetting: Boolean
) : CloudStorageArchiveClient {
	private val storage: Storage? = if (!enabledSetting || bucket.isBlank()) {
		null
	} else {
		try {
			StorageOptions.getDefaultInstance().service
		} catch (_: Exception) {
			null
		}
	}

	override val enabled: Boolean
		get() = storage != null

	override fun downloadArchive(): CloudStorageArchive? {
		val service = storage ?: return null
		val blob = service.get(bucket, DATA_ARCHIVE) ?: return null
		val generation = blob.generation ?: return null
		val bytes = service.readAllBytes(
			bucket,
			DATA_ARCHIVE,
			Storage.BlobSourceOption.generationMatch(generation)
		)
		return CloudStorageArchive(bytes, generation)
	}

	override fun uploadArchive(bytes: ByteArray, expectedGeneration: Long?): Long {
		val service = storage ?: return 0L
		val archiveInfo = BlobInfo.newBuilder(bucket, DATA_ARCHIVE)
			.setContentType("application/zip")
			.build()
		val uploaded = if (expectedGeneration == null) {
			service.create(archiveInfo, bytes, Storage.BlobTargetOption.doesNotExist())
		} else {
			service.create(archiveInfo, bytes, Storage.BlobTargetOption.generationMatch(expectedGeneration))
		}
		return uploaded.generation
	}

	private companion object {
		const val DATA_ARCHIVE = "data-backup.zip"
	}
}