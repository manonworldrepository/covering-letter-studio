package com.mosta.coveringletter.persistence

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream

class LegacyDataAccountMigration {
	fun duplicateLegacyData(archivePath: Path, ownerEmail: String) {
		val archive = archivePath.toAbsolutePath().normalize()
		if (!Files.isRegularFile(archive)) throw IllegalArgumentException("Data archive does not exist.")
		val accountPrefix = accountPrefix(archive, ownerEmail)
		val temporary = Files.createTempFile(archive.parent, ".data-backup-", ".zip")
		try {
			ZipFile(archive.toFile()).use { source ->
				val entries = source.entries().asSequence().toList()
				if (entries.any { it.name.startsWith(accountPrefix) }) {
					throw IllegalStateException("The archive already contains data for $ownerEmail.")
				}
				ZipOutputStream(Files.newOutputStream(temporary)).use { destination ->
					entries.forEach { entry -> copyEntry(source, entry, entry.name, destination) }
					entries.forEach { entry ->
						if (entry.isDirectory || entry.name.startsWith("accounts/") || entry.name == GLOBAL_ACCESS_FILE) return@forEach
						copyEntry(source, entry, "$accountPrefix${entry.name}", destination)
					}
				}
			}
			try {
				Files.move(temporary, archive, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
			} catch (_: java.nio.file.AtomicMoveNotSupportedException) {
				Files.move(temporary, archive, StandardCopyOption.REPLACE_EXISTING)
			}
		} finally {
			Files.deleteIfExists(temporary)
		}
	}

	private fun accountPrefix(archive: Path, ownerEmail: String): String {
		val accountDirectory = AccountDataDirectories(archive.parent).forEmail(ownerEmail)
		return "accounts/${accountDirectory.fileName}/"
	}

	private fun copyEntry(source: ZipFile, entry: ZipEntry, name: String, destination: ZipOutputStream) {
		val copiedEntry = ZipEntry(name).apply {
			time = entry.time
			comment = entry.comment
			extra = entry.extra
		}
		destination.putNextEntry(copiedEntry)
		if (!entry.isDirectory) source.getInputStream(entry).use { it.copyTo(destination) }
		destination.closeEntry()
	}

	private companion object {
		const val GLOBAL_ACCESS_FILE = ".account-access"
	}
}

object LegacyDataAccountMigrationCli {
	@JvmStatic
	fun main(args: Array<String>) {
		require(args.size == 2) { "Expected arguments: <data-backup.zip> <owner-email>." }
		LegacyDataAccountMigration().duplicateLegacyData(Path.of(args[0]), args[1])
		println("Duplicated shared legacy data into the requested account workspace.")
	}
}