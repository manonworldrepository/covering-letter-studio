package com.mosta.coveringletter.security

import org.slf4j.LoggerFactory
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
        private val log = LoggerFactory.getLogger(JwtAuthenticationConverter::class.java)
    }

    fun convert(exchange: ServerWebExchange): Mono<Authentication> {
        // Try Authorization header first, then fall back to cookie
        val authHeader = exchange.request.headers.getFirst("Authorization")
        val tokenFromHeader = if (authHeader != null && authHeader.startsWith("Bearer ")) {
            authHeader.substring(7)
        } else {
            null
        }
        val path = exchange.request.path.value()
        val token = tokenFromHeader ?: exchange.request.cookies[ACCESS_TOKEN_COOKIE]?.firstOrNull()?.value
            ?: return Mono.empty()
        val source = if (tokenFromHeader != null) "header" else "cookie"
        log.warn("JwtAuthenticationConverter: path={}, token_source={}, token_length={}, token_first_50={}", path, source, token.length, token.take(50))
        val mono = jwtService.validateAccessToken(token)
        log.warn("JwtAuthenticationConverter: validateAccessToken returned Mono, now mapping")
        return mono
            .map<Authentication> { email ->
                log.warn("JwtAuthenticationConverter: validateAccessToken SUCCEEDED for {}", email)
                UsernamePasswordAuthenticationToken(email, null)
            }
    }
}
