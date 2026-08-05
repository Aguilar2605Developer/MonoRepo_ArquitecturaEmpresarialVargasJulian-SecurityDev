package com.pucetec.securitydev.repository

import com.pucetec.securitydev.entity.AuditLog
import org.springframework.data.jpa.repository.JpaRepository

interface AuditLogRepository : JpaRepository<AuditLog, Long> {
    fun findByEntityNameAndEntityIdOrderByCreatedAtDesc(entityName: String, entityId: String): List<AuditLog>
}