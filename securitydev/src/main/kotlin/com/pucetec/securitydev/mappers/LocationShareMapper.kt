package com.pucetec.securitydev.mappers

import com.pucetec.securitydev.dto.LocationShareRequest
import com.pucetec.securitydev.dto.LocationShareResponse
import com.pucetec.securitydev.entity.LocationShare
import org.springframework.stereotype.Component

// Traduce entre el DTO y la entity LocationShare. Más simple que
// HotSpotMapper porque acá no hay dos tablas, solo una.
@Component
class LocationShareMapper {

    // DTO -> entity. shareId (UUID) y expiresAt (10 min) quedan con sus
    // valores por defecto definidos en la propia entity, no se tocan aquí.
    fun toEntity(request: LocationShareRequest, ownerCognitoId: String?): LocationShare {
        return LocationShare(
            latitude = request.latitude,
            longitude = request.longitude,
            ownerCognitoId = ownerCognitoId
        )
    }

    // Entity -> DTO de salida, mapeo 1 a 1 sin lógica extra.
    fun toResponse(locationShare: LocationShare): LocationShareResponse {
        return LocationShareResponse(
            shareId = locationShare.shareId,
            latitude = locationShare.latitude,
            longitude = locationShare.longitude,
            active = locationShare.active,
            expiresAt = locationShare.expiresAt,
            ownerCognitoId = locationShare.ownerCognitoId
        )
    }
}