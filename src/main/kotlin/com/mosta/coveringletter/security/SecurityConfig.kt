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
                oauth.authenticationSuccessHandler(IssuingLoginSuccessHandler())
                oauth.authenticationFailureHandler(LoginFailureHandler())
            }
            .logout { logout ->
                logout.logoutUrl("/logout")
            }
            .addFilterBefore(jwtCookieIssueFilter(), SecurityWebFiltersOrder.AUTHENTICATION)
            .addFilterAt(jwtAuthenticationFilter(), SecurityWebFiltersOrder.AUTHENTICATION)
        return http.build()
    }

    @Bean
    fun jwtCookieIssueFilter(): JwtCookieIssueFilter {
        return JwtCookieIssueFilter(jwtService, userActions)
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

    inner class IssuingLoginSuccessHandler : ServerAuthenticationSuccessHandler {
        override fun onAuthenticationSuccess(exchange: WebFilterExchange, authentication: Authentication): Mono<Void> {
            val name = authentication.name.lowercase()
            return if (allowedEmails.contains(name)) {
                val wasRefresh = exchange.exchange.request.queryParams["refresh"]?.firstOrNull() == "true"
                if (wasRefresh) {
                    userActions.record("Session refreshed via Google Sign-In")
                } else {
                    userActions.record("Signed in with Google: $name")
                }
                // Store email in a request attribute for the JwtCookieIssueFilter to pick up
                exchange.exchange.attributes["jwtIssueEmail"] = name
                exchange.exchange.attributes["jwtIssueWasRefresh"] = wasRefresh
                // Let the filter chain continue - JwtCookieIssueFilter will add cookies
                exchange.chain.filter(exchange.exchange)
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

/**
 * WebFilter that issues JWT cookies after successful OAuth2 login.
 * Checks for the jwtIssueEmail request attribute set by IssuingLoginSuccessHandler.
 */
class JwtCookieIssueFilter(
    private val jwtService: JwtService,
    private val userActions: UserActionLogBuffer
) : org.springframework.web.server.WebFilter {
    override fun filter(exchange: ServerWebExchange, chain: org.springframework.web.server.WebFilterChain): Mono<Void> {
        val email = exchange.attributes["jwtIssueEmail"] as? String ?: return chain.filter(exchange)
        val wasRefresh = exchange.attributes["jwtIssueWasRefresh"] as? Boolean ?: false

        val accessToken = jwtService.generateAccessToken(email)
        val refreshToken = jwtService.generateRefreshToken(email)

        val accessCookie = ResponseCookie.from(JwtAuthenticationConverter.ACCESS_TOKEN_COOKIE, accessToken)
            .httpOnly(true).secure(true).sameSite("None").path("/").maxAge(900).build()
        val refreshCookie = ResponseCookie.from(JwtAuthenticationConverter.REFRESH_TOKEN_COOKIE, refreshToken)
            .httpOnly(true).secure(true).sameSite("None").path("/").maxAge(604800).build()

        exchange.response.headers.add("Set-Cookie", accessCookie.toString())
        exchange.response.headers.add("Set-Cookie", refreshCookie.toString())
        exchange.response.statusCode = HttpStatus.FOUND
        exchange.response.headers.setLocation(java.net.URI.create("/"))

        // Clear the attributes so they don't persist
        exchange.attributes.remove("jwtIssueEmail")
        exchange.attributes.remove("jwtIssueWasRefresh")

        return exchange.response.setComplete()
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
