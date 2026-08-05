package com.pucetec.securitydev.dto

// Espejo EXACTO del contrato de users-microservice (CognitoSyncRequest /
// CognitoSyncUser / CognitoSyncResponse en su paquete dto). Los nombres de
// campo deben coincidir tal cual porque Jackson serializa por nombre al
// hacer el POST hacia el otro microservicio.
data class UsersServiceSyncUser(
    val cognitoId: String,
    val email: String?,
    val name: String,
    val phone: String? = null
)

data class UsersServiceSyncRequest(
    val users: List<UsersServiceSyncUser>
)

data class UsersServiceSyncResponse(
    val totalRecibidos: Int,
    val creados: Int,
    val actualizados: Int
)