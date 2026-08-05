package com.pucetec.users.repositories

import com.pucetec.users.entities.AuditLog
import org.springframework.data.jpa.repository.JpaRepository

interface AuditLogRepository : JpaRepository<AuditLog, Long> {
    fun findByEntityNameAndEntityIdOrderByCreatedAtDesc(entityName: String, entityId: String): List<AuditLog>
}