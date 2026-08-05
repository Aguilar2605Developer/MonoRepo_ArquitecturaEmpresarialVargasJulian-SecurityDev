package com.pucetec.securitydev.dto

// Vista de un usuario para el panel admin, incluye conteo de actividad.
data class UserAdminResponse(
    val id: Long,
    val name: String,
    val email: String,
    val number: String,
    val hotspotsCount: Int  // cuántos hotspots ha reportado este usuario
)