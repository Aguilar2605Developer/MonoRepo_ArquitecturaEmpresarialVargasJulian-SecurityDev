package com.pucetec.securitydev.security

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

@Configuration
@EnableWebSecurity
class SecurityConfig {



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

    // Convierte el claim "cognito:groups" del JWT (ej. ["ADMIN"]) en
    // authorities de Spring Security con prefijo ROLE_ (ej. "ROLE_ADMIN").
    // Esto es lo que permite usar hasRole("ADMIN") más abajo.
    @Bean
    fun jwtAuthenticationConverter(): JwtAuthenticationConverter {
        val converter = JwtAuthenticationConverter()
        converter.setJwtGrantedAuthoritiesConverter { jwt: Jwt ->
            // Se aprovecha este punto (se ejecuta una vez por request, ya con
            // el JWT validado) para dejar "sub" en el MDC. Todo log emitido
            // desde aquí en adelante en el mismo hilo incluye sub=<claim>
            // en el formato de logback (ver logback-spring.xml). Se limpia
            // en MdcCleanupFilter, que envuelve toda la cadena de filtros.
            MDC.put("sub", jwt.subject)
            val groups: List<String> = jwt.getClaimAsStringList("cognito:groups") ?: emptyList()
            groups.map { SimpleGrantedAuthority("ROLE_$it") } as List<GrantedAuthority>
        }
        return converter
    }

    // La cadena de filtros real: define qué está permitido sin login,
    // qué requiere rol ADMIN, y que todo lo demás requiere JWT válido.
    @Bean
    fun securityFilterChain(http: HttpSecurity): SecurityFilterChain {
        http
            .cors { it.configurationSource(corsConfigurationSource()) }
            .csrf { it.disable() }  // no aplica CSRF: API stateless con JWT, no cookies de sesión
            .sessionManagement { it.sessionCreationPolicy(SessionCreationPolicy.STATELESS) } // sin sesión en servidor, cada request se autentica sola vía el JWT
            .authorizeHttpRequests {
                // Health checks: públicos, sin excepción.
                it.requestMatchers(HttpMethod.GET, "/api/health", "/api/health/**").permitAll()

                // Lectura de hotspots: pública (mapa de peligros visible sin login).
                it.requestMatchers(HttpMethod.GET, "/api/hotspots", "/api/hotspots/**").permitAll()

                // Los 3 pasos de registro/confirmación: públicos por definición
                // (todavía no existe una sesión/token para el usuario).
                it.requestMatchers(
                    HttpMethod.POST,
                    "/api/users/register",
                    "/api/users/confirm",
                    "/api/users/resend-code"
                ).permitAll()

                // location-shares deja de ser publico: la autorizacion se decide
                // comparando el email del token contra la tabla de destinatarios.
                // (nota del propio autor: NO hay un requestMatchers explícito
                // para /api/location-shares/** aquí; caen en anyRequest().authenticated()
                // de abajo, y el detalle fino de "dueño vs destinatario" lo resuelve
                // el propio LocationShareController, no esta clase)

                // Todo /api/admin/** exige el rol ADMIN (viene del grupo Cognito).
                it.requestMatchers("/api/admin/**").hasRole("ADMIN")

                // Cualquier otra ruta no listada arriba: solo exige JWT válido
                // (sin rol específico). Aquí caen /api/users/sync, /api/hotspots (POST/PUT/DELETE),
                // y todo /api/location-shares/**.
                it.anyRequest().authenticated()
            }
            .oauth2ResourceServer { oauth2 ->
                // Habilita la validación del JWT contra el issuer-uri de Cognito
                // (configurado en application.yaml) + aplica el converter de roles de arriba.
                oauth2.jwt { jwt -> jwt.jwtAuthenticationConverter(jwtAuthenticationConverter()) }
            }

        return http.build()
    }
}