package com.pucetec.securitydev.dto

data class DashboardResponse(
    val totalUsers: Int,
    val activeHotspotsTotal: Int,
    val hotspotsByModality: Map<String, Int>,
    val activeShares: Int
)