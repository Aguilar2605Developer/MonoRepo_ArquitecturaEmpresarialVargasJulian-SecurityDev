package com.pucetec.securitydev.service

import com.pucetec.securitydev.dto.LocationShareRequest
import com.pucetec.securitydev.dto.LocationShareResponse
import com.pucetec.securitydev.entity.LocationShare
import com.pucetec.securitydev.exceptions.LocationShareNotFoundException
import com.pucetec.securitydev.mappers.LocationShareMapper
import com.pucetec.securitydev.repository.LocationShareRepository
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Service
import java.time.LocalDateTime

// Lógica de negocio del compartir de ubicación en tiempo real.
@Service
class LocationShareService(
    private val locationShareRepository: LocationShareRepository,
    private val locationShareMapper: LocationShareMapper
) {

    // Crea un share nuevo con shareId (UUID) autogenerado en la entity.
    fun startSharing(request: LocationShareRequest, ownerCognitoId: String?): LocationShareResponse {
        val entity = locationShareMapper.toEntity(request, ownerCognitoId)
        val saved = locationShareRepository.save(entity)
        return locationShareMapper.toResponse(saved)
    }

    // Solo actualiza si sigue activo (si ya expiró/paró, tira NotFound).
    fun updateLocation(shareId: String, latitude: Double, longitude: Double): LocationShareResponse {
        val existing = locationShareRepository.findByShareIdAndActiveTrue(shareId)
            ?: throw LocationShareNotFoundException("Compartir ubicación no encontrado o expirado: $shareId")

        val updated = LocationShare(
            id = existing.id,
            shareId = existing.shareId,
            latitude = latitude,
            longitude = longitude,
            active = true,
            expiresAt = existing.expiresAt,
            ownerCognitoId = existing.ownerCognitoId
        )
        return locationShareMapper.toResponse(locationShareRepository.save(updated))
    }

    // Lectura pública/autorizada por shareId (el controller decide quién puede leer).
    fun getByShareId(shareId: String): LocationShareResponse {
        val share = getEntityByShareId(shareId)
        return locationShareMapper.toResponse(share)
    }

    // Igual que arriba pero devuelve la entity cruda (usado internamente,
    // ej. para asociar un LocationShareRecipient en el controller).
    fun getEntityByShareId(shareId: String): LocationShare {
        return locationShareRepository.findByShareId(shareId)
            ?: throw LocationShareNotFoundException("Compartir ubicación no encontrado: $shareId")
    }

    // Detiene el share (active=false), solo si estaba activo.
    fun stopSharing(shareId: String): LocationShareResponse {
        val existing = locationShareRepository.findByShareIdAndActiveTrue(shareId)
            ?: throw LocationShareNotFoundException("Compartir ubicación no encontrado o expirado: $shareId")

        val stopped = LocationShare(
            id = existing.id,
            shareId = existing.shareId,
            latitude = existing.latitude,
            longitude = existing.longitude,
            active = false,
            expiresAt = existing.expiresAt,
            ownerCognitoId = existing.ownerCognitoId
        )
        return locationShareMapper.toResponse(locationShareRepository.save(stopped))
    }

    // Job automático: apaga los shares cuyo expiresAt ya pasó (vida corta,
    // por defecto 10 min desde que se creó, ver entity LocationShare).
    @Scheduled(fixedRate = 60000)
    fun deactivateExpiredShares() {
        val expired = locationShareRepository.findByActiveTrueAndExpiresAtBefore(LocalDateTime.now())
        expired.forEach {
            locationShareRepository.save(
                LocationShare(
                    id = it.id,
                    shareId = it.shareId,
                    latitude = it.latitude,
                    longitude = it.longitude,
                    active = false,
                    expiresAt = it.expiresAt,
                    ownerCognitoId = it.ownerCognitoId
                )
            )
        }
    }
}