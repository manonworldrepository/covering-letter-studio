package com.mosta.coveringletter.persistence

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream

class LegacyDataAccountMigrationTests {
	@TempDir
	lateinit var temporaryDirectory: Path

	@Test
	fun `duplicates shared legacy entries while preserving existing archive data`() {
		val archive = temporaryDirectory.resolve("data-backup.zip")
		createArchive(
			archive,
			"resumes/legacy.pdf" to "shared resume",
			"supporting-documents/.selected-documents" to "shared selection",
			".env" to "GEMINI_MODEL=gemini-model",
			".account-access" to "ALLOWED_EMAILS=global@example.com",
			"accounts/other%40example.com/.env" to "GEMINI_API_KEY=other-key"
		)

		LegacyDataAccountMigration().duplicateLegacyData(archive, "Info@ManonWorld.de")

		val accountPrefix = "accounts/${AccountDataDirectories(temporaryDirectory).forEmail("info@manonworld.de").fileName}/"
		ZipFile(archive.toFile()).use { migrated ->
			assertEquals("shared resume", migrated.readText("resumes/legacy.pdf"))
			assertEquals("shared resume", migrated.readText("${accountPrefix}resumes/legacy.pdf"))
			assertEquals("shared selection", migrated.readText("${accountPrefix}supporting-documents/.selected-documents"))
			assertEquals("GEMINI_MODEL=gemini-model", migrated.readText("${accountPrefix}.env"))
			assertEquals("ALLOWED_EMAILS=global@example.com", migrated.readText(".account-access"))
			assertEquals("GEMINI_API_KEY=other-key", migrated.readText("accounts/other%40example.com/.env"))
		}
	}

	@Test
	fun `refuses to overwrite an existing account workspace without changing the archive`() {
		val archive = temporaryDirectory.resolve("data-backup.zip")
		createArchive(
			archive,
			"resumes/legacy.pdf" to "shared resume",
			"accounts/info%40example.com/.env" to "GEMINI_API_KEY=private-key"
		)
		val originalBytes = Files.readAllBytes(archive)

		assertThrows(IllegalStateException::class.java) {
			LegacyDataAccountMigration().duplicateLegacyData(archive, "info@example.com")
		}

		assertEquals(originalBytes.toList(), Files.readAllBytes(archive).toList())
	}

	@Test
	fun `rejects invalid owner emails`() {
		val archive = temporaryDirectory.resolve("data-backup.zip")
		createArchive(archive, "resumes/legacy.pdf" to "shared resume")

		assertThrows(IllegalArgumentException::class.java) {
			LegacyDataAccountMigration().duplicateLegacyData(archive, "../someone")
		}
	}

	private fun createArchive(archive: Path, vararg entries: Pair<String, String>) {
		ZipOutputStream(Files.newOutputStream(archive)).use { zip ->
			entries.forEach { (name, value) ->
				zip.putNextEntry(ZipEntry(name))
				zip.write(value.toByteArray())
				zip.closeEntry()
			}
		}
	}

	private fun ZipFile.readText(name: String): String = getInputStream(getEntry(name)).bufferedReader().use { it.readText() }
}