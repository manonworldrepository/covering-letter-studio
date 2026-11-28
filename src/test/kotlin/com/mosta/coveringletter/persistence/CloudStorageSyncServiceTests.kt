package com.mosta.coveringletter.persistence

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.nio.file.Files
import java.nio.file.Path
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

class CloudStorageSyncServiceTests {
	@TempDir
	lateinit var temporaryDirectory: Path

	@Test
	fun `stale shutdown archive cannot overwrite a newer cloud generation`() {
		val archiveClient = FakeArchiveClient(CloudStorageArchive(archive("state.txt" to "startup"), 7))
		val service = CloudStorageSyncService(temporaryDirectory.resolve("data").toString(), archiveClient)
		service.downloadFromGcs()
		assertEquals("startup", Files.readString(temporaryDirectory.resolve("data").resolve("state.txt")))

		archiveClient.currentArchive = CloudStorageArchive(archive("state.txt" to "migrated"), 8)
		Files.writeString(temporaryDirectory.resolve("data").resolve("state.txt"), "stale local snapshot")

		assertThrows(IllegalStateException::class.java) { service.uploadToGcs() }
		assertEquals(8, archiveClient.currentArchive?.generation)
		assertEquals("migrated", readArchive(archiveClient.currentArchive!!.bytes, "state.txt"))
	}

	@Test
	fun `current shutdown archive advances the generation it loaded`() {
		val archiveClient = FakeArchiveClient(CloudStorageArchive(archive("state.txt" to "startup"), 7))
		val service = CloudStorageSyncService(temporaryDirectory.resolve("data").toString(), archiveClient)
		service.downloadFromGcs()
		Files.writeString(temporaryDirectory.resolve("data").resolve("state.txt"), "current local state")

		service.uploadToGcs()

		assertEquals(8, archiveClient.currentArchive?.generation)
		assertEquals("current local state", readArchive(archiveClient.currentArchive!!.bytes, "state.txt"))
	}

	private fun archive(vararg entries: Pair<String, String>): ByteArray = ByteArrayOutputStream().use { output ->
		ZipOutputStream(output).use { zip ->
			entries.forEach { (name, value) ->
				zip.putNextEntry(ZipEntry(name))
				zip.write(value.toByteArray())
				zip.closeEntry()
			}
		}
		output.toByteArray()
	}

	private fun readArchive(bytes: ByteArray, entryName: String): String =
		ZipInputStream(ByteArrayInputStream(bytes)).use { zip ->
			var entry = zip.nextEntry
			while (entry != null) {
				if (entry.name == entryName) return@use zip.readBytes().decodeToString()
				entry = zip.nextEntry
			}
			throw IllegalStateException("Archive entry $entryName was not found.")
		}

	private class FakeArchiveClient(var currentArchive: CloudStorageArchive?) : CloudStorageArchiveClient {
		override val enabled: Boolean = true

		override fun downloadArchive(): CloudStorageArchive? = currentArchive

		override fun uploadArchive(bytes: ByteArray, expectedGeneration: Long?): Long {
			if (currentArchive?.generation != expectedGeneration) {
				throw IllegalStateException("Archive generation changed after startup.")
			}
			val generation = (currentArchive?.generation ?: 0) + 1
			currentArchive = CloudStorageArchive(bytes, generation)
			return generation
		}
	}
}