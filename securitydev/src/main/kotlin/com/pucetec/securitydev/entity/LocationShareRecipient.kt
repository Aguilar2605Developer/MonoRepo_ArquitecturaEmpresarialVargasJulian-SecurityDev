package com.pucetec.securitydev.entity

import jakarta.persistence.*
import java.time.LocalDateTime

// Tabla "location_share_recipient": lista blanca de emails autorizados a
// VER un LocationShare que no son el dueño. Constraint único evita invitar
// dos veces al mismo email al mismo share.
@Entity
@Table(
    name = "location_share_recipient",
    uniqueConstraints = [UniqueConstraint(columnNames = ["location_share_id", "email"])]
)
class LocationShareRecipient(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0L,

    // FK hacia el share al que este destinatario fue invitado.
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "location_share_id", nullable = false)
    val locationShare: LocationShare? = null,

    // Siempre normalizado en minusculas antes de guardar (ver LocationShareController)
    @Column(nullable = false)
    val email: String = "",

    val addedAt: LocalDateTime = LocalDateTime.now()  // cuándo fue invitado
)