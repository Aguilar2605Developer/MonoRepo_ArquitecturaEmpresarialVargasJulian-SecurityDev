package com.pucetec.securitydev.service

import com.pucetec.securitydev.client.UsersMicroserviceClient
import com.pucetec.securitydev.dto.DashboardResponse
import com.pucetec.securitydev.dto.SyncFromCognitoResponse
import com.pucetec.securitydev.dto.UserAdminResponse
import com.pucetec.securitydev.dto.UserCreateRequest
import com.pucetec.securitydev.dto.UserUpdateRequest
import com.pucetec.securitydev.dto.UsersServiceSyncRequest
import com.pucetec.securitydev.dto.UsersServiceSyncUser
import com.pucetec.securitydev.entity.AdminRosterUser
import com.pucetec.securitydev.repository.HotSpotRepository
import com.pucetec.securitydev.repository.HotSpotReportRepository
import com.pucetec.securitydev.repository.LocationShareRecipientRepository
import com.pucetec.securitydev.repository.LocationShareRepository
import com.pucetec.securitydev.repository.AdminRosterUserRepository
import com.pucetec.securitydev.security.CurrentUser
import org.slf4j.LoggerFactory
import org.springframework.context.annotation.Lazy
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import software.amazon.awssdk.services.cognitoidentityprovider.model.UserNotFoundException

