package com.pucetec.securitydev.dto

import java.time.LocalDateTime


data class HotSpotResponse(
    val id: Long,
    val latitude: Double,
    val longitude: Double,
    val modality: String,
    val description: String,
    val reporterCognitoId: String?,
    val active: Boolean,
    val expiresAt: LocalDateTime,
    val peopleInvolved: Int
)