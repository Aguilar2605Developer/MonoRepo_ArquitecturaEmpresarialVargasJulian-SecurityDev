package com.pucetec.users.services

import com.pucetec.users.dto.CognitoSyncRequest
import com.pucetec.users.dto.CognitoSyncResponse
import com.pucetec.users.dto.UserRequest
import com.pucetec.users.dto.UserResponse
import com.pucetec.users.entities.User
import com.pucetec.users.exceptions.BlankNameException
import com.pucetec.users.exceptions.UserNotFoundException
import com.pucetec.users.mappers.UserMapper
import com.pucetec.users.repositories.UserRepository
import org.springframework.stereotype.Service

@Service
class UserService(
    private val userRepository: UserRepository,
    private val userMapper: UserMapper,
    private val auditService: AuditService
) {

    fun createOrUpdateMe(cognitoId: String, request: UserRequest): UserResponse {
        if (request.name.isBlank()) {
            throw BlankNameException("El nombre no puede estar vacio")
        }

        val existing = userRepository.findByCognitoId(cognitoId)
        val toSave = if (existing != null) {
            User(
                id = existing.id,
                cognitoId = cognitoId,
                name = request.name,
                email = request.email ?: existing.email,
                phone = request.phone ?: existing.phone
            )
        } else {
            User(
                cognitoId = cognitoId,
                name = request.name,
                email = request.email,
                phone = request.phone
            )
        }
        val saved = userRepository.save(toSave)
        if (existing != null) {
            auditService.recordUpdate("User", saved.id.toString(), oldValue = existing, newValue = saved)
        } else {
            auditService.recordCreate("User", saved.id.toString(), newValue = saved)
        }
        return userMapper.toResponse(saved)
    }

    fun getMe(cognitoId: String): UserResponse {
        val user = userRepository.findByCognitoId(cognitoId)
            ?: throw UserNotFoundException("No existe un perfil para este usuario todavia")
        return userMapper.toResponse(user)
    }

    fun getAllUsers(): List<UserResponse> =
        userRepository.findAll().map { userMapper.toResponse(it) }

    fun getUserById(id: Long): UserResponse {
        val user = userRepository.findById(id)
            .orElseThrow { UserNotFoundException("No existe el usuario con id $id") }
        return userMapper.toResponse(user)
    }

    fun getUserByCognitoId(cognitoId: String): UserResponse {
        val user = userRepository.findByCognitoId(cognitoId)
            ?: throw UserNotFoundException("No existe un perfil para cognitoId $cognitoId")
        return userMapper.toResponse(user)
    }

    fun deleteUser(id: Long) {
        val existing = userRepository.findById(id)
            .orElseThrow { UserNotFoundException("No existe el usuario con id $id") }
        userRepository.deleteById(id)
        auditService.recordDelete("User", existing.id.toString(), oldValue = existing)
    }

    fun syncFromCognito(request: CognitoSyncRequest): CognitoSyncResponse {
        var creados = 0
        var actualizados = 0

        for (cognitoUser in request.users) {
            val existing = userRepository.findByCognitoId(cognitoUser.cognitoId)
            val toSave = if (existing != null) {
                actualizados++
                User(
                    id = existing.id,
                    cognitoId = cognitoUser.cognitoId,
                    name = cognitoUser.name.ifBlank { existing.name },
                    email = cognitoUser.email ?: existing.email,
                    phone = cognitoUser.phone ?: existing.phone
                )
            } else {
                creados++
                User(
                    cognitoId = cognitoUser.cognitoId,
                    name = cognitoUser.name,
                    email = cognitoUser.email,
                    phone = cognitoUser.phone
                )
            }
            val saved = userRepository.save(toSave)
            if (existing != null) {
                auditService.recordUpdate("User", saved.id.toString(), oldValue = existing, newValue = saved)
            } else {
                auditService.recordCreate("User", saved.id.toString(), newValue = saved)
            }
        }

        return CognitoSyncResponse(
            totalRecibidos = request.users.size,
            creados = creados,
            actualizados = actualizados
        )
    }
}