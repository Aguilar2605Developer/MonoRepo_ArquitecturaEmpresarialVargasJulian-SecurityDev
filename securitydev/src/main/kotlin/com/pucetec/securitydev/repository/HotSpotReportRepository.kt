package com.pucetec.securitydev.repository

import com.pucetec.securitydev.entity.HotSpotReport
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository

@Repository
interface HotSpotReportRepository : JpaRepository<HotSpotReport, Long> {

    fun findByHotSpotId(hotSpotId: Long): List<HotSpotReport>

    fun findByHotSpotIdIn(hotSpotIds: List<Long>): List<HotSpotReport>

    fun deleteByHotSpotId(hotSpotId: Long)

    // Necesario para AdminService
    fun countByReporterCognitoId(reporterCognitoId: String): Long

    // Necesario para la validación de los 5 reportes en HotSpotService
    fun countByReporterCognitoIdAndHotSpotId(reporterCognitoId: String, hotSpotId: Long): Long
}