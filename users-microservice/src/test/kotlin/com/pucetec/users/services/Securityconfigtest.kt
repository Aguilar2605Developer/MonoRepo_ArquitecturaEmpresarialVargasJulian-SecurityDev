package com.pucetec.users.security

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.springframework.security.oauth2.jwt.Jwt
import java.time.Instant

class SecurityConfigTest {

    private fun buildJwt(groups: List<String>? = null): Jwt {
        val claims = mutableMapOf<String, Any>("sub" to "cognito-sub-1")
        if (groups != null) claims["cognito:groups"] = groups

        return Jwt.withTokenValue("token-de-prueba")
            .header("alg", "RS256")
            .claims { it.putAll(claims) }
            .issuedAt(Instant.now())
            .expiresAt(Instant.now().plusSeconds(3600))
            .build()
    }

    @Test
    fun `deberia mapear el grupo ADMIN de Cognito a ROLE_ADMIN`() {
        val converter = SecurityConfig().jwtAuthenticationConverter()

        val authorities = converter.convert(buildJwt(groups = listOf("ADMIN")))!!.authorities

        assertTrue(authorities.any { it.authority == "ROLE_ADMIN" })
    }

    @Test
    fun `deberia mapear varios grupos a varios roles`() {
        val converter = SecurityConfig().jwtAuthenticationConverter()

        val authorities = converter.convert(buildJwt(groups = listOf("ADMIN", "USER")))!!.authorities

        assertEquals(2, authorities.size)
        assertTrue(authorities.any { it.authority == "ROLE_ADMIN" })
        assertTrue(authorities.any { it.authority == "ROLE_USER" })
    }

    @Test
    fun `deberia devolver sin autoridades si el token no trae cognito groups`() {
        val converter = SecurityConfig().jwtAuthenticationConverter()

        val authorities = converter.convert(buildJwt(groups = null))!!.authorities

        assertTrue(authorities.isEmpty())
    }
}