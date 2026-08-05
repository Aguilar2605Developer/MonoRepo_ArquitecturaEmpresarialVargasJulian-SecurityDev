package com.pucetec.securitydev.dto

// Devuelve el id local + nombre tras el get-or-create del roster.
data class SyncResponse(
    val id: Long,
    val name: String
)