package com.mosta.coveringletter.api

import org.springframework.beans.factory.annotation.Value
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.core.context.SecurityContext
import org.springframework.security.oauth2.core.user.OAuth2User
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import reactor.core.publisher.Mono

@RestController
class UserController(
    @Value("\${spring.security.oauth2.client.registration.google.client-id:}")
    private val clientId: String,
) {
	@GetMapping("/api/user/me")
	fun getCurrentUser(securityContext: Mono<SecurityContext>): Mono<UserInfoResponse> =
		securityContext.flatMap { ctx ->
			val auth = ctx.authentication ?: throw org.springframework.security.access.AccessDeniedException("Not authenticated")
			val email = auth.name
			val name = if (auth.principal is OAuth2User) {
				val user = auth.principal as OAuth2User
				user.getAttribute("name") ?: user.getAttribute("given_name") ?: email
			} else {
				email
			}
			Mono.just(UserInfoResponse(email = email, name = name))
		}

	@GetMapping("/api/session/heartbeat")
	fun heartbeat(securityContext: Mono<SecurityContext>): Mono<String> =
		securityContext.map { ctx ->
			if (ctx.authentication != null) {
				"""{"status":"ok"}"""
			} else {
				throw org.springframework.security.access.AccessDeniedException("Session expired")
			}
		}

	@GetMapping("/login")
	fun loginRedirect(@RequestParam(required = false) prompt: String?, @RequestParam(required = false) refresh: String?): ResponseEntity<Void> {
		val redirectPrompt = prompt ?: "none"
		val redirectUri = java.net.URLEncoder.encode("https://coveringletter-ga7tig2oka-uc.a.run.app/login/oauth2/code/google", "UTF-8")
		val refreshParam = if (refresh == "true") "&refresh=true" else ""
		val authUrl = "https://accounts.google.com/o/oauth2/v2/auth?client_id=$clientId&redirect_uri=$redirectUri&response_type=code&scope=${java.net.URLEncoder.encode("email profile openid offline", "UTF-8")}&access_type=offline&prompt=$redirectPrompt$refreshParam"
		return ResponseEntity.status(HttpStatus.FOUND).location(java.net.URI.create(authUrl)).build()
	}
}
