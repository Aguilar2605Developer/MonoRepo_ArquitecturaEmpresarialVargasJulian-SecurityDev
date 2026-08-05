package com.pucetec.users.entities

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Lob
import jakarta.persistence.Table
import java.time.LocalDateTime

// Auditoria minima exigida por la rubrica (Criterio 2e.2): quien / que
// entidad / que accion / cuando / valores anterior-nuevo. Registro
// persistente aparte del logging de negocio (event=...).
@Entity
@Table(name = "audit_log")
class AuditLog(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0L,

    // "sub" de Cognito de quien ejecuto la accion (CurrentUser.sub()).
    // Nullable: el sync masivo desde securitydev reenvia el JWT del admin,
    // pero por robustez se deja nullable igual que en securitydev.
    @Column(name = "user_id")
    val userId: String? = null,

    @Column(name = "entity_name", nullable = false)
    val entityName: String = "",

    @Column(name = "entity_id", nullable = false)
    val entityId: String = "",

    // "CREATE" | "UPDATE" | "DELETE"
    @Column(name = "action", nullable = false)
    val action: String = "",

    @Column(name = "created_at", nullable = false)
    val createdAt: LocalDateTime = LocalDateTime.now(),

    // JSON serializado del estado anterior. Null si action = CREATE.
    @Lob
    @Column(name = "old_value")
    val oldValue: String? = null,

    // JSON serializado del estado nuevo. Null si action = DELETE.
    @Lob
    @Column(name = "new_value")
    val newValue: String? = null
)