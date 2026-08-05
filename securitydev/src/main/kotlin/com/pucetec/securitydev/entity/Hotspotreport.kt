package com.pucetec.securitydev.entity

import jakarta.persistence.*

// Tabla "hotspot_report": el detalle textual de un reporte sobre un HotSpot.
// Relación 1 HotSpot -> N reportes (permite reportes repetidos/actualizados
// sobre el mismo punto sin perder el historial).
@Entity
@Table(name = "hotspot_report")
data class HotSpotReport(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0L,

    val modality: String = "",       // tipo de incidente
    val description: String = "",
    val peopleInvolved: Int = 1,

    // FK real hacia el HotSpot padre.
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "hotspot_id", nullable = false)
    val hotSpot: HotSpot? = null,


    // (nombre, email, teléfono) se consulta en users-microservice si hace falta.
    @Column(name = "reporter_cognito_id", nullable = true)
    val reporterCognitoId: String? = null
)