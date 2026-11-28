package com.mosta.coveringletter.security

import com.mosta.coveringletter.activity.UserActionLogBuffer
import com.mosta.coveringletter.api.controller.UserController
import com.mosta.coveringletter.settings.AllowedEmailsService
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.springframework.http.HttpCookie
import org.springframework.http.HttpStatus
import org.springframework.mock.http.server.reactive.MockServerHttpRequest
import org.springframework.mock.web.server.MockServerWebExchange
import java.nio.file.Path

class TokenRefreshTests {

    private lateinit var jwtService: JwtService
    private lateinit var controller: UserController
    private lateinit var userActions: UserActionLogBuffer
    private lateinit var allowedEmails: AllowedEmailsService
    @TempDir
    lateinit var temporaryDirectory: Path

    @BeforeEach
    fun setup() {
        jwtService = JwtService(900, 604800, "", "").also { it.init() }
        userActions = UserActionLogBuffer()
        allowedEmails = AllowedEmailsService("").also { it.envFile = temporaryDirectory.resolve(".env") }
        allowedEmails.save("test@example.com")
        controller = UserController(jwtService, userActions, allowedEmails)
    }

    @Test
    fun `refreshTokens returns new tokens for valid refresh token`() {
        val refreshToken = jwtService.generateRefreshToken("test@example.com")
        val request = MockServerHttpRequest.post("/api/auth/refresh")
            .cookie(HttpCookie(JwtAuthenticationConverter.REFRESH_TOKEN_COOKIE, refreshToken))
            .build()
        val exchange = MockServerWebExchange.from(request)

        controller.refreshTokens(exchange).block()

        assertEquals(HttpStatus.OK, exchange.response.statusCode)
        // Tokens are now returned in the response body, not as cookies
        val body = exchange.response.bodyAsString.block() ?: ""
        assertTrue(body.contains("\"access_token\""))
        assertTrue(body.contains("\"refresh_token\""))
    }

    @Test
    fun `refreshTokens returns 401 when no refresh token cookie`() {
        val exchange = MockServerWebExchange.from(
            MockServerHttpRequest.post("/api/auth/refresh").build()
        )

        controller.refreshTokens(exchange).block()

        assertEquals(HttpStatus.UNAUTHORIZED, exchange.response.statusCode)
    }

    @Test
    fun `refreshTokens returns 401 for invalid refresh token`() {
        val request = MockServerHttpRequest.post("/api/auth/refresh")
            .cookie(HttpCookie(JwtAuthenticationConverter.REFRESH_TOKEN_COOKIE, "invalid.token.here"))
            .build()
        val exchange = MockServerWebExchange.from(request)

        controller.refreshTokens(exchange).block()

        assertEquals(HttpStatus.UNAUTHORIZED, exchange.response.statusCode)
    }

    @Test
    fun `refreshTokens returns 401 for access token in refresh cookie`() {
        val accessToken = jwtService.generateAccessToken("test@example.com")
        val request = MockServerHttpRequest.post("/api/auth/refresh")
            .cookie(HttpCookie(JwtAuthenticationConverter.REFRESH_TOKEN_COOKIE, accessToken))
            .build()
        val exchange = MockServerWebExchange.from(request)

        controller.refreshTokens(exchange).block()

        assertEquals(HttpStatus.UNAUTHORIZED, exchange.response.statusCode)
    }

    @Test
    fun `refreshTokens rejects a valid token after the account is removed from the allowlist`() {
        val refreshToken = jwtService.generateRefreshToken("test@example.com")
        allowedEmails.update("admin@example.com", "admin@example.com")
        val request = MockServerHttpRequest.post("/api/auth/refresh")
            .cookie(HttpCookie(JwtAuthenticationConverter.REFRESH_TOKEN_COOKIE, refreshToken))
            .build()
        val exchange = MockServerWebExchange.from(request)

        controller.refreshTokens(exchange).block()

        assertEquals(HttpStatus.UNAUTHORIZED, exchange.response.statusCode)
    }

    @Test
    fun `logout clears both cookies`() {
        val exchange = MockServerWebExchange.from(
            MockServerHttpRequest.post("/api/auth/logout").build()
        )

        controller.logout(exchange).block()

        assertEquals(HttpStatus.OK, exchange.response.statusCode)
        val accessCookie = exchange.response.cookies[JwtAuthenticationConverter.ACCESS_TOKEN_COOKIE]
        val refreshCookie = exchange.response.cookies[JwtAuthenticationConverter.REFRESH_TOKEN_COOKIE]
        assertEquals(true, accessCookie?.isNotEmpty() == true)
        assertEquals(true, refreshCookie?.isNotEmpty() == true)
    }

    @Test
    fun `refreshTokens logs success message on valid refresh`() {
        val refreshToken = jwtService.generateRefreshToken("test@example.com")
        val request = MockServerHttpRequest.post("/api/auth/refresh")
            .cookie(HttpCookie(JwtAuthenticationConverter.REFRESH_TOKEN_COOKIE, refreshToken))
            .build()
        val exchange = MockServerWebExchange.from(request)

        controller.refreshTokens(exchange).block()

        val logs = userActions.latest("test@example.com")
        assertTrue(logs.any { it.action.contains("Token refresh attempted") })
        assertTrue(logs.any { it.action.contains("Token refreshed successfully") })
    }

    @Test
    fun `refreshTokens logs failure message on expired token`() {
        val request = MockServerHttpRequest.post("/api/auth/refresh")
            .cookie(HttpCookie(JwtAuthenticationConverter.REFRESH_TOKEN_COOKIE, "invalid.token.here"))
            .build()
        val exchange = MockServerWebExchange.from(request)

        controller.refreshTokens(exchange).block()

        val logs = userActions.latest()
        assertTrue(logs.any { it.action.contains("Token refresh failed") })
    }
}
