package com.mosta.coveringletter.api.controller

import com.mosta.coveringletter.activity.UserActionLogBuffer
import com.mosta.coveringletter.api.response.UserInfoResponse
import com.mosta.coveringletter.security.JwtAuthenticationConverter
import com.mosta.coveringletter.security.JwtService
import com.mosta.coveringletter.settings.AllowedEmailsService
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.ResponseCookie
import org.springframework.security.core.context.SecurityContext
import org.springframework.security.oauth2.core.user.OAuth2User
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.server.ServerWebExchange
import reactor.core.publisher.Mono
import java.security.Principal

@RestController
class UserController(
    private val jwtService: JwtService,
    private val userActions: UserActionLogBuffer,
    private val allowedEmails: AllowedEmailsService
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
            Mono.just(UserInfoResponse(email = email, name = name, isAdmin = allowedEmails.isAdmin(email)))
        }

    @GetMapping("/api/session/heartbeat")
    fun heartbeat(exchange: ServerWebExchange): Mono<Void> {
        // Read token directly from cookie/header without relying on SecurityContext
        // This endpoint is permitAll() so SecurityContext may not be populated
        val authHeader = exchange.request.headers.getFirst("Authorization")
        val tokenFromHeader = if (authHeader != null && authHeader.startsWith("Bearer ")) {
            authHeader.substring(7)
        } else {
            null
        }
        val token = tokenFromHeader
            ?: exchange.request.cookies[JwtAuthenticationConverter.ACCESS_TOKEN_COOKIE]?.firstOrNull()?.value
            ?: return unauthorizedHeartbeat(exchange)

        return jwtService.validateAccessToken(token)
            .filter(allowedEmails::allows)
            .flatMap { _ ->
                okHeartbeat(exchange)
            }
            .switchIfEmpty(unauthorizedHeartbeat(exchange))
    }

    private fun okHeartbeat(exchange: ServerWebExchange): Mono<Void> {
        val response = exchange.response
        response.statusCode = HttpStatus.OK
        response.headers.contentType = MediaType.APPLICATION_JSON
        return response.writeWith(
            Mono.just(
                response.bufferFactory().wrap("""{"status":"ok"}""".toByteArray())
            )
        )
    }

    private fun unauthorizedHeartbeat(exchange: ServerWebExchange): Mono<Void> {
        val response = exchange.response
        response.statusCode = HttpStatus.UNAUTHORIZED
        response.headers.contentType = MediaType.APPLICATION_JSON
        return response.writeWith(
            Mono.just(
                response.bufferFactory().wrap("""{"status":"unauthorized"}""".toByteArray())
            )
        )
    }

    @GetMapping("/api/auth/login-success")
    fun loginSuccess(exchange: ServerWebExchange): Mono<Void> {
        val email = exchange.request.queryParams["email"]?.firstOrNull() ?: return unauthorizedResponse(exchange)
        if (!allowedEmails.allows(email)) return unauthorizedResponse(exchange)
        val wasRefresh = exchange.request.queryParams["refresh"]?.firstOrNull() == "true"

        val accessToken = jwtService.generateAccessToken(email)
        val refreshToken = jwtService.generateRefreshToken(email)

        val response = exchange.response
        response.cookies.put(JwtAuthenticationConverter.ACCESS_TOKEN_COOKIE, listOf(createAccessTokenCookie(accessToken)))
        response.cookies.put(JwtAuthenticationConverter.REFRESH_TOKEN_COOKIE, listOf(createRefreshTokenCookie(refreshToken)))

        // Redirect to main page
        response.statusCode = HttpStatus.FOUND
        response.headers.setLocation(java.net.URI.create("/"))
        return response.setComplete()
    }

    @PostMapping("/api/auth/refresh")
    fun refreshTokens(exchange: ServerWebExchange): Mono<Void> {
        // Try Authorization header first, then fall back to cookie
        val authHeader = exchange.request.headers.getFirst("Authorization")
        val refreshTokenFromHeader = if (authHeader != null && authHeader.startsWith("Bearer ")) {
            authHeader.substring(7)
        } else {
            null
        }
        val refreshToken = refreshTokenFromHeader
            ?: exchange.request.cookies[JwtAuthenticationConverter.REFRESH_TOKEN_COOKIE]?.firstOrNull()?.value
            ?: return unauthorizedResponse(exchange)

        return jwtService.validateRefreshToken(refreshToken)
            .filter(allowedEmails::allows)
            .map { email -> email to jwtService.generateRefreshToken(email) }
            .flatMap { (email, newRefreshToken) ->
                userActions.record("Token refresh attempted", email)
                val newAccessToken = jwtService.generateAccessToken(email)
                val response = exchange.response
                response.statusCode = HttpStatus.OK
                response.headers.contentType = MediaType.APPLICATION_JSON
                userActions.record("Token refreshed successfully", email)
                // Return tokens in response body for client-side storage - properly escape for JSON
                val escapedAccessToken = newAccessToken.replace("\\", "\\\\").replace("\"", "\\\"")
                val escapedRefreshToken = newRefreshToken.replace("\\", "\\\\").replace("\"", "\\\"")
                response.writeWith(
                    Mono.just(
                        response.bufferFactory().wrap(
                            """{"status":"refreshed","access_token":"$escapedAccessToken","refresh_token":"$escapedRefreshToken"}""".toByteArray()
                        )
                    )
                )
                    .thenReturn(Unit)
            }
            .switchIfEmpty(Mono.defer {
                unauthorizedResponseWithLog(exchange).thenReturn(Unit)
            })
            .then()
    }

    private fun unauthorizedResponseWithLog(exchange: ServerWebExchange): Mono<Void> {
        userActions.record("Token refresh failed: refresh token expired")
        return unauthorizedResponse(exchange)
    }

    @PostMapping("/api/auth/logout")
    fun logout(exchange: ServerWebExchange): Mono<Void> {
        val response = exchange.response
        val clearAccess = ResponseCookie.from(JwtAuthenticationConverter.ACCESS_TOKEN_COOKIE, "")
            .httpOnly(true).secure(true).sameSite("None").path("/").maxAge(0).build()
        val clearRefresh = ResponseCookie.from(JwtAuthenticationConverter.REFRESH_TOKEN_COOKIE, "")
            .httpOnly(true).secure(true).sameSite("None").path("/").maxAge(0).build()
        // Also clear the Spring Security session cookie
        val clearSession = ResponseCookie.from("SESSION", "")
            .httpOnly(true).secure(true).sameSite("None").path("/").maxAge(0).build()
        response.cookies.put(JwtAuthenticationConverter.ACCESS_TOKEN_COOKIE, listOf(clearAccess))
        response.cookies.put(JwtAuthenticationConverter.REFRESH_TOKEN_COOKIE, listOf(clearRefresh))
        response.cookies.put("SESSION", listOf(clearSession))
        response.statusCode = HttpStatus.OK
        response.headers.contentType = MediaType.APPLICATION_JSON
        return exchange.getPrincipal<Principal>()
            .doOnNext { userActions.clear(it.name) }
            .then(
                response.writeWith(
                    Mono.just(
                        response.bufferFactory().wrap(
                            """{"status":"logged_out"}""".toByteArray()
                        )
                    )
                )
            )
    }

    private fun unauthorizedResponse(exchange: ServerWebExchange): Mono<Void> {
        val response = exchange.response
        response.statusCode = HttpStatus.UNAUTHORIZED
        response.headers.contentType = MediaType.APPLICATION_JSON
        return response.writeWith(
            Mono.just(
                response.bufferFactory().wrap(
                    """{"message":"Refresh token expired. Please sign in again."}""".toByteArray()
                )
            )
        )
    }

    private fun createAccessTokenCookie(token: String): ResponseCookie {
        return ResponseCookie.from(JwtAuthenticationConverter.ACCESS_TOKEN_COOKIE, token)
            .httpOnly(true)
            .secure(true)
            .sameSite("None")
            .path("/")
            .maxAge(900)
            .build()
    }

    private fun createRefreshTokenCookie(token: String): ResponseCookie {
        return ResponseCookie.from(JwtAuthenticationConverter.REFRESH_TOKEN_COOKIE, token)
            .httpOnly(true)
            .secure(true)
            .sameSite("None")
            .path("/")
            .maxAge(604800)
            .build()
    }
}
