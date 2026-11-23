package com.mosta.coveringletter.security

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.http.HttpCookie
import org.springframework.mock.http.server.reactive.MockServerHttpRequest
import org.springframework.mock.web.server.MockServerWebExchange
import org.springframework.security.core.Authentication
import org.springframework.security.core.context.ReactiveSecurityContextHolder
import org.springframework.web.server.WebFilterChain
import reactor.core.publisher.Mono
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference

class JwtAuthenticationFilterTests {

    private lateinit var jwtService: JwtService
    private lateinit var converter: JwtAuthenticationConverter

    @BeforeEach
    fun setup() {
        jwtService = JwtService(900, 604800)
        converter = JwtAuthenticationConverter(jwtService)
    }

    @Test
    fun `convert returns authentication for valid access token`() {
        val token = jwtService.generateAccessToken("test@example.com")
        val exchange = createExchangeWithCookie(JwtAuthenticationConverter.ACCESS_TOKEN_COOKIE, token)

        val result = converter.convert(exchange).block()

        assertTrue(result is Authentication)
        assertEquals("test@example.com", result?.name)
    }

    @Test
    fun `convert returns empty when no cookie`() {
        val exchange = MockServerWebExchange.from(MockServerHttpRequest.get("/").build())

        val result = converter.convert(exchange).block()

        assertNull(result)
    }

    @Test
    fun `convert returns empty for invalid token`() {
        val exchange = createExchangeWithCookie(JwtAuthenticationConverter.ACCESS_TOKEN_COOKIE, "invalid.token.here")

        val result = converter.convert(exchange).block()

        assertNull(result)
    }

    @Test
    fun `convert returns empty for refresh token in access cookie`() {
        val token = jwtService.generateRefreshToken("test@example.com")
        val exchange = createExchangeWithCookie(JwtAuthenticationConverter.ACCESS_TOKEN_COOKIE, token)

        val result = converter.convert(exchange).block()

        assertNull(result)
    }

    @Test
    fun `convert returns empty for empty token`() {
        val exchange = createExchangeWithCookie(JwtAuthenticationConverter.ACCESS_TOKEN_COOKIE, "")

        val result = converter.convert(exchange).block()

        assertNull(result)
    }

    @Test
    fun `convert returns different emails for different tokens`() {
        val token1 = jwtService.generateAccessToken("user1@example.com")
        val token2 = jwtService.generateAccessToken("user2@example.com")

        val exchange1 = createExchangeWithCookie(JwtAuthenticationConverter.ACCESS_TOKEN_COOKIE, token1)
        val exchange2 = createExchangeWithCookie(JwtAuthenticationConverter.ACCESS_TOKEN_COOKIE, token2)

        val result1 = converter.convert(exchange1).block()
        val result2 = converter.convert(exchange2).block()

        assertEquals("user1@example.com", result1?.name)
        assertEquals("user2@example.com", result2?.name)
    }

    @Test
    fun `filter invokes downstream once and exposes valid JWT authentication`() {
        val token = jwtService.generateAccessToken("test@example.com")
        val exchange = createExchangeWithCookie(JwtAuthenticationConverter.ACCESS_TOKEN_COOKIE, token)
        val invocations = AtomicInteger()
        val authenticatedName = AtomicReference<String>()
        val filter = JwtAuthenticationWebFilter(converter)
        val chain = WebFilterChain {
            invocations.incrementAndGet()
            ReactiveSecurityContextHolder.getContext()
                .map { it.authentication?.name ?: error("Authentication missing from reactive context.") }
                .doOnNext(authenticatedName::set)
                .then()
        }

        filter.filter(exchange, chain).block()

        assertEquals(1, invocations.get())
        assertEquals("test@example.com", authenticatedName.get())
    }

    @Test
    fun `filter invokes downstream once when JWT is missing`() {
        val exchange = MockServerWebExchange.from(MockServerHttpRequest.get("/").build())
        val invocations = AtomicInteger()
        val filter = JwtAuthenticationWebFilter(converter)
        val chain = WebFilterChain {
            invocations.incrementAndGet()
            Mono.empty()
        }

        filter.filter(exchange, chain).block()

        assertEquals(1, invocations.get())
    }

    private fun createExchangeWithCookie(cookieName: String, cookieValue: String): MockServerWebExchange {
        val request = MockServerHttpRequest.get("/")
            .cookie(HttpCookie(cookieName, cookieValue))
            .build()
        return MockServerWebExchange.from(request)
    }
}
