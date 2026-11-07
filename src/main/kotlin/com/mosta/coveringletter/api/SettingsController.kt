package com.mosta.coveringletter.api

import com.mosta.coveringletter.activity.UserActionLogBuffer
import com.mosta.coveringletter.api.request.AppSettingsRequest
import com.mosta.coveringletter.api.request.GeminiModelsRequest
import com.mosta.coveringletter.settings.AppSettingsService
import com.mosta.coveringletter.settings.AppSettingsStatus
import com.mosta.coveringletter.settings.CurrentGeminiSettings
import com.mosta.coveringletter.resume.ResumeStore
import jakarta.validation.Valid
import org.springframework.http.MediaType
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RestController
import org.springframework.http.server.reactive.ServerHttpResponse
import reactor.core.publisher.Mono

@RestController
class SettingsController(
	private val settings: AppSettingsService,
	private val resumeStore: ResumeStore,
	private val logs: UserActionLogBuffer
) {
	@GetMapping("/api/settings")
	fun getSettings() = settings.status(resumeStore.status())

	@GetMapping("/api/settings/current", produces = [MediaType.APPLICATION_JSON_VALUE])
	fun getCurrentSettings(response: ServerHttpResponse): CurrentGeminiSettings {
		response.headers.cacheControl = "no-store"
		return settings.currentSettings()
	}

	@PostMapping("/api/settings/models", consumes = [MediaType.APPLICATION_JSON_VALUE])
	fun getModels(@Valid @RequestBody request: GeminiModelsRequest) =
		settings.availableModels(request.apiKey)

	@PostMapping("/api/settings/api-key", consumes = [MediaType.APPLICATION_JSON_VALUE])
	fun saveApiKey(@Valid @RequestBody request: GeminiModelsRequest) =
		settings.saveApiKey(request.apiKey)
			.doOnSuccess { logs.record("Saved Gemini API key during setup") }

	@PostMapping("/api/settings", consumes = [MediaType.APPLICATION_JSON_VALUE])
	fun saveSettings(@Valid @RequestBody request: AppSettingsRequest): Mono<AppSettingsStatus> =
		settings.save(request)
			.doOnSuccess { logs.record("Updated Gemini API settings") }
			.map { settings.status(resumeStore.status()) }
}
