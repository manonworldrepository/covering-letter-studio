package com.mosta.coveringletter.security

import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.Authentication
import org.springframework.stereotype.Component
import org.springframework.web.server.ServerWebExchange
import reactor.core.publisher.Mono

@Component
class JwtAuthenticationConverter(
    private val jwtService: JwtService
) {

    companion object {
        const val ACCESS_TOKEN_COOKIE = "access_token"
        const val REFRESH_TOKEN_COOKIE = "refresh_token"
    }

    fun convert(exchange: ServerWebExchange): Mono<Authentication> {
        // Try Authorization header first, then fall back to cookie
        val authHeader = exchange.request.headers.getFirst("Authorization")
        val tokenFromHeader = if (authHeader != null && authHeader.startsWith("Bearer ")) {
            authHeader.substring(7)
        } else {
            null
        }
        val token = tokenFromHeader ?: exchange.request.cookies[ACCESS_TOKEN_COOKIE]?.firstOrNull()?.value
            ?: return Mono.empty()

        return jwtService.validateAccessToken(token)
            .map { email ->
                UsernamePasswordAuthenticationToken(email, null)
            }
    }
}
