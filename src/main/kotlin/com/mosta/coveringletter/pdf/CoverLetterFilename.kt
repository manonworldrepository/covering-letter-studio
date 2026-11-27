package com.mosta.coveringletter.pdf

import java.text.Normalizer

object CoverLetterFilename {
	fun create(
		personName: String,
		companyName: String,
		jobTitle: String
	): String {
		val person = personFilenamePart(personName)
		val company = filenamePart(companyName)
		val job = jobTitleFilenamePart(jobTitle)
		if (person.isBlank() || job.isBlank()) {
			throw IllegalArgumentException("Person name and job title are required to create a filename.")
		}
		val parts = listOf(person, company, job).filter(String::isNotBlank)
		return "${parts.joinToString("_")}_Cover_Letter.pdf"
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

	private fun jobTitleFilenamePart(value: String): String {
		val normalized = filenamePart(value)
		return if (value.length > MAX_JOB_TITLE_LENGTH) normalized.split("_").take(2).joinToString("_") else normalized
	}

	private const val MAX_JOB_TITLE_LENGTH = 25
}
