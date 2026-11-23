package com.mosta.coveringletter.security

import com.mosta.coveringletter.activity.UserActionLogBuffer
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.ResponseCookie
import org.springframework.security.config.web.server.ServerHttpSecurity
import org.springframework.security.core.Authentication
import org.springframework.security.core.AuthenticationException
import org.springframework.security.web.server.SecurityWebFilterChain
import org.springframework.security.config.web.server.SecurityWebFiltersOrder
import org.springframework.security.web.server.ServerAuthenticationEntryPoint
import org.springframework.security.web.server.WebFilterExchange
import org.springframework.security.web.server.authentication.ServerAuthenticationSuccessHandler
import org.springframework.security.web.server.authentication.ServerAuthenticationFailureHandler
import org.springframework.web.server.ServerWebExchange
import reactor.core.publisher.Mono
import org.springframework.security.core.context.ReactiveSecurityContextHolder
import org.springframework.security.core.context.SecurityContextImpl

@Configuration
class SecurityConfig(
    private val userActions: UserActionLogBuffer,
    private val jwtService: JwtService,
    private val jwtAuthenticationConverter: JwtAuthenticationConverter
) {

    @Value("\${coverletter.security.allowed-emails:info@manonworld.de}")
    private lateinit var allowedEmailsProperty: String

    private val allowedEmails: Set<String>
        get() = allowedEmailsProperty.split(",").map { it.trim().lowercase() }.toSet()

    @Bean
    fun securityWebFilterChain(http: ServerHttpSecurity): SecurityWebFilterChain {
        http
            .csrf { it.disable() }
            .exceptionHandling { exceptions ->
                exceptions.authenticationEntryPoint(ApiAuthenticationEntryPoint())
            }
            .authorizeExchange { auth ->
                auth.pathMatchers(
                    "/login",
                    "/login.html",
                    "/privacy.html",
                    "/terms.html",
                    "/error",
                    "/favicon.ico",
                    "/login/oauth2/code/*",
                    "/api/auth/refresh",
                    "/api/auth/logout",
                    "/api/auth/login-success",
                    "/api/session/heartbeat"
                ).permitAll()
                auth.anyExchange().authenticated()
            }
            .httpBasic { it.disable() }
            .oauth2Login { oauth ->
                oauth.loginPage("/login.html")
                oauth.authenticationSuccessHandler(EmailCheckingSuccessHandler())
                oauth.authenticationFailureHandler(LoginFailureHandler())
            }
            .logout { logout ->
                logout.logoutUrl("/logout")
            }
            .addFilterAt(jwtAuthenticationFilter(), SecurityWebFiltersOrder.AUTHENTICATION)
        return http.build()
    }

    @Bean
    fun jwtAuthenticationFilter(): JwtAuthenticationWebFilter {
        return JwtAuthenticationWebFilter(jwtAuthenticationConverter)
    }

    class ApiAuthenticationEntryPoint : ServerAuthenticationEntryPoint {
        override fun commence(exchange: ServerWebExchange, ex: AuthenticationException): Mono<Void> {
            val path = exchange.request.path.value()
            return if (path.startsWith("/api/")) {
                exchange.response.statusCode = HttpStatus.UNAUTHORIZED
                exchange.response.headers.contentType = MediaType.APPLICATION_JSON
                exchange.response.writeWith(
                    Mono.just(
                        exchange.response.bufferFactory().wrap(
                            """{"message":"Session expired. Please sign in again."}""".toByteArray()
                        )
                    )
                )
            } else {
                exchange.response.statusCode = HttpStatus.FOUND
                exchange.response.headers.setLocation(java.net.URI.create("/login.html"))
                exchange.response.setComplete()
            }
        }
    }

    inner class EmailCheckingSuccessHandler : ServerAuthenticationSuccessHandler {
        override fun onAuthenticationSuccess(exchange: WebFilterExchange, authentication: Authentication): Mono<Void> {
            val name = authentication.name.lowercase()
            return if (allowedEmails.contains(name)) {
                val wasRefresh = exchange.exchange.request.queryParams["refresh"]?.firstOrNull() == "true"
                if (wasRefresh) {
                    userActions.record("Session refreshed via Google Sign-In")
                } else {
                    userActions.record("Signed in with Google: $name")
                }
                // Generate JWT tokens and pass them via URL params for client-side cookie setting
                val accessToken = jwtService.generateAccessToken(name)
                val refreshToken = jwtService.generateRefreshToken(name)
                val encodedAccessToken = java.net.URLEncoder.encode(accessToken, java.nio.charset.StandardCharsets.UTF_8)
                val encodedRefreshToken = java.net.URLEncoder.encode(refreshToken, java.nio.charset.StandardCharsets.UTF_8)
                val refreshParam = if (wasRefresh) "&refresh=true" else ""
                exchange.exchange.response.statusCode = HttpStatus.FOUND
                exchange.exchange.response.headers.setLocation(
                    java.net.URI.create("/?access_token=$encodedAccessToken&refresh_token=$encodedRefreshToken$refreshParam")
                )
                exchange.exchange.response.setComplete()
            } else {
                exchange.exchange.response.statusCode = HttpStatus.FOUND
                exchange.exchange.response.headers.setLocation(java.net.URI.create("/login.html?error=access_denied"))
                exchange.exchange.response.setComplete()
            }
        }
    }

    class LoginFailureHandler : ServerAuthenticationFailureHandler {
        override fun onAuthenticationFailure(exchange: WebFilterExchange, exception: AuthenticationException): Mono<Void> {
            exchange.exchange.response.statusCode = HttpStatus.FOUND
            exchange.exchange.response.headers.setLocation(java.net.URI.create("/login.html?error=authentication_failed"))
            return exchange.exchange.response.setComplete()
        }
    }
}


class JwtAuthenticationWebFilter(
    private val authenticationConverter: JwtAuthenticationConverter
) : org.springframework.web.server.WebFilter {
    override fun filter(exchange: ServerWebExchange, chain: org.springframework.web.server.WebFilterChain): Mono<Void> {
        // Skip JWT validation for token refresh endpoint - it handles its own validation
        val path = exchange.request.path.value()
        if (path == "/api/auth/refresh") {
            return chain.filter(exchange)
        }
        // Use flatMap so authentication is resolved BEFORE downstream filters run
        return authenticationConverter.convert(exchange)
            .flatMap { auth ->
                chain.filter(exchange).contextWrite(
                    ReactiveSecurityContextHolder.withSecurityContext(
                        Mono.just(SecurityContextImpl(auth))
                    )
                )
            }
            .switchIfEmpty(chain.filter(exchange))
    }
}
