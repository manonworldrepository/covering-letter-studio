package com.mosta.coveringletter.api.controller

import com.mosta.coveringletter.api.request.AllowedEmailsSetupRequest
import com.mosta.coveringletter.api.response.AllowedEmailsResponse
import com.mosta.coveringletter.settings.AllowedEmailsService
import jakarta.validation.Valid
import org.springframework.http.MediaType
import org.springframework.http.server.reactive.ServerHttpResponse
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RestController
import java.security.Principal

@RestController
class AllowedEmailsController(
	private val allowedEmails: AllowedEmailsService
) {
	@GetMapping("/api/settings/allowed-emails", produces = [MediaType.APPLICATION_JSON_VALUE])
	fun getAllowedEmails(response: ServerHttpResponse, principal: Principal): AllowedEmailsResponse {
		allowedEmails.requireAdmin(principal.name)
		response.headers.cacheControl = "no-store"
		return AllowedEmailsResponse(allowedEmails.allowedEmails().sorted(), allowedEmails.adminEmail())
	}

	@PutMapping(
		"/api/settings/allowed-emails",
		consumes = [MediaType.APPLICATION_JSON_VALUE],
		produces = [MediaType.APPLICATION_JSON_VALUE]
	)
	fun updateAllowedEmails(
		@Valid @RequestBody request: AllowedEmailsSetupRequest,
		response: ServerHttpResponse,
		principal: Principal
	): AllowedEmailsResponse {
		if (!allowedEmails.isConfigured()) throw org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.NOT_FOUND)
		allowedEmails.requireAdmin(principal.name)
		response.headers.cacheControl = "no-store"
		val emails = allowedEmails.update(request.emails, request.adminEmail)
		return AllowedEmailsResponse(emails.sorted(), allowedEmails.adminEmail())
	}
}