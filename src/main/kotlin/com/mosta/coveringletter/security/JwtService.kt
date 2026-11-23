package com.mosta.coveringletter.security

import com.nimbusds.jose.JWSAlgorithm
import com.nimbusds.jose.jwk.JWKSet
import com.nimbusds.jose.jwk.RSAKey
import com.nimbusds.jose.jwk.source.ImmutableJWKSet
import jakarta.annotation.PostConstruct
import org.springframework.beans.factory.annotation.Value
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm
import org.springframework.security.oauth2.jwt.JwsHeader
import org.springframework.security.oauth2.jwt.JwtClaimsSet
import org.springframework.security.oauth2.jwt.JwtDecoder
import org.springframework.security.oauth2.jwt.JwtEncoder
import org.springframework.security.oauth2.jwt.JwtEncoderParameters
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder
import org.springframework.stereotype.Service
import reactor.core.publisher.Mono
import java.security.KeyFactory
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.interfaces.RSAPrivateCrtKey
import java.security.interfaces.RSAPublicKey
import java.security.spec.PKCS8EncodedKeySpec
import java.security.spec.X509EncodedKeySpec
import java.time.Instant
import java.util.Base64

@Service
class JwtService(
    @Value("\${coverletter.jwt.access-token-validity-seconds:900}")
    private val accessTokenValiditySeconds: Long,
    @Value("\${coverletter.jwt.refresh-token-validity-seconds:604800}")
    private val refreshTokenValiditySeconds: Long,
    @Value("\${coverletter.jwt.private-key-base64:}")
    private val privateKeyBase64: String,
    @Value("\${coverletter.jwt.public-key-base64:}")
    private val publicKeyBase64: String
) {

    private lateinit var keyPair: KeyPair

    private fun initializeKeyPair() {
        val hasPrivate = privateKeyBase64.isNotBlank()
        val hasPublic = publicKeyBase64.isNotBlank()
        System.err.println("JwtService INIT: privateKeyBase64 length=${privateKeyBase64.length}, publicKeyBase64 length=${publicKeyBase64.length}")
        System.err.println("JwtService INIT: hasPrivate=$hasPrivate, hasPublic=$hasPublic")
        if (hasPrivate && hasPublic) {
            try {
                keyPair = loadKeyPairFromBase64(privateKeyBase64, publicKeyBase64)
                val pubFingerprint = Base64.getEncoder().encodeToString(keyPair.public.encoded).take(20)
                System.err.println("JwtService INIT: Loaded shared key pair. Public key fingerprint: $pubFingerprint")
            } catch (e: Exception) {
                System.err.println("JwtService INIT: Failed to load shared key pair: ${e.message}. Generating random key.")
                e.printStackTrace()
                keyPair = generateKeyPair()
            }
        } else {
            System.err.println("JwtService INIT: No shared keys configured, generating random key pair")
            keyPair = generateKeyPair()
        }
    }

    @PostConstruct
    fun init() {
        initializeKeyPair()
    }

    private val jwtEncoder: JwtEncoder by lazy { createEncoder() }
    private val jwtDecoder: JwtDecoder by lazy { createDecoder() }

    fun generateAccessToken(email: String): String {
        val now = Instant.now()
        val claims = JwtClaimsSet.builder()
            .subject(email)
            .issuer("coveringletter")
            .issuedAt(now)
            .expiresAt(now.plusSeconds(accessTokenValiditySeconds))
            .claim("email_verified", true)
            .claim("type", "access")
            .build()
        val header = JwsHeader.with(SignatureAlgorithm.RS256).build()
        return jwtEncoder.encode(org.springframework.security.oauth2.jwt.JwtEncoderParameters.from(header, claims)).tokenValue
    }

    fun generateRefreshToken(email: String): String {
        val now = Instant.now()
        val claims = JwtClaimsSet.builder()
            .subject(email)
            .issuer("coveringletter")
            .issuedAt(now)
            .expiresAt(now.plusSeconds(refreshTokenValiditySeconds))
            .claim("email_verified", true)
            .claim("type", "refresh")
            .build()
        val header = JwsHeader.with(SignatureAlgorithm.RS256).build()
        return jwtEncoder.encode(org.springframework.security.oauth2.jwt.JwtEncoderParameters.from(header, claims)).tokenValue
    }

    fun validateAccessToken(token: String): Mono<String> =
        validateToken(token, "access")

    fun validateRefreshToken(token: String): Mono<String> =
        validateToken(token, "refresh")

    private fun validateToken(token: String, expectedType: String): Mono<String> =
        Mono.fromCallable {
            val jwt = jwtDecoder.decode(token)
            // Validate expiry manually - NimbusJwtDecoder withPublicKey() doesn't validate by default
            if (jwt.expiresAt != null && Instant.now().isAfter(jwt.expiresAt)) {
                throw IllegalArgumentException("Token expired")
            }
            val type = jwt.claims["type"] as? String
            if (type != expectedType) {
                throw IllegalArgumentException("Invalid token type")
            }
            jwt.subject
        }.onErrorMap { e ->
            if (e is org.springframework.security.oauth2.jwt.JwtException || e is IllegalArgumentException) {
                e
            } else {
                IllegalArgumentException("Invalid token", e)
            }
        }.onErrorResume { Mono.empty() }

    private fun loadKeyPairFromBase64(privateKeyB64: String, publicKeyB64: String): KeyPair {
        val kf = KeyFactory.getInstance("RSA")
        val decoder = Base64.getMimeDecoder()
        // Strip any whitespace that might be embedded in the env var value
        val cleanPrivateKey = privateKeyB64.replace(Regex("\\s"), "")
        val cleanPublicKey = publicKeyB64.replace(Regex("\\s"), "")
        val privateKey = kf.generatePrivate(
            PKCS8EncodedKeySpec(decoder.decode(cleanPrivateKey))
        ) as RSAPrivateCrtKey
        val publicKey = kf.generatePublic(
            X509EncodedKeySpec(decoder.decode(cleanPublicKey))
        ) as RSAPublicKey
        return KeyPair(publicKey, privateKey)
    }

    private fun generateKeyPair(): KeyPair {
        val keyPairGenerator = KeyPairGenerator.getInstance("RSA")
        keyPairGenerator.initialize(2048)
        return keyPairGenerator.generateKeyPair()
    }

    private fun createEncoder(): JwtEncoder {
        val rsaKey = RSAKey.Builder(keyPair.public as RSAPublicKey)
            .privateKey(keyPair.private as RSAPrivateCrtKey)
            .algorithm(JWSAlgorithm.RS256)
            .keyID("coveringletter-key")
            .build()
        return NimbusJwtEncoder(ImmutableJWKSet(JWKSet(rsaKey)))
    }

    private fun createDecoder(): JwtDecoder {
        return NimbusJwtDecoder.withPublicKey(keyPair.public as RSAPublicKey).build()
    }
}
