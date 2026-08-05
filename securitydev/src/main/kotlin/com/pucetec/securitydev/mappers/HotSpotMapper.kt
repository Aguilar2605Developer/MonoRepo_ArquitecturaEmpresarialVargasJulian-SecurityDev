package com.pucetec.securitydev.mappers

import com.pucetec.securitydev.dto.HotSpotRequest
import com.pucetec.securitydev.dto.HotSpotResponse
import com.pucetec.securitydev.entity.HotSpot
import com.pucetec.securitydev.entity.HotSpotReport
import org.springframework.stereotype.Component
import java.time.LocalDateTime

// Traduce entre el DTO de entrada/salida y las DOS entities (HotSpot +
// HotSpotReport) que juntas forman el concepto de "punto de peligro".
@Component
class HotSpotMapper {

    // DTO -> entity HotSpot. Calcula expiresAt sumando las horas de
    // duración pedidas por el usuario a la hora actual.
    fun toHotSpotEntity(request: HotSpotRequest, id: Long = 0L): HotSpot {
        return HotSpot(
            id = id,
            latitude = request.latitude,
            longitude = request.longitude,
            active = true,
            expiresAt = LocalDateTime.now().plusHours(request.durationHours)
        )
    }

    // DTO -> entity HotSpotReport. Necesita el HotSpot ya guardado (para
    // la FK) y el cognitoId de quien reporta.
    fun toReportEntity(
        request: HotSpotRequest,
        hotSpot: HotSpot,
        reporterCognitoId: String?,
        id: Long = 0L
    ): HotSpotReport {
        return HotSpotReport(
            id = id,
            modality = request.modality,
            description = request.description,
            peopleInvolved = request.peopleInvolved,
            hotSpot = hotSpot,
            reporterCognitoId = reporterCognitoId
        )
    }

    // Entities (HotSpot + su último reporte) -> DTO de salida.
    // El "?:" cubre el caso raro de un hotspot sin ningún reporte asociado.
    fun toResponse(hotSpot: HotSpot, report: HotSpotReport?): HotSpotResponse {
        return HotSpotResponse(
            id = hotSpot.id,
            latitude = hotSpot.latitude,
            longitude = hotSpot.longitude,
            modality = report?.modality ?: "",
            description = report?.description ?: "",
            reporterCognitoId = report?.reporterCognitoId,
            active = hotSpot.active,
            expiresAt = hotSpot.expiresAt,
            peopleInvolved = report?.peopleInvolved ?: 0
        )
    }
}