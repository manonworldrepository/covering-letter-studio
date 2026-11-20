package com.mosta.coveringletter.persistence

import com.google.cloud.storage.Storage
import com.google.cloud.storage.StorageOptions
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.context.event.ApplicationReadyEvent
import org.springframework.context.event.ContextClosedEvent
import org.springframework.context.event.EventListener
import org.springframework.stereotype.Component
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/**
 * Syncs the `.data` directory to and from Google Cloud Storage so that data persists
 * across Cloud Run container restarts (scale-to-zero).
 *
 * On startup: downloads the data archive from Cloud Storage and extracts it to `.data`.
 * On shutdown: zips the `.data` directory and uploads it to Cloud Storage.
 *
 * The sync is optional — if the GCS bucket is not configured, the app falls back to
 * local-only storage (same behavior as before).
 */
@Component
class CloudStorageSyncService(
	@Value("\${coverletter.storage.directory:.data}") private val storageDirectory: String,
	@Value("\${coverletter.cloud-storage.bucket:}") private val gcsBucket: String,
	@Value("\${coverletter.cloud-storage.enabled:false}") private val enabled: Boolean
) {

	private val dataDir = File(storageDirectory).absoluteFile

	private val storage: Storage? = run {
		if (!enabled || gcsBucket.isBlank()) {
			null
		} else {
			try {
				StorageOptions.getDefaultInstance().service
			} catch (e: Exception) {
				null
			}
		}
	}

	@EventListener
	fun onReady(event: ApplicationReadyEvent) {
		if (storage == null) return
		try {
			downloadFromGcs()
		} catch (e: Exception) {
			println("CloudStorageSyncService: download failed or no data found: ${e.message}")
		}
	}

	@EventListener
	fun onShutdown(event: ContextClosedEvent) {
		if (storage == null) return
		try {
			uploadToGcs()
		} catch (e: Exception) {
			println("CloudStorageSyncService: upload failed: ${e.message}")
		}
	}

	private fun downloadFromGcs() {
		val blob = storage?.get(gcsBucket, DATA_ARCHIVE) ?: return
		val bytes = ByteArrayOutputStream().use { baos ->
			blob.downloadTo(baos)
			baos.toByteArray()
		}
		if (bytes.isEmpty()) return

		dataDir.mkdirs()
		ZipInputStream(bytes.inputStream()).use { zis ->
			var entry = zis.nextEntry
			while (entry != null) {
				val file = File(dataDir, entry.name)
				if (entry.isDirectory) {
					file.mkdirs()
				} else {
					file.parentFile?.mkdirs()
					zis.copyTo(file.outputStream())
				}
				zis.closeEntry()
				entry = zis.nextEntry
			}
		}
	}

	private fun uploadToGcs() {
		if (!dataDir.exists()) return
		val s = storage ?: return

		val bytes = ByteArrayOutputStream().use { baos ->
			ZipOutputStream(baos).use { zos ->
				val files = dataDir.listFiles() ?: return@use
				files.forEach { file ->
					addFileToZip(zos, file, file.name)
				}
			}
			baos.toByteArray()
		}

		val blobInfo = com.google.cloud.storage.BlobInfo.newBuilder(gcsBucket, DATA_ARCHIVE)
			.setContentType("application/zip")
			.build()

		s.create(blobInfo, bytes)
	}

	private fun addFileToZip(zos: ZipOutputStream, file: File, path: String) {
		if (file.isDirectory) {
			val files = file.listFiles() ?: return
			files.forEach { child ->
				addFileToZip(zos, child, "$path/${child.name}")
			}
		} else {
			zos.putNextEntry(ZipEntry(path))
			file.inputStream().use { it.copyTo(zos) }
			zos.closeEntry()
		}
	}

	companion object {
		private const val DATA_ARCHIVE = "data-backup.zip"
	}
}
