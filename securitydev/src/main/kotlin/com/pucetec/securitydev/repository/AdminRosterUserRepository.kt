package com.pucetec.securitydev.repository

import com.pucetec.securitydev.entity.AdminRosterUser
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository


// Usado activamente por UserService y AdminService.
@Repository
interface AdminRosterUserRepository : JpaRepository<AdminRosterUser, Long> {
    fun findByEmail(email: String): AdminRosterUser?          // usado en syncCurrentUser (fallback si no hay match por sub)
    fun existsByEmail(email: String): Boolean                  // usado en AdminService.createUser para evitar duplicados
    fun findByCognitoSub(cognitoSub: String): AdminRosterUser? // usado en syncCurrentUser y syncUsersFromCognito (match primario)
}