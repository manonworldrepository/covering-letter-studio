package com.mosta.coveringletter.generation

import com.mosta.coveringletter.settings.AppSettingsServiceDefaults
import org.springframework.web.reactive.function.client.WebClientResponseException
import org.springframework.web.server.ResponseStatusException

internal object GeminiModelFallback {
	const val MAX_ATTEMPTS = 3

	fun candidates(preferred: String, availableIds: List<String>): List<String> {
		val preferredModel = preferred.trim()
		val available = availableIds.map { it.trim() }.filter { it.isNotBlank() }.distinct()
		val ordered = LinkedHashSet<String>()

		if (preferredModel.isNotBlank()) {
			ordered.add(preferredModel)
		}
		ordered.add(AppSettingsServiceDefaults.DEFAULT_MODEL)

		available
			.filter { it.contains("flash", ignoreCase = true) }
			.sortedWith(
				compareBy<String> { !it.contains("lite", ignoreCase = true) }
					.thenBy { it.lowercase() }
			)
			.forEach { ordered.add(it) }

		available.forEach { ordered.add(it) }

		val preferredFirst = listOfNotNull(preferredModel.takeIf { it.isNotBlank() })
		val rest = if (available.isEmpty()) {
			ordered.filterNot { it == preferredModel }
		} else {
			ordered.filter { it in available && it != preferredModel }
		}
		return (preferredFirst + rest).distinct().take(MAX_ATTEMPTS)
	}

	fun isRetryable(error: Throwable): Boolean {
		val status = when (error) {
			is WebClientResponseException -> error.statusCode.value()
			is ResponseStatusException -> error.statusCode.value()
			else -> null
		}
		return when (status) {
			null -> true
			408, 429, 500, 502, 503, 504 -> true
			404 -> true
			else -> false
		}
	}
}