@Service
class AdminService(
    private val userRepository: AdminRosterUserRepository,
    private val hotSpotRepository: HotSpotRepository,
    private val hotSpotReportRepository: HotSpotReportRepository,
    private val locationShareRepository: LocationShareRepository,
    private val locationShareRecipientRepository: LocationShareRecipientRepository,
    private val cognitoAdminService: CognitoAdminService,
    private val cognitoService: CognitoService,
    @Lazy private val self: AdminService,
    private val usersMicroserviceClient: UsersMicroserviceClient,
    private val auditService: AuditService
) {

    private val logger = LoggerFactory.getLogger(AdminService::class.java)

    fun getAllUsers(): List<UserAdminResponse> = userRepository.findAll().map { toUserAdminResponse(it) }

    fun getUserById(id: Long): UserAdminResponse {
        val user = userRepository.findById(id).orElseThrow {
            RuntimeException("Usuario no encontrado con ID: $id")
        }
        return toUserAdminResponse(user)
    }

    fun createUser(request: UserCreateRequest): UserAdminResponse {
        if (userRepository.existsByEmail(request.email)) {
            throw IllegalArgumentException("Ya existe un usuario registrado con ese correo")
        }

        val sub = cognitoAdminService.createUser(request.email, request.name, request.password)
        cognitoAdminService.addUserToGroup(request.email, "USER")

        val newUser = AdminRosterUser(
            id = 0,
            cognitoSub = sub,
            name = request.name,
            email = request.email,
            number = request.number
        )
        val saved = userRepository.save(newUser)
        auditService.recordCreate("AdminRosterUser", saved.id.toString(), saved)
        return toUserAdminResponse(saved)
    }

    fun updateUser(id: Long, request: UserUpdateRequest): UserAdminResponse {
        val existing = userRepository.findById(id).orElseThrow {
            RuntimeException("Usuario no encontrado con ID: $id")
        }
        val updated = AdminRosterUser(
            id = existing.id,
            cognitoSub = existing.cognitoSub,
            name = request.name,
            email = request.email,
            number = request.number
        )
        val saved = userRepository.save(updated)
        auditService.recordUpdate("AdminRosterUser", saved.id.toString(), oldValue = existing, newValue = saved)
        return toUserAdminResponse(saved)
    }

    fun resetPassword(id: Long, newPassword: String) {
        val existing = userRepository.findById(id).orElseThrow {
            RuntimeException("Usuario no encontrado con ID: $id")
        }
        cognitoAdminService.resetPassword(existing.email, newPassword)
    }

    @Transactional
    fun deleteUser(id: Long) {
        val existing = userRepository.findById(id).orElseThrow {
            RuntimeException("Usuario no encontrado con ID: $id")
        }
        existing.cognitoSub?.let { sub ->
            locationShareRecipientRepository.deleteByLocationShareOwnerCognitoId(sub)
            locationShareRepository.deleteByOwnerCognitoId(sub)
        }
        try {
            cognitoAdminService.deleteUser(existing.email)
        } catch (ex: UserNotFoundException) {
            logger.warn("event=DELETE_USER_NOT_IN_COGNITO msg=User not found in Cognito, skipping remote delete and continuing with local delete email={}", existing.email)
        }
        userRepository.deleteById(id)
        auditService.recordDelete("AdminRosterUser", existing.id.toString(), oldValue = existing)
    }

    @Transactional
    fun purgeOrphanedUsers(): List<String> {
        val allUsers = userRepository.findAll()
        val removedEmails = mutableListOf<String>()

        for (user in allUsers) {
            val cognitoStatus = cognitoService.getUserStatus(user.email)
            if (cognitoStatus == null) {
                logger.warn(
                    "event=ORPHANED_USER_DETECTED msg=User no longer exists in Cognito, deleting locally email={} id={}",
                    user.email, user.id
                )
                user.cognitoSub?.let { sub ->
                    locationShareRecipientRepository.deleteByLocationShareOwnerCognitoId(sub)
                    locationShareRepository.deleteByOwnerCognitoId(sub)
                }
                userRepository.deleteById(user.id)
                removedEmails.add(user.email)
            }
        }

        return removedEmails
    }

    @Transactional
    fun syncUsersFromCognito(): SyncFromCognitoResponse {
        val cognitoUsers = cognitoService.listAllUsers()
        val creados = mutableListOf<String>()
        val omitidos = mutableListOf<String>()
        var yaExistian = 0

        for (cognitoUser in cognitoUsers) {
            if (cognitoUser.status != "CONFIRMED") {
                omitidos.add(cognitoUser.email)
                continue
            }

            val existing = userRepository.findByCognitoSub(cognitoUser.sub)
                ?: userRepository.findByEmail(cognitoUser.email)

            if (existing != null) {
                yaExistian++
                continue
            }

            val newUser = AdminRosterUser(
                id = 0,
                cognitoSub = cognitoUser.sub,
                name = cognitoUser.name,
                email = cognitoUser.email,
                number = cognitoUser.phoneNumber ?: ""
            )
            userRepository.save(newUser)
            creados.add(cognitoUser.email)
            logger.info("event=USER_SYNCED_FROM_COGNITO msg=User synced from Cognito and created locally email={}", cognitoUser.email)
        }

        val remoteSyncOk = syncToUsersMicroservice(cognitoUsers)

        return SyncFromCognitoResponse(
            totalEnCognito = cognitoUsers.size,
            creados = creados,
            yaExistian = yaExistian,
            omitidosNoConfirmados = omitidos,
            usersServiceSyncOk = remoteSyncOk
        )
    }

    private fun syncToUsersMicroservice(cognitoUsers: List<CognitoUserSummary>): Boolean {
        val bearerToken = CurrentUser.rawToken()
        if (bearerToken == null) {
            logger.warn("event=USERS_SERVICE_SYNC_SKIPPED msg=No hay JWT en el contexto actual, no se pudo llamar a users-microservice")
            return false
        }

        val confirmedUsers = cognitoUsers
            .filter { it.status == "CONFIRMED" }
            .map { UsersServiceSyncUser(cognitoId = it.sub, email = it.email, name = it.name, phone = it.phoneNumber) }

        if (confirmedUsers.isEmpty()) return true

        val response = usersMicroserviceClient.syncUsers(UsersServiceSyncRequest(users = confirmedUsers), bearerToken)
        if (response != null) {
            logger.info(
                "event=USERS_SERVICE_SYNC_OK msg=Sync replicado hacia users-microservice totalRecibidos={} creados={} actualizados={}",
                response.totalRecibidos, response.creados, response.actualizados
            )
            return true
        }
        return false
    }

    @Scheduled(fixedRate = 900000)
    fun autoPurgeOrphanedUsersJob() {
        try {
            val removed = self.purgeOrphanedUsers()
            if (removed.isNotEmpty()) {
                logger.info("event=AUTO_PURGE_ORPHANED_USERS msg=Auto-purge removed orphaned users count={} emails={}", removed.size, removed)
            }
        } catch (ex: Exception) {
            logger.error("event=AUTO_PURGE_FAILED msg=Auto-purge job failed, will retry next cycle", ex)
        }
    }

    fun getDashboardStats(): DashboardResponse {
        val activeHotspots = hotSpotRepository.findByActiveTrue()
        val activeIds = activeHotspots.map { it.id }
        val hotspotsByModality = hotSpotReportRepository.findByHotSpotIdIn(activeIds)
            .groupingBy { it.modality }
            .eachCount()

        return DashboardResponse(
            totalUsers = userRepository.count().toInt(),
            activeHotspotsTotal = activeHotspots.size,
            hotspotsByModality = hotspotsByModality,
            activeShares = locationShareRepository.countByActiveTrue().toInt()
        )
    }

    private fun toUserAdminResponse(user: AdminRosterUser): UserAdminResponse {
        val hotspotsCount = user.cognitoSub?.let { hotSpotReportRepository.countByReporterCognitoId(it).toInt() } ?: 0
        return UserAdminResponse(
            id = user.id,
            name = user.name,
            email = user.email,
            number = user.number,
            hotspotsCount = hotspotsCount
        )
    }
}