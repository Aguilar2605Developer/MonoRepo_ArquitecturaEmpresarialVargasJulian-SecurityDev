package com.pucetec.securitydev.service

import com.pucetec.securitydev.dto.HotSpotRequest
import com.pucetec.securitydev.dto.HotSpotResponse
import com.pucetec.securitydev.entity.HotSpot
import com.pucetec.securitydev.entity.HotSpotReport
import com.pucetec.securitydev.exceptions.HotSpotNotFoundException
import com.pucetec.securitydev.mappers.HotSpotMapper
import com.pucetec.securitydev.repository.HotSpotReportRepository
import com.pucetec.securitydev.repository.HotSpotRepository
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime
import java.time.ZoneOffset

@Service
class HotSpotService(
    private val hotSpotRepository: HotSpotRepository,
    private val hotSpotReportRepository: HotSpotReportRepository,
    private val hotSpotMapper: HotSpotMapper
) {

    fun createHotSpot(request: HotSpotRequest, reporterCognitoId: String?): HotSpotResponse {
        val savedHotSpot = hotSpotRepository.save(hotSpotMapper.toHotSpotEntity(request))
        val savedReport = hotSpotReportRepository.save(
            hotSpotMapper.toReportEntity(request, savedHotSpot, reporterCognitoId)
        )
        return hotSpotMapper.toResponse(savedHotSpot, savedReport)
    }

    fun getAllHotSpots(): List<HotSpotResponse> {
        val hotSpots = hotSpotRepository.findByActiveTrue()
        val reports = latestReportsByHotSpotId(hotSpots.map { it.id })
        return hotSpots.map { hotSpotMapper.toResponse(it, reports[it.id]) }
    }

    fun getAllHotSpotsAdmin(): List<HotSpotResponse> {
        val hotSpots = hotSpotRepository.findAll()
        val reports = latestReportsByHotSpotId(hotSpots.map { it.id })
        return hotSpots.map { hotSpotMapper.toResponse(it, reports[it.id]) }
    }

    fun getHotSpotById(id: Long): HotSpotResponse {
        val hotSpot = hotSpotRepository.findById(id).orElseThrow {
            HotSpotNotFoundException("Punto de peligro no encontrado con ID: $id")
        }
        val report = hotSpotReportRepository.findByHotSpotId(id).lastOrNull()
        return hotSpotMapper.toResponse(hotSpot, report)
    }

    fun updateHotSpot(id: Long, request: HotSpotRequest, reporterCognitoId: String?): HotSpotResponse {
        if (!hotSpotRepository.existsById(id)) {
            throw HotSpotNotFoundException("Punto de peligro no encontrado con ID: $id")
        }

        if (!reporterCognitoId.isNullOrBlank()) {
            val reportesExistentes = hotSpotReportRepository.countByReporterCognitoIdAndHotSpotId(reporterCognitoId, id)
            if (reportesExistentes >= 5) {
                throw IllegalArgumentException("Has alcanzado el limite maximo de 5 reportes para este punto.")
            }
        }

        val updatedHotSpot = hotSpotRepository.save(hotSpotMapper.toHotSpotEntity(request, id))

        val existingReportId = hotSpotReportRepository.findByHotSpotId(id).lastOrNull()?.id ?: 0L
        val updatedReport = hotSpotReportRepository.save(
            hotSpotMapper.toReportEntity(request, updatedHotSpot, reporterCognitoId, existingReportId)
        )
        return hotSpotMapper.toResponse(updatedHotSpot, updatedReport)
    }

    fun deactivateHotSpot(id: Long): HotSpotResponse {
        val hotSpot = hotSpotRepository.findById(id).orElseThrow {
            HotSpotNotFoundException("Punto de peligro no encontrado con ID: $id")
        }
        val deactivated = HotSpot(
            id = hotSpot.id,
            latitude = hotSpot.latitude,
            longitude = hotSpot.longitude,
            active = false,
            expiresAt = hotSpot.expiresAt
        )
        val saved = hotSpotRepository.save(deactivated)
        val report = hotSpotReportRepository.findByHotSpotId(id).lastOrNull()
        return hotSpotMapper.toResponse(saved, report)
    }

    @Transactional
    fun deleteHotSpot(id: Long) {
        if (!hotSpotRepository.existsById(id)) {
            throw HotSpotNotFoundException("Punto de peligro no encontrado con ID: $id")
        }
        hotSpotReportRepository.deleteByHotSpotId(id)
        hotSpotRepository.deleteById(id)
    }

    @Scheduled(fixedRate = 60000)
    fun deactivateExpiredHotSpots() {
        val expired = hotSpotRepository.findByActiveTrueAndExpiresAtBefore(LocalDateTime.now(ZoneOffset.UTC))
        expired.forEach {
            hotSpotRepository.save(
                HotSpot(
                    id = it.id,
                    latitude = it.latitude,
                    longitude = it.longitude,
                    active = false,
                    expiresAt = it.expiresAt
                )
            )
        }
    }

    private fun latestReportsByHotSpotId(hotSpotIds: List<Long>): Map<Long, HotSpotReport> {
        if (hotSpotIds.isEmpty()) return emptyMap()
        return hotSpotReportRepository.findByHotSpotIdIn(hotSpotIds)
            .groupBy { it.hotSpot?.id }
            .mapNotNull { (hotSpotId, reports) -> hotSpotId?.let { it to reports.last() } }
            .toMap()
    }
}