package com.pucetec.securitydev.entity

import jakarta.persistence.*

// Ya NO tiene relación con HotSpotReport ni LocationShare
// Esta tabla es exclusivamente el roster que se usa de soporte visual para el
// panel de administración — no la fuente de verdad del perfil,
// que ahora vive en users-microservice.
//
// IMPORTANTE: no se llama "users" ni la clase ni la tabla, a propósito:
// users-microservice es dueño de esa tabla/concepto. Esta es solo un
// roster local para el panel admin.
@Entity
@Table(name = "admin_roster_users")
class AdminRosterUser(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0L,

    @Column(unique = true, nullable = true)
    val cognitoSub: String? = null,

    val name: String = "",
    val email: String = "",
    val number: String = ""
)