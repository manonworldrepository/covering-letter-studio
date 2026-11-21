package com.mosta.coveringletter.security

import com.mosta.coveringletter.api.UserController
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.http.HttpCookie
import org.springframework.http.HttpStatus
import org.springframework.mock.http.server.reactive.MockServerHttpRequest
import org.springframework.mock.web.server.MockServerWebExchange

class TokenRefreshTests {

    private lateinit var jwtService: JwtService
    private lateinit var controller: UserController

    @BeforeEach
    fun setup() {
        jwtService = JwtService(900, 604800)
        controller = UserController(jwtService)
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
        val newAccessCookie = exchange.response.cookies[JwtAuthenticationConverter.ACCESS_TOKEN_COOKIE]
        val newRefreshCookie = exchange.response.cookies[JwtAuthenticationConverter.REFRESH_TOKEN_COOKIE]
        assertEquals(true, newAccessCookie?.isNotEmpty() == true)
        assertEquals(true, newRefreshCookie?.isNotEmpty() == true)
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
}
