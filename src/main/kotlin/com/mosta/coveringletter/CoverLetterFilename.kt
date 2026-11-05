package com.mosta.coveringletter

import java.text.Normalizer
import java.time.LocalDate

object CoverLetterFilename {
	fun create(personName: String, jobTitle: String, date: LocalDate = LocalDate.now()): String {
		val person = titleCase(personName)
		val job = titleCase(jobTitle)
		if (person.isBlank() || job.isBlank()) {
			throw IllegalArgumentException("Person name and job title are required to create a filename.")
		}
		return "${person}_${job}_${date}.pdf"
	}

	private fun titleCase(value: String): String =
		Normalizer.normalize(value, Normalizer.Form.NFKD)
			.replace(Regex("\\p{M}+"), "")
			.split(Regex("[^A-Za-z0-9]+"))
			.filter(String::isNotBlank)
			.joinToString("_") { token ->
				token.lowercase().replaceFirstChar(Char::uppercaseChar)
			}
}
