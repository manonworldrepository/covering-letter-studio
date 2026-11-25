package com.mosta.coveringletter.security

import java.security.KeyPairGenerator
import java.util.Base64
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
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
        jwtService.init()
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
        ).also { it.init() }
        val token = shortLivedService.generateAccessToken("test@example.com")
        // Wait for token to expire
        Thread.sleep(1500)
        val email = shortLivedService.validateAccessToken(token).block()
        assertTrue(email.isNullOrEmpty(), "Expired token should be rejected")
    }

    @Test
    fun `partial shared key configuration fails instead of generating a random key`() {
        val keyPair = encodedKeyPair()

        assertThrows(IllegalStateException::class.java) {
            JwtService(900, 604800, keyPair.first, "").init()
        }
    }

    @Test
    fun `invalid shared key configuration fails instead of generating a random key`() {
        assertThrows(IllegalStateException::class.java) {
            JwtService(900, 604800, "invalid", "invalid").init()
        }
    }

    @Test
    fun `mismatched shared public and private keys fail initialization`() {
        val signingKeys = encodedKeyPair()
        val verificationKeys = encodedKeyPair()

        assertThrows(IllegalStateException::class.java) {
            JwtService(900, 604800, signingKeys.first, verificationKeys.second).init()
        }
    }

    @Test
    fun `required shared key pair fails initialization when missing`() {
        assertThrows(IllegalStateException::class.java) {
            JwtService(900, 604800, "", "", requireSharedKey = true).init()
        }
    }

    @Test
    fun `matching shared key pair signs and validates tokens`() {
        val keys = encodedKeyPair()
        val service = JwtService(900, 604800, keys.first, keys.second).also { it.init() }

        val token = service.generateAccessToken("test@example.com")
        assertEquals("test@example.com", service.validateAccessToken(token).block())
    }

    @Test
    fun `shared key pair from base64 loads correctly and validates tokens`() {
        val keys = encodedKeyPair()

        val service = JwtService(
            accessTokenValiditySeconds = 900,
            refreshTokenValiditySeconds = 604800,
            privateKeyBase64 = keys.first,
            publicKeyBase64 = keys.second
        ).also { it.init() }

        // Generate and validate an access token
        val token = service.generateAccessToken("test@example.com")
        assertTrue(token.isNotEmpty(), "Token should be generated")
        val email = service.validateAccessToken(token).block()
        assertEquals("test@example.com", email, "Token should validate with the shared key pair")
    }

    @Test
    fun `shared key pair with newlines in base64 still works`() {
        // Simulate Cloud Run storing base64 with line breaks (YAML block scalar format)
        val keys = encodedKeyPair()
        val privateKeyB64 = keys.first.chunked(64).joinToString("\n")
        val publicKeyB64 = keys.second.chunked(64).joinToString("\n")

        val service = JwtService(
            accessTokenValiditySeconds = 900,
            refreshTokenValiditySeconds = 604800,
            privateKeyBase64 = privateKeyB64,
            publicKeyBase64 = publicKeyB64
        ).also { it.init() }

        val token = service.generateAccessToken("test@example.com")
        val email = service.validateAccessToken(token).block()
        assertEquals("test@example.com", email, "Token should validate with shared key pair")
    }

    private fun encodedKeyPair(): Pair<String, String> {
        val keyPair = KeyPairGenerator.getInstance("RSA").apply { initialize(2048) }.generateKeyPair()
        return Base64.getEncoder().encodeToString(keyPair.private.encoded) to
            Base64.getEncoder().encodeToString(keyPair.public.encoded)
    }
}
