package com.pucetec.users.security

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.HttpMethod
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity
import org.springframework.security.config.http.SessionCreationPolicy
import org.slf4j.MDC
import org.springframework.security.core.GrantedAuthority
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter
import org.springframework.security.web.SecurityFilterChain
import org.springframework.web.cors.CorsConfiguration
import org.springframework.web.cors.CorsConfigurationSource
import org.springframework.web.cors.UrlBasedCorsConfigurationSource

// Comparte el mismo User Pool de Cognito que securitydev (mismo issuer-uri),
// asi que el claim "cognito:groups" ya trae el grupo ADMIN cuando aplica.
// Se replica el mismo mapeo grupo -> ROLE_X para no crear un segundo
// concepto de rol distinto entre microservicios.
//
// Regla de autorizacion: "/me" (crear/ver/editar el PROPIO perfil) sigue
// abierto a cualquier usuario autenticado -- eso ya es seguro porque el
// service usa jwt.subject, nunca un id que venga del cliente. Pero listar
// TODOS los usuarios, consultar el perfil de OTRO por id/cognitoId, borrar
// una cuenta ajena, o disparar el sync masivo desde Cognito, ahora exige
// rol ADMIN.
@Configuration
@EnableWebSecurity
class SecurityConfig {

    // Sin esto, el navegador bloquea el preflight OPTIONS antes de que la
    // request real llegue al controller (falta Access-Control-Allow-Origin).
    // Misma config que securitydev, para que el frontend pueda hablar con
    // ambos backends desde el mismo origen.
    @Bean
    fun corsConfigurationSource(): CorsConfigurationSource {
        val config = CorsConfiguration()
        config.allowedOrigins = listOf("*")
        config.allowedMethods = listOf("GET", "POST", "PUT", "DELETE", "OPTIONS")
        config.allowedHeaders = listOf("*")
        val source = UrlBasedCorsConfigurationSource()
        source.registerCorsConfiguration("/**", config)
        return source
    }

    // Idéntico al de securitydev: mapea cognito:groups -> ROLE_X.
    @Bean
    fun jwtAuthenticationConverter(): JwtAuthenticationConverter {
        val converter = JwtAuthenticationConverter()
        converter.setJwtGrantedAuthoritiesConverter { jwt: Jwt ->
            // Deja "sub" en el MDC para que todo log del request incluya
            // sub=<claim> segun el formato de logback-spring.xml. Se limpia
            // en MdcCleanupFilter, que envuelve toda la cadena de filtros.
            MDC.put("sub", jwt.subject)
            val groups: List<String> = jwt.getClaimAsStringList("cognito:groups") ?: emptyList()
            groups.map { SimpleGrantedAuthority("ROLE_$it") } as List<GrantedAuthority>
        }
        return converter
    }

    @Bean
    fun securityFilterChain(http: HttpSecurity): SecurityFilterChain {
        http
            .cors { it.configurationSource(corsConfigurationSource()) }
            .csrf { it.disable() }
            .sessionManagement { it.sessionCreationPolicy(SessionCreationPolicy.STATELESS) }
            .authorizeHttpRequests {
                // "/me" (crear/ver/editar mi propio perfil): cualquier usuario logueado.
                it.requestMatchers(HttpMethod.POST, "/api/users/me").authenticated()
                it.requestMatchers(HttpMethod.GET, "/api/users/me").authenticated()
                it.requestMatchers(HttpMethod.PUT, "/api/users/me").authenticated()

                // Sync masivo desde Cognito (llamado por securitydev): solo ADMIN.
                // IMPORTANTE: debe declararse ANTES de cualquier matcher mas
                // generico tipo "/api/users/*" o del anyRequest() del final,
                // porque Spring Security aplica la PRIMERA regla que matchea.
                it.requestMatchers(HttpMethod.POST, "/api/users/admin/sync-from-cognito").hasRole("ADMIN")

                // Ver perfil de OTRO por cognitoId: solo ADMIN.
                it.requestMatchers(HttpMethod.GET, "/api/users/cognito/**").hasRole("ADMIN")

                // Listar todos / ver por id numérico: solo ADMIN.
                it.requestMatchers(HttpMethod.GET, "/api/users", "/api/users/*").hasRole("ADMIN")

                // Borrar cualquier cuenta: solo ADMIN.
                it.requestMatchers(HttpMethod.DELETE, "/api/users/*").hasRole("ADMIN")

                // Cualquier otra ruta: solo exige estar autenticado.
                it.anyRequest().authenticated()
            }
            .oauth2ResourceServer { oauth2 ->
                oauth2.jwt { jwt -> jwt.jwtAuthenticationConverter(jwtAuthenticationConverter()) }
            }

        return http.build()
    }
}