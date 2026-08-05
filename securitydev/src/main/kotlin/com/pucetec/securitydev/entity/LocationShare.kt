package com.pucetec.securitydev.entity

import jakarta.persistence.*
import java.time.LocalDateTime
import java.util.UUID


// El shareId (UUID) es el identificador público que se usa en la URL/link
// que se comparte con otras personas.
@Entity
@Table(name = "location_share")
class LocationShare(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0L,

    @Column(unique = true, nullable = false)
    val shareId: String = UUID.randomUUID().toString(),

    val latitude: Double = 0.0,
    val longitude: Double = 0.0,

    val active: Boolean = true,

    val expiresAt: LocalDateTime = LocalDateTime.now().plusMinutes(10), // vida corta por defecto (10 min)

    // Igual que en HotSpotReport: solo el cognitoId del dueño
    @Column(name = "owner_cognito_id", nullable = true)
    val ownerCognitoId: String? = null
)