package com.mosta.coveringletter.pdf

import java.text.Normalizer
import java.time.LocalDate

object TailoredResumeFilename {
	fun create(
		applicantName: String,
		companyName: String,
		jobTitle: String,
		date: LocalDate = LocalDate.now()
	): String {
		val parts = listOf(applicantName, companyName, jobTitle)
			.map(::filenamePart)
			.filter(String::isNotBlank)
			.ifEmpty { listOf("Tailored", "Resume") }
		return "${parts.joinToString("_")}_${date}.pdf"
	}

	private fun filenamePart(value: String): String =
		Normalizer.normalize(value, Normalizer.Form.NFKD)
			.replace(Regex("\\p{M}+"), "")
			.split(Regex("[^A-Za-z0-9]+"))
			.filter(String::isNotBlank)
			.joinToString("_") { token ->
				token.lowercase().replaceFirstChar(Char::uppercaseChar)
			}
}