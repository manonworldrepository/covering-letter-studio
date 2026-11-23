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

    @Test
    fun `shared key pair from base64 loads correctly and validates tokens`() {
        // Use the actual keys from the deployment plan to verify they work
        val privateKeyB64 = "MIIEvQIBADANBgkqhkiG9w0BAQEFAASCBKcwggSjAgEAAoIBAQCVljU8eHkRoLw3SQkPdD3EH3bq5B/LBu9x/dJSRWf6uqoCH7R+75yjnrGA8+kdjlErXDvpYke5QAGaGhFNvbX+t+AWBqTMSDqLL9e/sCnw1+mBbyHPkg+WppTPuW1BO9nkS4pqwKZGs8ET67os8u8L46AQKCnaCaJswwTKxfQ46eclEQ57Whpse3i7AjiXeEWHaMZR9skPeogXPqlQXr1ZNAo2xX7xPAKzisYuOe+rUhIGtVQyO1zjWoQxWkrsnlTlVfvPAWpUecHLVsadKjRXwdQPUK1TUu2CAbEyCOl6ywqHUI8Tk9GmcD7bUHFbeH4DKzGeKSiUUljJMGmKfeslAgMBAAECggEACHfnTflAsAufoX5G1Iikods+0gprIFp6qMM7/8CtI8fN2G6aEHZD3GF0tBOwNqZNbb0m084RYxPpY04m0ktd/RcaHl/EPvUMghUJy5t6AbKqJf6NdUupX+0l+eKJ+bTyihbwCvNwFmBBGyhvsvFn22OeC/wcw8Vi3BxoQ4sptHph8N5ZJNKl/FNL+yLnYwV25JeqqEilILoXOTRk9xwh1djbREEEIck3slj9i/FvKGM+jdkRpaTDVH9u9wO5IKep9el0ra+J0sRssUZlXljZvEMsxNff8KHDZf+VOsnyPBQ2Ii0tFfcX913sLskf/jkhNhNkpNaooNv+VRcHa7DSSwKBgQDI+P5p44Kp+j0DqcAQIXvSZhe5c0mGpSZ+TptXQ08u68DERkIpBRb/zFk/dg60snqLLF5NpxHevlyZyRslCgYwcB4vG1buKnFtF/iJAb1GcAuikhrL5M93Q1DKIPckbzpNfe97JhhVTnpgSB3TE+DqGvC01AT4zfmFMCDNLCBPGwKBgQC+i11D4N50tuzSNcFy/0J0qs5BfEYlSImzSKuK8N4iKDgNXRxCE2GYHDfeuLL92cadSGuZauZEFhqw6UBeYZdIj/vsuxpbEb00X0Bjb8eWzu9+zUk9CimfUOtxzJkiQBnNjusGQYjUkuTM0+Xq4TMhYGsmyeP2N2T4ADyHYKkSvwKBgHmelPengq8yjDHnTB924+PTfI/xwJEG6GCPWVpZlVWjvagJjIgeWGi8VVYeL5ASYi5Oz1LWw+2z7X3zQcYMZupxLxL6zISckr+ePf1LXCuAKspa+HRjJ1nZFs77XBRkazfx+jbgOuGE+HHDsqjF4tpJK1mC5O9cVp/PHEqNZY/5AoGBAKZGq36ebsHfF05hRoz5n557pz2SGwxR/ogLNFcs1ktsWW2gARbQHuxEQ6UB/IB1WFLMbckVdheYSnZzNY6nDwNtUmRE6jzqf/eB34hmdP9vWaOHtQ+lHKlAThWie0deNKYiv4lYCvWlsi/kAdEz8BCCOT9CEsI7mMqqOifQ+/m1AoGALHrZgZ0Rz4HoMWNShTnHOUGw4ahVu7oQBwt5Y0ADcP791LzQi3zDMbCiDmfLsJ+FkfZoVDUWWVrJVyORe7NAtaHUmWkrmy9zIgqIh/kW/J/Dhd38zDkIxss2FHIJ1w4FBR5XEOY4O0hSe6jlEq36mh4JN7Gd+L7Mn3WFdVSdlKs="
        val publicKeyB64 = "MIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8AMIIBCgKCAQEAlZY1PHh5EaC8N0kJD3Q9xB926uQfywbvcf3SUkVn+rqqAh+0fu+co56xgPPpHY5RK1w76WJHuUABmhoRTb21/rfgFgakzEg6iy/Xv7Ap8NfpgW8hz5IPlqaUz7ltQTvZ5EuKasCmRrPBE+u6LPLvC+OgECgp2gmibMMEysX0OOnnJREOe1oabHt4uwI4l3hFh2jGUfbJD3qIFz6pUF69WTQKNsV+8TwCs4rGLjnvq1ISBrVUMjtc41qEMVpK7J5U5VX7zwFqVHnBy1bGnSo0V8HUD1CtU1LtggGxMgjpessKh1CPE5PRpnA+21BxW3h+AysxnikolFJYyTBpin3rJQIDAQAB"

        val service = JwtService(
            accessTokenValiditySeconds = 900,
            refreshTokenValiditySeconds = 604800,
            privateKeyBase64 = privateKeyB64,
            publicKeyBase64 = publicKeyB64
        )

        // Generate and validate an access token
        val token = service.generateAccessToken("test@example.com")
        assertTrue(token.isNotEmpty(), "Token should be generated")
        val email = service.validateAccessToken(token).block()
        assertEquals("test@example.com", email, "Token should validate with the shared key pair")
    }

    @Test
    fun `shared key pair with newlines in base64 still works`() {
        // Simulate Cloud Run storing base64 with line breaks (YAML block scalar format)
        val privateKeyB64 = "MIIEvQIBADANBgkqhkiG9w0BAQEFAASCBKcwggSjAgEAAoIBAQCVljU8eHkRoLw3SQkPdD3EH3bq5B/LBu9x/dJSRWf6uqoCH7R+75yjnrGA8+kdjlErXDvpYke5QAGaGhFNvbX+t+AWBqTMSDqLL9e/sCnw1+mBbyHPkg+WppTPuW1BO9nkS4pqwKZGs8ET67os8u8L46AQKCnaCaJswwTKxfQ46eclEQ57Whpse3i7AjiXeEWHaMZR9skPeogXPqlQXr1ZNAo2xX7xPAKzisYuOe+rUhIGtVQyO1zjWoQxWkrsnlTlVfvPAWpUecHLVsadKjRXwdQPUK1TUu2CAbEyCOl6ywqHUI8Tk9GmcD7bUHFbeH4DKzGeKSiUUljJMGmKfeslAgMBAAECggEACHfnTflAsAufoX5G1Iikods+0gprIFp6qMM7/8CtI8fN2G6aEHZD3GF0tBOwNqZNbb0m084RYxPpY04m0ktd/RcaHl/EPvUMghUJy5t6AbKqJf6NdUupX+0l+eKJ+bTyihbwCvNwFmBBGyhvsvFn22OeC/wcw8Vi3BxoQ4sptHph8N5ZJNKl/FNL+yLnYwV25JeqqEilILoXOTRk9xwh1djbREEEIck3slj9i/FvKGM+jdkRpaTDVH9u9wO5IKep9el0ra+J0sRssUZlXljZvEMsxNff8KHDZf+VOsnyPBQ2Ii0tFfcX913sLskf/jkhNhNkpNaooNv+VRcHa7DSSwKBgQDI+P5p44Kp+j0DqcAQIXvSZhe5c0mGpSZ+TptXQ08u68DERkIpBRb/zFk/dg60snqLLF5NpxHevlyZyRslCgYwcB4vG1buKnFtF/iJAb1GcAuikhrL5M93Q1DKIPckbzpNfe97JhhVTnpgSB3TE+DqGvC01AT4zfmFMCDNLCBPGwKBgQC+i11D4N50tuzSNcFy/0J0qs5BfEYlSImzSKuK8N4iKDgNXRxCE2GYHDfeuLL92cadSGuZauZEFhqw6UBeYZdIj/vsuxpbEb00X0Bjb8eWzu9+zUk9CimfUOtxzJkiQBnNjusGQYjUkuTM0+Xq4TMhYGsmyeP2N2T4ADyHYKkSvwKBgHmelPengq8yjDHnTB924+PTfI/xwJEG6GCPWVpZlVWjvagJjIgeWGi8VVYeL5ASYi5Oz1LWw+2z7X3zQcYMZupxLxL6zISckr+ePf1LXCuAKspa+HRjJ1nZFs77XBRkazfx+jbgOuGE+HHDsqjF4tpJK1mC5O9cVp/PHEqNZY/5AoGBAKZGq36ebsHfF05hRoz5n557pz2SGwxR/ogLNFcs1ktsWW2gARbQHuxEQ6UB/IB1WFLMbckVdheYSnZzNY6nDwNtUmRE6jzqf/eB34hmdP9vWaOHtQ+lHKlAThWie0deNKYiv4lYCvWlsi/kAdEz8BCCOT9CEsI7mMqqOifQ+/m1AoGALHrZgZ0Rz4HoMWNShTnHOUGw4ahVu7oQBwt5Y0ADcP791LzQi3zDMbCiDmfLsJ+FkfZoVDUWWVrJVyORe7NAtaHUmWkrmy9zIgqIh/kW/J/Dhd38zDkIxss2FHIJ1w4FBR5XEOY4O0hSe6jlEq36mh4JN7Gd+L7Mn3WFdVSdlKs="
        val publicKeyB64 = "MIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8AMIIBCgKCAQEAlZY1PHh5EaC8N0kJD3Q9xB926uQfywbvcf3SUkVn+rqqAh+0fu+co56xgPPpHY5RK1w76WJHuUABmhoRTb21/rfgFgakzEg6iy/Xv7Ap8NfpgW8hz5IPlqaUz7ltQTvZ5EuKasCmRrPBE+u6LPLvC+OgECgp2gmibMMEysX0OOnnJREOe1oabHt4uwI4l3hFh2jGUfbJD3qIFz6pUF69WTQKNsV+8TwCs4rGLjnvq1ISBrVUMjtc41qEMVpK7J5U5VX7zwFqVHnBy1bGnSo0V8HUD1CtU1LtggGxMgjpessKh1CPE5PRpnA+21BxW3h+AysxnikolFJYyTBpin3rJQIDAQAB"

        val service = JwtService(
            accessTokenValiditySeconds = 900,
            refreshTokenValiditySeconds = 604800,
            privateKeyBase64 = privateKeyB64,
            publicKeyBase64 = publicKeyB64
        )

        val token = service.generateAccessToken("test@example.com")
        val email = service.validateAccessToken(token).block()
        assertEquals("test@example.com", email, "Token should validate with shared key pair")
    }
}
