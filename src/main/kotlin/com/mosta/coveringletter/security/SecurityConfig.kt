package com.mosta.coveringletter.security

import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.HttpStatus
import org.springframework.security.config.web.server.ServerHttpSecurity
import org.springframework.security.core.Authentication
import org.springframework.security.core.AuthenticationException
import org.springframework.security.oauth2.client.registration.ClientRegistration
import org.springframework.security.oauth2.client.registration.InMemoryReactiveClientRegistrationRepository
import org.springframework.security.oauth2.core.AuthorizationGrantType
import org.springframework.security.oauth2.core.ClientAuthenticationMethod
import org.springframework.security.web.server.SecurityWebFilterChain
import org.springframework.security.web.server.WebFilterExchange
import org.springframework.security.web.server.authentication.ServerAuthenticationSuccessHandler
import org.springframework.security.web.server.authentication.ServerAuthenticationFailureHandler
import reactor.core.publisher.Mono

@Configuration
class SecurityConfig(
    @Value("\${coverletter.security.allowed-emails:info@manonworld.de}")
    private val allowedEmailsProperty: String,
    @Value("\${spring.security.oauth2.client.registration.google.client-id:}")
    private val clientId: String,
    @Value("\${spring.security.oauth2.client.registration.google.client-secret:}")
    private val clientSecret: String
) {

    private val allowedEmails: Set<String> = allowedEmailsProperty.split(",").map { it.trim().lowercase() }.toSet()

    @Bean
    fun clientRegistrationRepository(): InMemoryReactiveClientRegistrationRepository {
        val registration = ClientRegistration.withRegistrationId("google")
            .clientId(clientId)
            .clientSecret(clientSecret)
            .clientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_POST)
            .authorizationUri("https://accounts.google.com/o/oauth2/v2/auth")
            .tokenUri("https://oauth2.googleapis.com/token")
            .userInfoUri("https://www.googleapis.com/oauth2/v3/userinfo")
            .userNameAttributeName("email")
            .jwkSetUri("https://www.googleapis.com/oauth2/v3/certs")
            .redirectUri("https://coveringletter-971727829810.us-central1.run.app/login/oauth2/code/google")
            .scope("email", "profile", "openid")
            .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
            .build()
        return InMemoryReactiveClientRegistrationRepository(registration)
    }

    @Bean
    fun securityWebFilterChain(http: ServerHttpSecurity): SecurityWebFilterChain {
        http
            .csrf { it.disable() }
            .authorizeExchange { auth ->
                auth.pathMatchers(
                    "/login",
                    "/login.html",
                    "/error",
                    "/favicon.ico",
                    "/login/oauth2/code/*"
                ).permitAll()
                auth.anyExchange().authenticated()
            }
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

    class LoginSuccessHandler(private val allowedEmails: Set<String>) : ServerAuthenticationSuccessHandler {
        override fun onAuthenticationSuccess(exchange: WebFilterExchange, authentication: Authentication): Mono<Void> {
            val name = authentication.name.lowercase()
            val targetUrl = if (allowedEmails.contains(name)) {
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
