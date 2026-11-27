package com.mosta.coveringletter.pdf

import java.text.Normalizer
import java.time.LocalDate

object CoverLetterFilename {
	fun create(
		personName: String,
		companyName: String,
		jobTitle: String,
		date: LocalDate = LocalDate.now()
	): String {
		val person = personFilenamePart(personName)
		val company = filenamePart(companyName)
		val job = filenamePart(jobTitle)
		if (person.isBlank() || job.isBlank()) {
			throw IllegalArgumentException("Person name and job title are required to create a filename.")
		}
		val parts = listOf(person, company, job).filter(String::isNotBlank)
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
