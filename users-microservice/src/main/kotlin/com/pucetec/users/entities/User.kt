package com.pucetec.users.entities

import jakarta.persistence.*

@Entity
@Table(name = "users")
class User(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0L,

    @Column(unique = true, nullable = false)
    val cognitoId: String = "",  // identidad real: el "sub" del JWT de Cognito

    val name: String = "",
    val email: String? = null,
    val phone: String? = null
)