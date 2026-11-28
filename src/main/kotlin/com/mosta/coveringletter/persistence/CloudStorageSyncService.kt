package com.mosta.coveringletter.persistence

import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.context.event.ApplicationReadyEvent
import org.springframework.context.event.ContextClosedEvent
import org.springframework.context.event.EventListener
import org.springframework.stereotype.Component
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
	private val archiveClient: CloudStorageArchiveClient
) {

	private val dataDir = File(storageDirectory).absoluteFile
	private var generationAtStartup: Long? = null

	@EventListener
	fun onReady(event: ApplicationReadyEvent) {
		if (!archiveClient.enabled) return
		try {
			downloadFromGcs()
		} catch (e: Exception) {
			println("CloudStorageSyncService: download failed or no data found: ${e.message}")
		}
	}

	@EventListener
	fun onShutdown(event: ContextClosedEvent) {
		if (!archiveClient.enabled) return
		try {
			uploadToGcs()
		} catch (e: Exception) {
			println("CloudStorageSyncService: upload failed: ${e.message}")
		}
	}

	internal fun downloadFromGcs() {
		val archive = archiveClient.downloadArchive() ?: return
		generationAtStartup = archive.generation
		val bytes = archive.bytes
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
					file.outputStream().use { output -> zis.copyTo(output) }
				}
				zis.closeEntry()
				entry = zis.nextEntry
			}
		}
	}

	internal fun uploadToGcs() {
		if (!dataDir.exists()) return

		val bytes = java.io.ByteArrayOutputStream().use { baos ->
			ZipOutputStream(baos).use { zos ->
				val files = dataDir.listFiles() ?: return@use
				files.forEach { file ->
					addFileToZip(zos, file, file.name)
				}
			}
			baos.toByteArray()
		}

		generationAtStartup = archiveClient.uploadArchive(bytes, generationAtStartup)
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
}
