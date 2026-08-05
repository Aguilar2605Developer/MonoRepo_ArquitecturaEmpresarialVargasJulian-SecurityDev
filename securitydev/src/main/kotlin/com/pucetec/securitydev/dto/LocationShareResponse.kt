package com.pucetec.securitydev.dto

import java.time.LocalDateTime

// Estado actual de un share de ubicación.
class LocationShareResponse(
    val shareId: String,          // identificador público usado en la URL
    val latitude: Double,
    val longitude: Double,
    val active: Boolean,
    val expiresAt: LocalDateTime,
    val ownerCognitoId: String? = null  // usado por el controller para validar dueño vs. destinatario
)