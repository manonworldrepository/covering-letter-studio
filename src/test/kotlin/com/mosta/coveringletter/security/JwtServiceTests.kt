package com.mosta.coveringletter.security

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class JwtServiceTests {

    private lateinit var jwtService: JwtService

    @BeforeEach
    fun setup() {
        jwtService = JwtService(
            accessTokenValiditySeconds = 900,
            refreshTokenValiditySeconds = 604800,
            privateKeyBase64 = "",
            publicKeyBase64 = ""
        )
    }

    @Test
    fun `generateAccessToken creates a valid token`() {
        val token = jwtService.generateAccessToken("test@example.com")
        assertTrue(token.isNotEmpty())
        assertTrue(token.contains("."))
    }

    @Test
    fun `generateRefreshToken creates a valid token`() {
        val token = jwtService.generateRefreshToken("test@example.com")
        assertTrue(token.isNotEmpty())
        assertTrue(token.contains("."))
    }

    @Test
    fun `validateAccessToken returns email for valid token`() {
        val token = jwtService.generateAccessToken("test@example.com")
        val email = jwtService.validateAccessToken(token).block()
        assertEquals("test@example.com", email)
    }

    @Test
    fun `validateRefreshToken returns email for valid token`() {
        val token = jwtService.generateRefreshToken("test@example.com")
        val email = jwtService.validateRefreshToken(token).block()
        assertEquals("test@example.com", email)
    }

    @Test
    fun `validateAccessToken rejects refresh token`() {
        val token = jwtService.generateRefreshToken("test@example.com")
        val email = jwtService.validateAccessToken(token).block()
        assertTrue(email.isNullOrEmpty())
    }

    @Test
    fun `validateRefreshToken rejects access token`() {
        val token = jwtService.generateAccessToken("test@example.com")
        val email = jwtService.validateRefreshToken(token).block()
        assertTrue(email.isNullOrEmpty())
    }

    @Test
    fun `validateAccessToken rejects malformed token`() {
        val email = jwtService.validateAccessToken("invalid.token.here").block()
        assertTrue(email.isNullOrEmpty())
    }

    @Test
    fun `validateRefreshToken rejects malformed token`() {
        val email = jwtService.validateRefreshToken("invalid.token.here").block()
        assertTrue(email.isNullOrEmpty())
    }

    @Test
    fun `validateAccessToken rejects empty token`() {
        val email = jwtService.validateAccessToken("").block()
        assertTrue(email.isNullOrEmpty())
    }

    @Test
    fun `validateRefreshToken rejects empty token`() {
        val email = jwtService.validateRefreshToken("").block()
        assertTrue(email.isNullOrEmpty())
    }

    @Test
    fun `validateAccessToken rejects tampered token`() {
        val token = jwtService.generateAccessToken("test@example.com")
        val tampered = token.substring(0, token.length - 5) + "XXXXX"
        val email = jwtService.validateAccessToken(tampered).block()
        assertTrue(email.isNullOrEmpty())
    }

    @Test
    fun `validateRefreshToken rejects tampered token`() {
        val token = jwtService.generateRefreshToken("test@example.com")
        val tampered = token.substring(0, token.length - 5) + "XXXXX"
        val email = jwtService.validateRefreshToken(tampered).block()
        assertTrue(email.isNullOrEmpty())
    }

    @Test
    fun `access token and refresh token are different`() {
        val accessToken = jwtService.generateAccessToken("test@example.com")
        val refreshToken = jwtService.generateRefreshToken("test@example.com")
        assertTrue(accessToken != refreshToken)
    }

    @Test
    fun `tokens with different emails produce different tokens`() {
        val token1 = jwtService.generateAccessToken("user1@example.com")
        val token2 = jwtService.generateAccessToken("user2@example.com")
        assertTrue(token1 != token2)
    }

    @Test
    fun `validateAccessToken rejects expired token`() {
        // Create a service with 1-second token expiry
        val shortLivedService = JwtService(
            accessTokenValiditySeconds = 1,
            refreshTokenValiditySeconds = 604800,
            privateKeyBase64 = "",
            publicKeyBase64 = ""
        )
        val token = shortLivedService.generateAccessToken("test@example.com")
        // Wait for token to expire
        Thread.sleep(1500)
        val email = shortLivedService.validateAccessToken(token).block()
        assertTrue(email.isNullOrEmpty(), "Expired token should be rejected")
    }
}
