package com.mosta.coveringletter.settings

import org.springframework.beans.factory.annotation.Value
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.web.server.ResponseStatusException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths

@Service
class AllowedEmailsService(
	@Value("\${coverletter.security.allowed-emails:}") private val configuredEmailsProperty: String
) {
	internal var envFile: Path = Paths.get(".env").toAbsolutePath().normalize()
	private val setupLock = Any()
	@Volatile private var runtimeEmailsOverride: Set<String>? = null

	fun allowedEmails(): Set<String> {
		runtimeEmailsOverride?.let { return it }
		val configured = parseEmails(configuredEmailsProperty)
			.ifEmpty { parseEmails(readEnv()["ALLOWED_EMAILS"].orEmpty()) }
		return configured
	}

	private fun parseEmails(value: String): Set<String> = value.split(',')
			.map { it.trim().lowercase() }
			.filter { it.isNotBlank() && it != PLACEHOLDER_EMAIL }
			.toSet()

	fun isConfigured(): Boolean = allowedEmails().isNotEmpty()

	fun allows(email: String): Boolean = email.trim().lowercase() in allowedEmails()

	fun save(requestedEmails: String) {
		synchronized(setupLock) {
			if (isConfigured()) {
				throw ResponseStatusException(HttpStatus.NOT_FOUND)
			}

			persist(requestedEmails)
		}
	}

	fun update(requestedEmails: String, allowEmpty: Boolean = false): Set<String> {
		synchronized(setupLock) {
			ensureConfiguredForUpdate()

			return persist(requestedEmails, allowEmpty)
		}
	}

	fun validateUpdate(requestedEmails: String, allowEmpty: Boolean = false): Set<String> = synchronized(setupLock) {
		ensureConfiguredForUpdate()
		normalizeEmails(requestedEmails, allowEmpty)
	}

	fun reset(preservedModel: String) {
		synchronized(setupLock) {
			val currentValues = readEnv()
			val lines = listOf("GOOGLE_CLIENT_ID", "GOOGLE_CLIENT_SECRET")
				.mapNotNull { name -> currentValues[name]?.let { "$name=$it" } }
				.toMutableList()
			lines.add("GEMINI_MODEL=$preservedModel")
			writeEnv(lines)
			runtimeEmailsOverride = emptySet()
		}
	}

	private fun persist(requestedEmails: String, allowEmpty: Boolean = false): Set<String> {
		val normalizedEmails = normalizeEmails(requestedEmails, allowEmpty)
		writeEnv(normalizedEmails.joinToString(","))
		runtimeEmailsOverride = normalizedEmails.takeIf { configuredEmailsProperty.isNotBlank() }
		return normalizedEmails
	}

	private fun normalizeEmails(requestedEmails: String, allowEmpty: Boolean = false): Set<String> {
		if (allowEmpty && requestedEmails.isBlank()) return emptySet()

		val emails = requestedEmails.split(',').map { it.trim().lowercase() }
		if (requestedEmails.length > MAX_EMAILS_LENGTH || emails.isEmpty() || emails.size > MAX_EMAIL_COUNT ||
			emails.any { !EMAIL_PATTERN.matches(it) }
		) {
			throw ResponseStatusException(
				HttpStatus.BAD_REQUEST,
				"Enter one or more valid email addresses, separated by commas."
			)
		}

		return emails.distinct().toSet()
	}

	private fun ensureConfiguredForUpdate() {
		if (!isConfigured()) {
			throw ResponseStatusException(HttpStatus.NOT_FOUND)
		}
	}

	private fun readEnv(): Map<String, String> {
		if (!Files.exists(envFile)) return emptyMap()
		return Files.readAllLines(envFile).mapNotNull { line ->
			val match = ENV_LINE.matchEntire(line.removePrefix("\uFEFF")) ?: return@mapNotNull null
			val value = match.groupValues[2].trim().removeSurrounding("\"").removeSurrounding("'")
			match.groupValues[1] to value
		}.toMap()
	}

	private fun writeEnv(value: String) {
		val lines = if (Files.exists(envFile)) Files.readAllLines(envFile).toMutableList() else mutableListOf()
		val index = lines.indexOfFirst {
			ENV_LINE.matchEntire(it.removePrefix("\uFEFF"))?.groupValues?.get(1) == "ALLOWED_EMAILS"
		}
		val line = "ALLOWED_EMAILS=$value"
		if (index >= 0) lines[index] = line else lines.add(line)
		writeEnv(lines)
	}

	private fun writeEnv(lines: List<String>) {
		Files.createDirectories(envFile.parent)
		val temporary = Files.createTempFile(envFile.parent, ".env-", ".tmp")
		try {
			Files.write(temporary, lines)
			try {
				Files.move(
					temporary,
					envFile,
					java.nio.file.StandardCopyOption.REPLACE_EXISTING,
					java.nio.file.StandardCopyOption.ATOMIC_MOVE
				)
			} catch (_: java.nio.file.AtomicMoveNotSupportedException) {
				Files.move(temporary, envFile, java.nio.file.StandardCopyOption.REPLACE_EXISTING)
			}
		} finally {
			Files.deleteIfExists(temporary)
		}
	}

	private companion object {
		const val PLACEHOLDER_EMAIL = "your.email@gmail.com"
		const val MAX_EMAIL_COUNT = 50
		const val MAX_EMAILS_LENGTH = 4096
		val ENV_LINE = Regex("^\\s*([A-Za-z_][A-Za-z0-9_]*)\\s*=\\s*(.*)\\s*$")
		val EMAIL_PATTERN = Regex(
			"^[A-Za-z0-9.!#$%&'*+/=?^_`{|}~-]+@[A-Za-z0-9](?:[A-Za-z0-9-]{0,61}[A-Za-z0-9])?(?:\\.[A-Za-z0-9](?:[A-Za-z0-9-]{0,61}[A-Za-z0-9])?)+$"
		)
	}
}