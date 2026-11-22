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
                    "/api/auth/logout"
                ).permitAll()
                auth.anyExchange().authenticated()
            }
            .httpBasic { it.disable() }
            .oauth2Login { oauth ->
                oauth.loginPage("/login.html")
                oauth.authenticationSuccessHandler(LoginSuccessHandler())
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

    inner class LoginSuccessHandler : ServerAuthenticationSuccessHandler {
        override fun onAuthenticationSuccess(exchange: WebFilterExchange, authentication: Authentication): Mono<Void> {
            val name = authentication.name.lowercase()
            return if (allowedEmails.contains(name)) {
                val wasRefresh = exchange.exchange.request.queryParams["refresh"]?.firstOrNull() == "true"
                if (wasRefresh) {
                    userActions.record("Session refreshed via Google Sign-In")
                } else {
                    userActions.record("Signed in with Google: $name")
                }
                val accessToken = jwtService.generateAccessToken(name)
                val refreshToken = jwtService.generateRefreshToken(name)
                val response = exchange.exchange.response
                response.addCookie(createAccessTokenCookie(accessToken))
                response.addCookie(createRefreshTokenCookie(refreshToken))
                response.statusCode = HttpStatus.FOUND
                response.headers.setLocation(java.net.URI.create("/"))
                response.setComplete()
            } else {
                exchange.exchange.response.statusCode = HttpStatus.FOUND
                exchange.exchange.response.headers.setLocation(java.net.URI.create("/login.html?error=access_denied"))
                exchange.exchange.response.setComplete()
            }
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
        return authenticationConverter.convert(exchange)
            .flatMap { auth: Authentication ->
                val ctx = SecurityContextImpl(auth)
                chain.filter(exchange)
                    .contextWrite(ReactiveSecurityContextHolder.withSecurityContext(Mono.just(ctx)))
            }
            .switchIfEmpty(chain.filter(exchange))
    }
}

class SecurityContextImpl(
    private var authentication: Authentication
) : org.springframework.security.core.context.SecurityContext {
    override fun getAuthentication(): Authentication? = authentication
    override fun setAuthentication(authentication: Authentication?) {
        this.authentication = authentication ?: this.authentication
    }
}
