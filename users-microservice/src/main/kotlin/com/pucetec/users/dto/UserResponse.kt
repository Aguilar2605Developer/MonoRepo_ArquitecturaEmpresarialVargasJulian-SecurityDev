package com.pucetec.users.dto

// Respuesta de todos los endpoints.
data class UserResponse(
    val id: Long,
    val cognitoId: String,
    val name: String,
    val email: String?,
    val phone: String?
)