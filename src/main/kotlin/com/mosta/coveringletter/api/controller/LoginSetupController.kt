package com.mosta.coveringletter.api.controller

import com.mosta.coveringletter.api.request.AllowedEmailsSetupRequest
import com.mosta.coveringletter.api.response.SetupStatusResponse
import com.mosta.coveringletter.settings.AllowedEmailsService
import jakarta.validation.Valid
import org.springframework.core.io.ClassPathResource
import org.springframework.core.io.Resource
import org.springframework.http.MediaType
import org.springframework.http.HttpStatus
import org.springframework.http.server.reactive.ServerHttpResponse
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.server.ResponseStatusException

@RestController
class LoginSetupController(
	private val allowedEmails: AllowedEmailsService
) {
	@GetMapping("/login", produces = [MediaType.TEXT_HTML_VALUE])
	fun loginPage(response: ServerHttpResponse): Resource {
		response.headers.cacheControl = "no-store"
		val page = if (allowedEmails.isConfigured()) "static/login.html" else "templates/allowed-emails-setup.html"
		return ClassPathResource(page)
	}

	@GetMapping("/api/setup/status", produces = [MediaType.APPLICATION_JSON_VALUE])
	fun setupStatus(response: ServerHttpResponse): SetupStatusResponse {
		requireSetupAvailable()
		response.headers.cacheControl = "no-store"
		return SetupStatusResponse(configured = false)
	}

	@PostMapping("/api/setup/allowed-emails", consumes = [MediaType.APPLICATION_JSON_VALUE])
	fun saveAllowedEmails(@Valid @RequestBody request: AllowedEmailsSetupRequest): SetupStatusResponse {
		requireSetupAvailable()
		allowedEmails.save(request.emails)
		return SetupStatusResponse(configured = true)
	}

	private fun requireSetupAvailable() {
		if (allowedEmails.isConfigured()) {
			throw ResponseStatusException(HttpStatus.NOT_FOUND)
		}
	}
}