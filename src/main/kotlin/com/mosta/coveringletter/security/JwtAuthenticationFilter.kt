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
        val token = exchange.request.cookies[ACCESS_TOKEN_COOKIE]?.firstOrNull()?.value
            ?: return Mono.empty()

        return jwtService.validateAccessToken(token)
            .map { email ->
                UsernamePasswordAuthenticationToken(email, null)
            }
    }
}
