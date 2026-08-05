package com.pucetec.securitydev.logging

import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.slf4j.MDC
import org.springframework.core.Ordered
import org.springframework.core.annotation.Order
import org.springframework.stereotype.Component
import org.springframework.web.filter.OncePerRequestFilter

/**
 * Wraps every request so that any MDC value set further down the chain
 * (in particular "sub", set in SecurityConfig's jwtAuthenticationConverter
 * once the JWT is validated) is always cleared afterwards. Registered with
 * HIGHEST_PRECEDENCE so it wraps the whole Spring Security filter chain,
 * not just the controller.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
class MdcCleanupFilter : OncePerRequestFilter() {

    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        filterChain: FilterChain
    ) {
        try {
            filterChain.doFilter(request, response)
        } finally {
            MDC.clear()
        }
    }
}
