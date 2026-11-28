package com.mosta.coveringletter.api.controller

import com.mosta.coveringletter.api.request.AppSettingsRequest
import com.mosta.coveringletter.api.request.GeminiModelsRequest
import com.mosta.coveringletter.settings.AllowedEmailsService
import com.mosta.coveringletter.settings.AppSettingsService
import com.mosta.coveringletter.settings.AppSettingsStatus
import com.mosta.coveringletter.settings.CurrentGeminiSettings
import com.mosta.coveringletter.resume.ResumeStore
import jakarta.validation.Valid
import org.springframework.http.MediaType
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RestController
import org.springframework.http.server.reactive.ServerHttpResponse
import reactor.core.publisher.Mono
import java.security.Principal

@RestController
class SettingsController(
	private val settings: AppSettingsService,
	private val resumeStore: ResumeStore,
	private val allowedEmails: AllowedEmailsService
) {
	@GetMapping("/api/settings")
	fun getSettings(principal: Principal) = settings.status(resumeStore.status(principal.name), principal.name)

	@GetMapping("/api/settings/current", produces = [MediaType.APPLICATION_JSON_VALUE])
	fun getCurrentSettings(response: ServerHttpResponse, principal: Principal): CurrentGeminiSettings {
		response.headers.cacheControl = "no-store"
		return settings.currentSettings(principal.name)
	}

	@PostMapping("/api/settings/models", consumes = [MediaType.APPLICATION_JSON_VALUE])
	fun getModels(@Valid @RequestBody request: GeminiModelsRequest, principal: Principal) =
		settings.availableModels(request.apiKey, principal.name)

	@PostMapping("/api/settings/api-key", consumes = [MediaType.APPLICATION_JSON_VALUE])
	fun saveApiKey(@Valid @RequestBody request: GeminiModelsRequest, principal: Principal) =
		settings.saveApiKey(request.apiKey, principal.name)

	@PostMapping("/api/settings", consumes = [MediaType.APPLICATION_JSON_VALUE])
	fun saveSettings(@Valid @RequestBody request: AppSettingsRequest, principal: Principal): Mono<AppSettingsStatus> {
		request.allowedEmails?.let {
			allowedEmails.requireAdmin(principal.name)
			allowedEmails.validateUpdate(it, request.adminEmail)
		}
		if (request.allowedEmails == null && request.adminEmail != null) {
			return Mono.error(org.springframework.web.server.ResponseStatusException(
				org.springframework.http.HttpStatus.BAD_REQUEST,
				"Allowed emails and the admin assignment must be saved together."
			))
		}
		return settings.save(request, principal.name)
			.doOnSuccess { request.allowedEmails?.let { allowedEmails.update(it, request.adminEmail) } }
			.map { settings.status(resumeStore.status(principal.name), principal.name) }
	}

	@DeleteMapping("/api/settings")
	fun resetSettings(principal: Principal): Map<String, String> {
		allowedEmails.requireAdmin(principal.name)
		val model = settings.resetApplicationSettings(principal.name)
		allowedEmails.reset(model)
		return mapOf("status" to "reset")
	}
}
