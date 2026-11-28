package com.mosta.coveringletter.persistence

import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.nio.file.Path

class AccountDataDirectories(storageDirectory: Path) {
	private val root = storageDirectory.toAbsolutePath().normalize()
	private val accountsDirectory = root.resolve("accounts")
	private val emailPattern = Regex("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$")

	fun forEmail(email: String): Path {
		val normalizedEmail = email.trim().lowercase()
		if (!emailPattern.matches(normalizedEmail)) throw IllegalArgumentException("A valid account email is required.")
		val segment = URLEncoder.encode(normalizedEmail, StandardCharsets.UTF_8)
		return accountsDirectory.resolve(segment).normalize().also { path ->
			if (!path.startsWith(accountsDirectory)) throw IllegalArgumentException("Invalid account email.")
		}
	}
}