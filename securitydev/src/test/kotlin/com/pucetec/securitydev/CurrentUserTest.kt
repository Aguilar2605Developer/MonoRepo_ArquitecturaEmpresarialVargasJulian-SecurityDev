package com.pucetec.securitydev.security

import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.security.oauth2.jwt.Jwt
import java.time.Instant

class CurrentUserTest {

    @AfterEach
    fun limpiar() {
        SecurityContextHolder.clearContext()
    }

    private fun buildJwt(
        sub: String,
        email: String? = null,
        name: String? = null,
        emailVerified: Boolean? = null
    ): Jwt {
        val claims = mutableMapOf<String, Any>("sub" to sub)
        if (email != null) claims["email"] = email
        if (name != null) claims["name"] = name
        if (emailVerified != null) claims["email_verified"] = emailVerified

        return Jwt.withTokenValue("token-de-prueba")
            .header("alg", "RS256")
            .claims { it.putAll(claims) }
            .issuedAt(Instant.now())
            .expiresAt(Instant.now().plusSeconds(3600))
            .build()
    }

    private fun autenticarComo(jwt: Jwt) {
        val auth = UsernamePasswordAuthenticationToken(jwt, null, emptyList())
        SecurityContextHolder.getContext().authentication = auth
    }

    // ---------------------- sub ----------------------

    @Test
    fun `sub deberia devolver null cuando no hay autenticacion`() {
        SecurityContextHolder.clearContext()
        assertNull(CurrentUser.sub())
    }

    @Test
    fun `sub deberia devolver null cuando el principal no es un Jwt`() {
        val auth = UsernamePasswordAuthenticationToken("test@example.com", null, emptyList())
        SecurityContextHolder.getContext().authentication = auth

        assertNull(CurrentUser.sub())
    }

    @Test
    fun `sub deberia devolver el subject del Jwt`() {
        autenticarComo(buildJwt(sub = "cognito-sub-123"))

        assertEquals("cognito-sub-123", CurrentUser.sub())
    }

    // ---------------------- email ----------------------

    @Test
    fun `email deberia devolver el claim email del Jwt`() {
        autenticarComo(buildJwt(sub = "cognito-sub-123", email = "test@example.com"))

        assertEquals("test@example.com", CurrentUser.email())
    }

    @Test
    fun `email deberia devolver null cuando el principal no es un Jwt`() {
        val auth = UsernamePasswordAuthenticationToken("test@example.com", null, emptyList())
        SecurityContextHolder.getContext().authentication = auth

        assertNull(CurrentUser.email())
    }

    @Test
    fun `email deberia devolver null cuando el Jwt no trae el claim email`() {
        autenticarComo(buildJwt(sub = "cognito-sub-123"))

        assertNull(CurrentUser.email())
    }

    // ---------------------- name ----------------------

    @Test
    fun `name deberia devolver el claim name del Jwt`() {
        autenticarComo(buildJwt(sub = "cognito-sub-123", name = "Test User"))

        assertEquals("Test User", CurrentUser.name())
    }

    @Test
    fun `name deberia devolver null cuando no hay autenticacion`() {
        SecurityContextHolder.clearContext()
        assertNull(CurrentUser.name())
    }

    // ---------------------- emailVerified ----------------------

    @Test
    fun `emailVerified deberia devolver true cuando el claim es true`() {
        autenticarComo(buildJwt(sub = "cognito-sub-123", emailVerified = true))

        assertTrue(CurrentUser.emailVerified())
    }

    @Test
    fun `emailVerified deberia devolver false cuando el claim es false`() {
        autenticarComo(buildJwt(sub = "cognito-sub-123", emailVerified = false))

        assertFalse(CurrentUser.emailVerified())
    }

    @Test
    fun `emailVerified deberia devolver false cuando el Jwt no trae el claim`() {
        autenticarComo(buildJwt(sub = "cognito-sub-123"))

        assertFalse(CurrentUser.emailVerified())
    }

    @Test
    fun `emailVerified deberia devolver false cuando no hay autenticacion`() {
        SecurityContextHolder.clearContext()
        assertFalse(CurrentUser.emailVerified())
    }

    @Test
    fun `emailVerified deberia devolver false cuando el principal no es un Jwt`() {
        val auth = UsernamePasswordAuthenticationToken("test@example.com", null, emptyList())
        SecurityContextHolder.getContext().authentication = auth

        assertFalse(CurrentUser.emailVerified())
    }
}