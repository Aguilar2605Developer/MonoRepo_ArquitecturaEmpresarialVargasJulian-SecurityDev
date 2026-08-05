package com.pucetec.users.dto

data class CognitoSyncUser(
    val cognitoId: String,
    val email: String?,
    val name: String,
    val phone: String? = null
)

data class CognitoSyncRequest(
    val users: List<CognitoSyncUser>
)

data class CognitoSyncResponse(
    val totalRecibidos: Int,
    val creados: Int,
    val actualizados: Int
)