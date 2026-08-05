package com.pucetec.securitydev.repository

import com.pucetec.securitydev.entity.LocationShareRecipient
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository

// Acceso a la lista blanca de destinatarios autorizados por share.
@Repository
interface LocationShareRecipientRepository : JpaRepository<LocationShareRecipient, Long> {
    fun existsByLocationShareShareIdAndEmail(shareId: String, email: String): Boolean // el chequeo de autorización clave en GET /{shareId}
    fun findByLocationShareShareId(shareId: String): List<LocationShareRecipient>     // usado en listRecipients (solo dueño)
    fun deleteByLocationShareShareIdAndEmail(shareId: String, email: String)          // usado en revokeRecipient
    fun deleteByLocationShareOwnerCognitoId(cognitoId: String)                        // cascada al borrar/purgar un usuario (navega la relación ManyToOne)
}