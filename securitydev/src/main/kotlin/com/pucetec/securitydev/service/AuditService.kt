package com.pucetec.securitydev.service

import com.fasterxml.jackson.databind.ObjectMapper
import com.pucetec.securitydev.entity.AuditLog
import com.pucetec.securitydev.repository.AuditLogRepository
import com.pucetec.securitydev.security.CurrentUser
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service

// Servicio de auditoria minima (Criterio 2e.2). Se llama explicitamente
// desde los services de negocio (AdminService, etc.) DESPUES de que la
// operacion ya se ejecuto con exito -- no intercepta nada automaticamente,
// a proposito: asi queda clarisimo en cada service que accion se esta
// auditando, sin "magia" de aspectos/listeners que sea dificil de explicar
// en la sustentacion.
//
// Nunca debe tumbar la operacion de negocio si falla: registrar auditoria
// es best-effort, igual que la sincronizacion hacia users-microservice.
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
            // Best-effort: si la auditoria falla, NO debe revertir ni
            // bloquear la operacion de negocio que ya se ejecuto.
            logger.error(
                "event=AUDIT_RECORD_FAILED msg=Could not save audit entry entity={} entityId={} action={}",
                entityName, entityId, action, ex
            )
        }
    }
}