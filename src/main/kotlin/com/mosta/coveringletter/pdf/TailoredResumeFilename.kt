package com.mosta.coveringletter.pdf

import java.text.Normalizer

object TailoredResumeFilename {
	fun create(
		applicantName: String,
		companyName: String,
		jobTitle: String
	): String {
		val parts = listOf(personFilenamePart(applicantName), filenamePart(companyName), jobTitleFilenamePart(jobTitle))
			.filter(String::isNotBlank)
			.ifEmpty { listOf("Tailored") }
		return "${parts.joinToString("_")}_Resume.pdf"
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