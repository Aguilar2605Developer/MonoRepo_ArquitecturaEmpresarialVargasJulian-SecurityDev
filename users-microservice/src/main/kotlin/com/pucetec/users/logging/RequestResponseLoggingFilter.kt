package com.pucetec.users.logging

import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.slf4j.LoggerFactory
import org.springframework.core.Ordered
import org.springframework.core.annotation.Order
import org.springframework.stereotype.Component
import org.springframework.web.filter.OncePerRequestFilter

/**
 * Emite automaticamente una linea event=http.request al entrar cada peticion
 * y una linea event=http.response al salir (con el codigo HTTP y la duracion),
 * sin tener que pegar log.info manualmente en cada controller (Criterio 2d).
 *
 * Se registra con orden HIGHEST_PRECEDENCE + 1, es decir, JUSTO DESPUES de
 * MdcCleanupFilter en la cadena. Esto es importante: MdcCleanupFilter limpia
 * el MDC (incluido "sub") recien en su bloque finally, que se ejecuta cuando
 * este filtro ya termino de loguear event=http.response -- por eso la linea
 * de salida SI alcanza a mostrar el sub= real del usuario autenticado, y no
 * "anonimo", aunque el filtro se ejecute fuera de Spring Security.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 1)
class RequestResponseLoggingFilter : OncePerRequestFilter() {

    // Nombrado "appLogger" (no "logger") a proposito: OncePerRequestFilter
    // hereda un campo protegido "logger" de tipo commons-logging.Log desde
    // GenericFilterBean, y nombrar la propiedad igual generaba ambiguedad
    // de tipos en la compilacion (Kotlin resolvia el Log de Spring, no
    // el Logger de SLF4J, y fallaba al no soportar placeholders {}).
    private val appLogger = LoggerFactory.getLogger(RequestResponseLoggingFilter::class.java)

    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        filterChain: FilterChain
    ) {
        val startedAt = System.currentTimeMillis()
        appLogger.info(
            "event=http.request msg=Incoming request method={} uri={}",
            request.method, request.requestURI
        )
        try {
            filterChain.doFilter(request, response)
        } finally {
            val durationMs = System.currentTimeMillis() - startedAt
            appLogger.info(
                "event=http.response msg=Request completed method={} uri={} status={} durationMs={}",
                request.method, request.requestURI, response.status, durationMs
            )
        }
    }
}