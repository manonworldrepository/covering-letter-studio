package com.mosta.coveringletter.api

import org.springframework.security.core.context.SecurityContext
import org.springframework.security.oauth2.core.user.OAuth2User
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RestController
import reactor.core.publisher.Mono

@RestController
class UserController {
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
	fun heartbeat(): Mono<String> =
		Mono.just("""{"status":"ok"}""")
}
