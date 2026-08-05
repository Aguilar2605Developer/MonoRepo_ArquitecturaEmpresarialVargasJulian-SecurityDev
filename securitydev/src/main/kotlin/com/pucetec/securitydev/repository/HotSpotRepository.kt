package com.pucetec.securitydev.repository

import com.pucetec.securitydev.entity.HotSpot
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository
import java.time.LocalDateTime

@Repository
interface HotSpotRepository : JpaRepository<HotSpot, Long> {
    fun findByActiveTrue(): List<HotSpot>
    fun findByActiveTrueAndExpiresAtBefore(dateTime: LocalDateTime): List<HotSpot>
}