package com.mosta.coveringletter.security

import com.mosta.coveringletter.activity.UserActionLogBuffer
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.security.config.web.server.ServerHttpSecurity
import org.springframework.security.core.Authentication
import org.springframework.security.core.AuthenticationException
import org.springframework.security.web.server.SecurityWebFilterChain
import org.springframework.security.web.server.ServerAuthenticationEntryPoint
import org.springframework.security.web.server.WebFilterExchange
import org.springframework.security.web.server.authentication.ServerAuthenticationSuccessHandler
import org.springframework.security.web.server.authentication.ServerAuthenticationFailureHandler
import org.springframework.web.server.ServerWebExchange
import reactor.core.publisher.Mono

@Configuration
class SecurityConfig(
    private val userActions: UserActionLogBuffer
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
                    "/login/oauth2/code/*"
                ).permitAll()
                auth.anyExchange().authenticated()
            }
            .httpBasic { it.disable() }
            .oauth2Login { oauth ->
                oauth.loginPage("/login.html")
                oauth.authenticationSuccessHandler(LoginSuccessHandler(allowedEmails))
                oauth.authenticationFailureHandler(LoginFailureHandler())
            }
            .logout { logout ->
                logout.logoutUrl("/logout")
            }
        return http.build()
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

    inner class LoginSuccessHandler(private val allowedEmails: Set<String>) : ServerAuthenticationSuccessHandler {
        override fun onAuthenticationSuccess(exchange: WebFilterExchange, authentication: Authentication): Mono<Void> {
            val name = authentication.name.lowercase()
            val targetUrl = if (allowedEmails.contains(name)) {
                val wasRefresh = exchange.exchange.request.queryParams["refresh"]?.firstOrNull() == "true"
                if (wasRefresh) {
                    userActions.record("Session refreshed via Google Sign-In")
                } else {
                    userActions.record("Signed in with Google: $name")
                }
                "/"
            } else {
                "/login.html?error=access_denied"
            }
            exchange.exchange.response.statusCode = HttpStatus.FOUND
            exchange.exchange.response.headers.setLocation(java.net.URI.create(targetUrl))
            return exchange.exchange.response.setComplete()
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
