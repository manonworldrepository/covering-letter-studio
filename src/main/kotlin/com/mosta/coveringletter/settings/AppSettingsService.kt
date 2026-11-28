package com.mosta.coveringletter.settings

import com.mosta.coveringletter.resume.ResumeStatus
import com.mosta.coveringletter.api.request.AppSettingsRequest
import com.mosta.coveringletter.persistence.AccountDataDirectories

import org.springframework.http.HttpStatus
import org.springframework.core.env.Environment
import org.springframework.stereotype.Service
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.reactive.function.client.WebClientResponseException
import org.springframework.web.server.ResponseStatusException
import reactor.core.publisher.Mono
import tools.jackson.databind.JsonNode
import tools.jackson.databind.ObjectMapper
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.util.concurrent.ConcurrentHashMap

@Service
class AppSettingsService(
	private val objectMapper: ObjectMapper,
	private val environment: Environment
) {
	private val webClient = WebClient.builder()
		.baseUrl("https://generativelanguage.googleapis.com")
		.build()
	internal var envFile = AppSettingsServiceDefaults.envFile
	private val runtimeApiKeyOverrides = ConcurrentHashMap<String, String>()

	fun status(resume: ResumeStatus, accountEmail: String? = null): AppSettingsStatus {
		val key = apiKey(accountEmail)
		val configuredModel = configuredModel(accountEmail)
		val model = configuredModel ?: AppSettingsServiceDefaults.DEFAULT_MODEL
		return AppSettingsStatus(
			configured = key.isNotBlank() && configuredModel != null,
			apiKeyConfigured = key.isNotBlank(),
			modelConfigured = configuredModel != null,
			model = model,
			resumeConfigured = resume.configured,
			resumeFilename = resume.filename
		)
	}

	fun apiKey(accountEmail: String? = null): String {
		runtimeApiKeyOverrides[accountKey(accountEmail)]?.let { return it }
		if (accountEmail != null) {
			return readEnv(envFileFor(accountEmail))["GEMINI_API_KEY"]?.takeIf(String::isNotBlank).orEmpty()
		}
		return configuredValue("GEMINI_API_KEY")
			?: environment.getProperty("gemini.api-key")?.takeIf(String::isNotBlank)
			?: ""
	}

	fun model(accountEmail: String? = null): String =
		configuredModel(accountEmail)
			?: AppSettingsServiceDefaults.DEFAULT_MODEL

	fun currentSettings(accountEmail: String? = null): CurrentGeminiSettings =
		CurrentGeminiSettings(apiKey(accountEmail).takeIf(String::isNotBlank), model(accountEmail))

	fun saveApiKey(requestedKey: String, accountEmail: String? = null): Mono<List<GeminiModelOption>> {
		val key = requestedKey.trim()
		if (key.isBlank() || key.contains('\n') || key.contains('\r')) {
			return Mono.error(ResponseStatusException(HttpStatus.BAD_REQUEST, "A valid Gemini API key is required."))
		}
		return availableModels(key, accountEmail)
			.map { models ->
				try {
					writeEnv(envFileFor(accountEmail), key)
					runtimeApiKeyOverrides[accountKey(accountEmail)] = key
					models
				} catch (exception: Exception) {
					throw ResponseStatusException(
						HttpStatus.INTERNAL_SERVER_ERROR,
						"Could not save the API key to .env. Check file permissions.",
						exception
					)
				}
			}
	}

	fun availableModels(requestedKey: String, accountEmail: String? = null): Mono<List<GeminiModelOption>> {
		val key = requestedKey.trim().ifBlank { apiKey(accountEmail) }
		if (key.isBlank()) {
			return Mono.error(
				ResponseStatusException(HttpStatus.BAD_REQUEST, "Enter a Gemini API key to load available models.")
			)
		}

		return webClient.get()
			.uri { builder -> builder.path("/v1beta/models").queryParam("key", key).build() }
			.retrieve()
			.bodyToMono(JsonNode::class.java)
			.map { response ->
				response.path("models")
					.filter { model ->
					model.path("supportedGenerationMethods").any { it.asString("") == "generateContent" }
					}
					.map { model ->
						GeminiModelOption(
						id = model.path("name").asString("").removePrefix("models/"),
						name = model.path("displayName").asString("").ifBlank {
							model.path("name").asString("").removePrefix("models/")
							}
						)
					}
					.filter { it.id.isNotBlank() }
					.sortedBy { it.name.lowercase() }
			}
			.flatMap { models ->
				if (models.isEmpty()) {
					Mono.error(ResponseStatusException(HttpStatus.BAD_GATEWAY, "Gemini returned no text-generation models."))
				} else {
					Mono.just(models)
				}
			}
			.onErrorMap { error ->
				when (error) {
					is ResponseStatusException -> error
					is WebClientResponseException -> ResponseStatusException(
						HttpStatus.BAD_REQUEST,
						geminiMessage(error),
						error
					)
					else -> ResponseStatusException(
						HttpStatus.BAD_GATEWAY,
						"Could not reach Gemini to list models. Check your connection and API key.",
						error
					)
				}
			}
	}

	fun save(request: AppSettingsRequest, accountEmail: String? = null): Mono<Unit> {
		val key = request.apiKey.trim().ifBlank { apiKey(accountEmail) }
		val model = request.model.trim()
		if (key.isBlank() || key.contains('\n') || key.contains('\r')) {
			return Mono.error(ResponseStatusException(HttpStatus.BAD_REQUEST, "A Gemini API key is required."))
		}
		if (!AppSettingsServiceDefaults.MODEL_ID.matches(model)) {
			return Mono.error(ResponseStatusException(HttpStatus.BAD_REQUEST, "Select a valid Gemini model."))
		}

		return availableModels(key, accountEmail)
			.flatMap { models ->
				if (models.none { it.id == model }) {
					return@flatMap Mono.error<Unit>(
						ResponseStatusException(
							HttpStatus.BAD_REQUEST,
							"That model is not available for this API key. Load the model list and select an available model."
						)
					)
				}
				try {
					writeEnv(envFileFor(accountEmail), key, model)
					runtimeApiKeyOverrides[accountKey(accountEmail)] = key
					Mono.just(Unit)
				} catch (exception: Exception) {
					Mono.error(
						ResponseStatusException(
							HttpStatus.INTERNAL_SERVER_ERROR,
							"Could not save settings to .env. Check file permissions.",
							exception
						)
					)
				}
			}
	}

	fun resetApplicationSettings(accountEmail: String? = null): String {
		val selectedModel = model(accountEmail)
		writeEnv(envFileFor(accountEmail), listOf("GEMINI_MODEL=$selectedModel"))
		runtimeApiKeyOverrides[accountKey(accountEmail)] = ""
		return selectedModel
	}

	private fun readEnv(file: Path): Map<String, String> {
		if (!Files.exists(file)) return emptyMap()
		return Files.readAllLines(file).mapNotNull { line ->
			val match = AppSettingsServiceDefaults.ENV_LINE.matchEntire(line.removePrefix("\uFEFF")) ?: return@mapNotNull null
			val value = match.groupValues[2].trim().removeSurrounding("\"").removeSurrounding("'")
			match.groupValues[1] to value
		}.toMap()
	}

	private fun configuredValue(name: String, accountEmail: String? = null): String? =
		(accountEmail?.let { readEnv(envFileFor(it))[name] }?.takeIf(String::isNotBlank)
			?: readEnv(envFile)[name]?.takeIf(String::isNotBlank))
			?: System.getenv(name)?.takeIf(String::isNotBlank)
			?: environment.getProperty(name)?.takeIf(String::isNotBlank)

	private fun envFileFor(accountEmail: String?): Path = accountEmail?.let {
		AccountDataDirectories(envFile.parent ?: Paths.get(".data")).forEmail(it).resolve(".env")
	} ?: envFile

	private fun configuredModel(accountEmail: String?): String? = if (accountEmail == null) {
		configuredValue("GEMINI_MODEL")
	} else {
		readEnv(envFileFor(accountEmail))["GEMINI_MODEL"]?.takeIf(String::isNotBlank)
	}

	private fun accountKey(accountEmail: String?): String = accountEmail?.trim()?.lowercase() ?: "shared"

	private fun writeEnv(target: Path, key: String, model: String? = null) {
		val lines = if (Files.exists(target)) {
			Files.readAllLines(target).toMutableList()
		} else mutableListOf()
		setEnvValue(lines, "GEMINI_API_KEY", key)
		model?.let { setEnvValue(lines, "GEMINI_MODEL", it) }
		writeEnv(target, lines)
	}

	private fun writeEnv(target: Path, lines: List<String>) {
		Files.createDirectories(target.parent)
		val temporary = Files.createTempFile(target.parent, ".env-", ".tmp")
		try {
			Files.write(temporary, lines)
			try {
					Files.move(
						temporary,
						target,
					java.nio.file.StandardCopyOption.REPLACE_EXISTING,
					java.nio.file.StandardCopyOption.ATOMIC_MOVE
				)
			} catch (_: java.nio.file.AtomicMoveNotSupportedException) {
				Files.move(
					temporary,
					target,
					java.nio.file.StandardCopyOption.REPLACE_EXISTING
				)
			}
		} finally {
			Files.deleteIfExists(temporary)
		}
	}

	private fun setEnvValue(lines: MutableList<String>, name: String, value: String) {
		val index = lines.indexOfFirst {
			AppSettingsServiceDefaults.ENV_LINE.matchEntire(it.removePrefix("\uFEFF"))?.groupValues?.get(1) == name
		}
		val line = "$name=$value"
		if (index >= 0) lines[index] = line else lines.add(line)
	}

	private fun geminiMessage(error: WebClientResponseException): String {
		val message = try {
			objectMapper.readTree(error.responseBodyAsString).path("error").path("message").asString("")
		} catch (_: Exception) {
			""
		}
		return if (message.isBlank()) {
			"Gemini rejected the API key (HTTP ${error.statusCode.value()}). Check the key and try again."
		} else {
			"Gemini rejected the API key (HTTP ${error.statusCode.value()}): $message"
		}
	}
}
