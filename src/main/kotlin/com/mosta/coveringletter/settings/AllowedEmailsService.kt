package com.mosta.coveringletter.settings

import org.springframework.beans.factory.annotation.Value
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.web.server.ResponseStatusException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.nio.file.StandardCopyOption

@Service
class AllowedEmailsService(
	@Value("\${coverletter.security.allowed-emails:}") private val configuredEmailsProperty: String,
	@Value("\${coverletter.security.admin-email:}") private val configuredAdminEmailProperty: String = ""
) {
	internal var envFile: Path = Paths.get(".env").toAbsolutePath().normalize()
	private val defaultEnvFile = Paths.get(".env").toAbsolutePath().normalize()
	private val setupLock = Any()

	fun allowedEmails(): Set<String> = config().emails

	fun adminEmail(): String? = config().adminEmail

	fun isAdmin(email: String): Boolean = email.trim().lowercase() == adminEmail()

	fun requireAdmin(email: String) {
		if (!isAdmin(email)) throw ResponseStatusException(HttpStatus.FORBIDDEN, "Administrator access is required.")
	}

	private fun config(): AllowedAccountConfig {
		val stored = readAccessConfig()
		if (stored != null) return stored
		val env = readEnv()
		val emails = parseEmails(configuredEmailsProperty)
			.ifEmpty { parseEmails(env["ALLOWED_EMAILS"].orEmpty()) }
		val admin = env["ADMIN_EMAIL"]
			?: configuredAdminEmailProperty.takeIf(String::isNotBlank)
			?: System.getenv("ADMIN_EMAIL")
		return AllowedAccountConfig(emails, normalizeAdmin(admin, emails))
	}

	private fun parseEmails(value: String): Set<String> = value.split(',')
			.map { it.trim().lowercase() }
			.filter { it.isNotBlank() && it != PLACEHOLDER_EMAIL }
			.toSet()

	fun isConfigured(): Boolean = allowedEmails().isNotEmpty()

	fun allows(email: String): Boolean = email.trim().lowercase() in allowedEmails()

	fun save(requestedEmails: String, requestedAdminEmail: String? = null) {
		synchronized(setupLock) {
			if (isConfigured()) {
				throw ResponseStatusException(HttpStatus.NOT_FOUND)
			}

			persist(requestedEmails, requestedAdminEmail)
		}
	}

	fun update(
		requestedEmails: String,
		requestedAdminEmail: String? = null
	): Set<String> {
		synchronized(setupLock) {
			ensureConfiguredForUpdate()

			return persist(requestedEmails, requestedAdminEmail).emails
		}
	}

	fun validateUpdate(
		requestedEmails: String,
		requestedAdminEmail: String? = null
	): Set<String> = synchronized(setupLock) {
		ensureConfiguredForUpdate()
		normalizeConfig(requestedEmails, requestedAdminEmail).emails
	}

	fun reset(preservedModel: String) {
		synchronized(setupLock) {
			val currentValues = readEnv()
			val lines = listOf("GOOGLE_CLIENT_ID", "GOOGLE_CLIENT_SECRET")
				.mapNotNull { name -> currentValues[name]?.let { "$name=$it" } }
				.toMutableList()
			lines.add("GEMINI_MODEL=$preservedModel")
			writeEnv(lines)
			val emptyConfig = AllowedAccountConfig(emptySet(), null)
			writeAccessConfig(emptyConfig)
		}
	}

	private fun persist(
		requestedEmails: String,
		requestedAdminEmail: String?
	): AllowedAccountConfig {
		val config = normalizeConfig(requestedEmails, requestedAdminEmail)
		writeEnv(config.emails.joinToString(","), config.adminEmail)
		writeAccessConfig(config)
		return config
	}

	private fun normalizeConfig(
		requestedEmails: String,
		requestedAdminEmail: String?
	): AllowedAccountConfig {
		val emails = requestedEmails.split(',').map { it.trim().lowercase() }
		if (requestedEmails.length > MAX_EMAILS_LENGTH || emails.isEmpty() || emails.size > MAX_EMAIL_COUNT ||
			emails.any { !EMAIL_PATTERN.matches(it) }
		) {
			throw ResponseStatusException(
				HttpStatus.BAD_REQUEST,
				"Enter one or more valid email addresses, separated by commas."
			)
		}

		val distinctEmails = emails.distinct().toSet()
		val admin = normalizeAdmin(requestedAdminEmail, distinctEmails)
		if (admin == null || admin !in distinctEmails) {
			throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Select one allowed email address as the admin.")
		}
		return AllowedAccountConfig(distinctEmails, admin)
	}

	private fun normalizeAdmin(requestedAdminEmail: String?, emails: Set<String>): String? {
		val requested = requestedAdminEmail?.trim()?.lowercase()?.takeIf(String::isNotBlank)
		return requested ?: emails.firstOrNull()
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

	private fun accessConfigFile(): Path = if (envFile == defaultEnvFile) {
		Paths.get(".data", ".account-access").toAbsolutePath().normalize()
	} else {
		(envFile.parent ?: Paths.get(".data")).resolve(".account-access")
	}

	private fun readAccessConfig(): AllowedAccountConfig? {
		val file = accessConfigFile()
		if (!Files.isRegularFile(file)) return null
		val values = Files.readAllLines(file).mapNotNull { line ->
			val match = ENV_LINE.matchEntire(line) ?: return@mapNotNull null
			match.groupValues[1] to match.groupValues[2].trim()
		}.toMap()
		val emails = parseEmails(values["ALLOWED_EMAILS"].orEmpty())
		val admin = values["ADMIN_EMAIL"]?.trim()?.lowercase()?.takeIf { it in emails }
		return AllowedAccountConfig(emails, admin ?: emails.firstOrNull())
	}

	private fun writeEnv(value: String, adminEmail: String?) {
		val lines = if (Files.exists(envFile)) Files.readAllLines(envFile).toMutableList() else mutableListOf()
		setEnvValue(lines, "ALLOWED_EMAILS", value)
		if (adminEmail == null) removeEnvValue(lines, "ADMIN_EMAIL") else setEnvValue(lines, "ADMIN_EMAIL", adminEmail)
		writeEnv(lines)
	}

	private fun writeAccessConfig(config: AllowedAccountConfig) {
		val file = accessConfigFile()
		Files.createDirectories(file.parent)
		val lines = listOf("ALLOWED_EMAILS=${config.emails.joinToString(",")}") +
			listOfNotNull(config.adminEmail?.let { "ADMIN_EMAIL=$it" })
		val temporary = Files.createTempFile(file.parent, ".account-access-", ".tmp")
		try {
			Files.write(temporary, lines)
			try {
				Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
			} catch (_: java.nio.file.AtomicMoveNotSupportedException) {
				Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING)
			}
		} finally {
			Files.deleteIfExists(temporary)
		}
	}

	private fun setEnvValue(lines: MutableList<String>, name: String, value: String) {
		val index = lines.indexOfFirst { ENV_LINE.matchEntire(it.removePrefix("\uFEFF"))?.groupValues?.get(1) == name }
		val line = "$name=$value"
		if (index >= 0) lines[index] = line else lines.add(line)
	}

	private fun removeEnvValue(lines: MutableList<String>, name: String) {
		lines.removeAll { ENV_LINE.matchEntire(it.removePrefix("\uFEFF"))?.groupValues?.get(1) == name }
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

private data class AllowedAccountConfig(val emails: Set<String>, val adminEmail: String?)