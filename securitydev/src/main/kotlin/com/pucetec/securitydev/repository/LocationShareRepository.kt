package com.pucetec.securitydev.repository

import com.pucetec.securitydev.entity.LocationShare
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository
import java.time.LocalDateTime

// Acceso a la tabla location_share.
@Repository
interface LocationShareRepository : JpaRepository<LocationShare, Long> {
    fun findByShareIdAndActiveTrue(shareId: String): LocationShare?               // usado en updateLocation/stopSharing: exige que siga activo
    fun findByShareId(shareId: String): LocationShare?                            // lectura simple, sin exigir "active" (usado en getByShareId/getEntityByShareId)
    fun findByActiveTrueAndExpiresAtBefore(dateTime: LocalDateTime): List<LocationShare> // job @Scheduled de auto-expiración
    fun deleteByOwnerCognitoId(cognitoId: String)                                 // cascada al borrar/purgar un usuario
    fun countByActiveTrue(): Long                                                 // métrica "activeShares" del dashboard admin
}