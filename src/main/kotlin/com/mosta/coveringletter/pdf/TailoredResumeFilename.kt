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
		val parts = listOf(personFilenamePart(applicantName), filenamePart(companyName), filenamePart(jobTitle))
			.filter(String::isNotBlank)
			.ifEmpty { listOf("Tailored", "Resume") }
		return "${parts.joinToString("_")}_${date}.pdf"
	}

	private fun personFilenamePart(value: String): String {
		val parts = filenamePart(value).split("_").filter(String::isNotBlank)
		return if (parts.size > 2) "${parts.first()}_${parts.last()}" else parts.joinToString("_")
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