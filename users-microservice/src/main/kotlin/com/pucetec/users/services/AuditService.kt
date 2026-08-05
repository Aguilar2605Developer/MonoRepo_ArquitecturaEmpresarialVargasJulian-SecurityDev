package com.pucetec.users.services

import com.fasterxml.jackson.databind.ObjectMapper
import com.pucetec.users.entities.AuditLog
import com.pucetec.users.repositories.AuditLogRepository
import com.pucetec.users.security.CurrentUser
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service

// Servicio de auditoria minima (Criterio 2e.2), espejo del de securitydev.
// Se llama explicitamente desde UserService DESPUES de que la operacion
// de negocio ya se ejecuto con exito. Best-effort: nunca debe tumbar la
// operacion de negocio si el guardado de auditoria falla.
@Service
class AuditService(
    private val auditLogRepository: AuditLogRepository,
    private val objectMapper: ObjectMapper
) {

    private val logger = LoggerFactory.getLogger(AuditService::class.java)

    fun recordCreate(entityName: String, entityId: String, newValue: Any?) =
        record(entityName, entityId, "CREATE", oldValue = null, newValue = newValue)

    fun recordUpdate(entityName: String, entityId: String, oldValue: Any?, newValue: Any?) =
        record(entityName, entityId, "UPDATE", oldValue = oldValue, newValue = newValue)

    fun recordDelete(entityName: String, entityId: String, oldValue: Any?) =
        record(entityName, entityId, "DELETE", oldValue = oldValue, newValue = null)

    private fun record(entityName: String, entityId: String, action: String, oldValue: Any?, newValue: Any?) {
        try {
            val entry = AuditLog(
                userId = CurrentUser.sub(),
                entityName = entityName,
                entityId = entityId,
                action = action,
                oldValue = oldValue?.let { objectMapper.writeValueAsString(it) },
                newValue = newValue?.let { objectMapper.writeValueAsString(it) }
            )
            auditLogRepository.save(entry)
            logger.info(
                "event=AUDIT_RECORDED msg=Audit entry saved entity={} entityId={} action={} userId={}",
                entityName, entityId, action, CurrentUser.sub()
            )
        } catch (ex: Exception) {
            logger.error(
                "event=AUDIT_RECORD_FAILED msg=Could not save audit entry entity={} entityId={} action={}",
                entityName, entityId, action, ex
            )
        }
    }
}