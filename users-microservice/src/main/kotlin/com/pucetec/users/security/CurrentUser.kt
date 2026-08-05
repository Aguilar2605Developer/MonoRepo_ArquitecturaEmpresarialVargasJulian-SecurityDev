package com.pucetec.users.security

import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.security.oauth2.jwt.Jwt

// Espejo minimo del CurrentUser de securitydev. Solo se necesita el "sub"
// del JWT para saber quien ejecuto una accion en el AuditService.
object CurrentUser {

    fun sub(): String? {
        val principal = SecurityContextHolder.getContext().authentication?.principal
        return if (principal is Jwt) principal.subject else null
    }
}