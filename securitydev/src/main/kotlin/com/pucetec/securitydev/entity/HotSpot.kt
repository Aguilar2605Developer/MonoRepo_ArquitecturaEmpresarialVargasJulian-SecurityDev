package com.pucetec.securitydev.entity

import jakarta.persistence.*
import java.time.LocalDateTime
import java.time.ZoneOffset

// Tabla "hotspot": el punto de peligro en sí (ubicación + estado).
@Entity
@Table(name = "hotspot")
data class HotSpot(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0L,

    val latitude: Double = 0.0,
    val longitude: Double = 0.0,
    val active: Boolean = true,
    val expiresAt: LocalDateTime = LocalDateTime.now(ZoneOffset.UTC).plusHours(24)
)