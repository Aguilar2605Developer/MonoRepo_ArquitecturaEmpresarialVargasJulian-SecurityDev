package com.pucetec.securitydev

import com.pucetec.securitydev.client.UsersMicroserviceClient
import com.pucetec.securitydev.dto.UserCreateRequest
import com.pucetec.securitydev.dto.UserUpdateRequest
import com.pucetec.securitydev.entity.HotSpot
import com.pucetec.securitydev.entity.HotSpotReport
import com.pucetec.securitydev.entity.AdminRosterUser
import com.pucetec.securitydev.repository.HotSpotRepository
import com.pucetec.securitydev.repository.HotSpotReportRepository
import com.pucetec.securitydev.repository.LocationShareRecipientRepository
import com.pucetec.securitydev.repository.LocationShareRepository
import com.pucetec.securitydev.repository.AdminRosterUserRepository
import com.pucetec.securitydev.service.AdminService
import com.pucetec.securitydev.service.AuditService
import com.pucetec.securitydev.service.CognitoAdminService
import com.pucetec.securitydev.service.CognitoService
import com.pucetec.securitydev.service.CognitoUserSummary
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.*
import software.amazon.awssdk.services.cognitoidentityprovider.model.UserNotFoundException

@ExtendWith(MockitoExtension::class)
class AdminServiceTest {

    @Mock lateinit var userRepository: AdminRosterUserRepository
    @Mock lateinit var hotSpotRepository: HotSpotRepository
    @Mock lateinit var hotSpotReportRepository: HotSpotReportRepository
    @Mock lateinit var locationShareRepository: LocationShareRepository
    @Mock lateinit var locationShareRecipientRepository: LocationShareRecipientRepository
    @Mock lateinit var cognitoAdminService: CognitoAdminService
    @Mock lateinit var cognitoService: CognitoService

    @Mock lateinit var self: AdminService

    // NUEVO: mock del cliente hacia users-microservice. En estos tests
    // CurrentUser.rawToken() siempre devuelve null (no hay SecurityContext
    // configurado), asi que syncUsersFromCognito() nunca llega a invocar
    // este mock -- pero el constructor de AdminService igual lo exige.
    @Mock lateinit var usersMicroserviceClient: UsersMicroserviceClient

    // NUEVO: mock del servicio de auditoria (Criterio 2e.2). Como es
    // best-effort (nunca lanza), no hace falta stubbear nada en el
    // @BeforeEach; los tests existentes siguen pasando igual.
    @Mock lateinit var auditService: AuditService

    private lateinit var adminService: AdminService
    private lateinit var sampleUser: AdminRosterUser

    @BeforeEach
    fun setUp() {
        adminService = AdminService(
            userRepository,
            hotSpotRepository,
            hotSpotReportRepository,
            locationShareRepository,
            locationShareRecipientRepository,
            cognitoAdminService,
            cognitoService,
            self,
            usersMicroserviceClient,
            auditService
        )

        sampleUser = AdminRosterUser(
            id = 1L,
            cognitoSub = "cognito-sub-abc",
            name = "Juan Perez",
            email = "juan@example.com",
            number = "0999999999"
        )
    }

    // ── getAllUsers ─────────────────────────────────────────────────

    @Test
    fun `getAllUsers deberia retornar la lista mapeada con el conteo de hotspots por cognitoId`() {
        whenever(userRepository.findAll()).doReturn(listOf(sampleUser))
        whenever(hotSpotReportRepository.countByReporterCognitoId("cognito-sub-abc")).doReturn(3L)

        val result = adminService.getAllUsers()

        assertEquals(1, result.size)
        assertEquals(sampleUser.email, result[0].email)
        assertEquals(3, result[0].hotspotsCount)
    }

    @Test
    fun `getAllUsers deberia devolver hotspotsCount en 0 si el usuario no tiene cognitoSub`() {
        val userSinSub = sampleUser.copy2(cognitoSub = null)
        whenever(userRepository.findAll()).doReturn(listOf(userSinSub))

        val result = adminService.getAllUsers()

        assertEquals(0, result[0].hotspotsCount)
        verify(hotSpotReportRepository, never()).countByReporterCognitoId(any())
    }

    @Test
    fun `getAllUsers deberia retornar lista vacia si no hay usuarios`() {
        whenever(userRepository.findAll()).doReturn(emptyList())

        val result = adminService.getAllUsers()

        assertTrue(result.isEmpty())
    }

    // ── getUserById ─────────────────────────────────────────────────

    @Test
    fun `getUserById deberia retornar el usuario si existe`() {
        whenever(userRepository.findById(1L)).doReturn(java.util.Optional.of(sampleUser))
        whenever(hotSpotReportRepository.countByReporterCognitoId("cognito-sub-abc")).doReturn(0L)

        val result = adminService.getUserById(1L)

        assertEquals(sampleUser.id, result.id)
        assertEquals(sampleUser.name, result.name)
    }

    @Test
    fun `getUserById deberia lanzar excepcion si el usuario no existe`() {
        whenever(userRepository.findById(99L)).doReturn(java.util.Optional.empty())

        val exception = assertThrows(RuntimeException::class.java) {
            adminService.getUserById(99L)
        }
        assertTrue(exception.message!!.contains("99"))
    }

    // ── createUser ──────────────────────────────────────────────────

    @Test
    fun `createUser deberia crear el usuario en Cognito y guardar el roster local cuando el correo no existe`() {
        val request = UserCreateRequest(
            name = "Nuevo Usuario",
            email = "nuevo@example.com",
            number = "0988888888",
            password = "plainPass"
        )

        whenever(userRepository.existsByEmail(request.email)).doReturn(false)
        whenever(cognitoAdminService.createUser(request.email, request.name, request.password))
            .doReturn("cognito-sub-nuevo")
        whenever(userRepository.save(any())).thenAnswer { it.arguments[0] as AdminRosterUser }

        val result = adminService.createUser(request)

        assertEquals(request.name, result.name)
        assertEquals(request.email, result.email)
        verify(cognitoAdminService, times(1)).createUser(request.email, request.name, request.password)
        verify(cognitoAdminService, times(1)).addUserToGroup(request.email, "USER")
        verify(userRepository, times(1)).save(
            argThat { user -> user.cognitoSub == "cognito-sub-nuevo" }
        )
    }

    @Test
    fun `createUser deberia lanzar excepcion si el correo ya existe`() {
        val request = UserCreateRequest(
            name = "Duplicado",
            email = "juan@example.com",
            number = "0999999999",
            password = "plainPass"
        )

        whenever(userRepository.existsByEmail(request.email)).doReturn(true)

        assertThrows(IllegalArgumentException::class.java) {
            adminService.createUser(request)
        }
        verify(cognitoAdminService, never()).createUser(any(), any(), any())
        verify(userRepository, never()).save(any())
    }

    // ── updateUser ──────────────────────────────────────────────────

    @Test
    fun `updateUser deberia actualizar datos preservando el cognitoSub`() {
        val request = UserUpdateRequest(
            name = "Juan Actualizado",
            email = "juan.actualizado@example.com",
            number = "0977777777"
        )

        whenever(userRepository.findById(1L)).doReturn(java.util.Optional.of(sampleUser))
        whenever(userRepository.save(any())).thenAnswer { it.arguments[0] as AdminRosterUser }

        val result = adminService.updateUser(1L, request)

        assertEquals(request.name, result.name)
        assertEquals(request.email, result.email)
        verify(userRepository, times(1)).save(
            argThat { user -> user.cognitoSub == "cognito-sub-abc" && user.name == request.name }
        )
    }

    @Test
    fun `updateUser deberia lanzar excepcion si el usuario no existe`() {
        val request = UserUpdateRequest(
            name = "No importa",
            email = "no@example.com",
            number = "0900000000"
        )

        whenever(userRepository.findById(99L)).doReturn(java.util.Optional.empty())

        assertThrows(RuntimeException::class.java) {
            adminService.updateUser(99L, request)
        }
        verify(userRepository, never()).save(any())
    }

    // ── resetPassword ───────────────────────────────────────────────

    @Test
    fun `resetPassword deberia delegar en Cognito usando el email del usuario`() {
        whenever(userRepository.findById(1L)).doReturn(java.util.Optional.of(sampleUser))

        adminService.resetPassword(1L, "nuevaClave123")

        verify(cognitoAdminService, times(1)).resetPassword(sampleUser.email, "nuevaClave123")
        verify(userRepository, never()).save(any())
    }

    @Test
    fun `resetPassword deberia lanzar excepcion si el usuario no existe`() {
        whenever(userRepository.findById(99L)).doReturn(java.util.Optional.empty())

        assertThrows(RuntimeException::class.java) {
            adminService.resetPassword(99L, "clave")
        }
        verify(cognitoAdminService, never()).resetPassword(any(), any())
    }

    // ── deleteUser ──────────────────────────────────────────────────

    @Test
    fun `deleteUser deberia borrar location shares por cognitoId, borrar en Cognito y luego el usuario`() {
        whenever(userRepository.findById(1L)).doReturn(java.util.Optional.of(sampleUser))

        adminService.deleteUser(1L)

        verify(locationShareRecipientRepository, times(1)).deleteByLocationShareOwnerCognitoId("cognito-sub-abc")
        verify(locationShareRepository, times(1)).deleteByOwnerCognitoId("cognito-sub-abc")
        verify(cognitoAdminService, times(1)).deleteUser(sampleUser.email)
        verify(userRepository, times(1)).deleteById(1L)
    }

    @Test
    fun `deleteUser no deberia tocar location shares si el usuario no tiene cognitoSub`() {
        val userSinSub = sampleUser.copy2(cognitoSub = null)
        whenever(userRepository.findById(1L)).doReturn(java.util.Optional.of(userSinSub))

        adminService.deleteUser(1L)

        verify(locationShareRecipientRepository, never()).deleteByLocationShareOwnerCognitoId(any())
        verify(locationShareRepository, never()).deleteByOwnerCognitoId(any())
        verify(userRepository, times(1)).deleteById(1L)
    }

    @Test
    fun `deleteUser deberia continuar y borrar la fila local aunque el usuario ya no exista en Cognito`() {
        whenever(userRepository.findById(1L)).doReturn(java.util.Optional.of(sampleUser))
        whenever(cognitoAdminService.deleteUser(sampleUser.email))
            .thenThrow(UserNotFoundException.builder().message("no existe").build())

        adminService.deleteUser(1L)

        verify(userRepository, times(1)).deleteById(1L)
    }

    @Test
    fun `deleteUser deberia lanzar excepcion si el usuario no existe y no borrar nada`() {
        whenever(userRepository.findById(99L)).doReturn(java.util.Optional.empty())

        assertThrows(RuntimeException::class.java) {
            adminService.deleteUser(99L)
        }
        verify(locationShareRecipientRepository, never()).deleteByLocationShareOwnerCognitoId(any())
        verify(locationShareRepository, never()).deleteByOwnerCognitoId(any())
        verify(cognitoAdminService, never()).deleteUser(any())
        verify(userRepository, never()).deleteById(any())
    }

    // ── purgeOrphanedUsers ──────────────────────────────────────────

    @Test
    fun `purgeOrphanedUsers deberia borrar solo los usuarios que ya no existen en Cognito`() {
        val vigente = AdminRosterUser(id = 1L, cognitoSub = "sub1", name = "A", email = "a@example.com", number = "1")
        val huerfano = AdminRosterUser(id = 2L, cognitoSub = "sub2", name = "B", email = "b@example.com", number = "2")

        whenever(userRepository.findAll()).doReturn(listOf(vigente, huerfano))
        whenever(cognitoService.getUserStatus("a@example.com")).doReturn("CONFIRMED")
        whenever(cognitoService.getUserStatus("b@example.com")).doReturn(null)

        val removed = adminService.purgeOrphanedUsers()

        assertEquals(listOf("b@example.com"), removed)
        verify(locationShareRecipientRepository, times(1)).deleteByLocationShareOwnerCognitoId("sub2")
        verify(locationShareRepository, times(1)).deleteByOwnerCognitoId("sub2")
        verify(userRepository, times(1)).deleteById(2L)
        verify(userRepository, never()).deleteById(1L)
    }

    @Test
    fun `purgeOrphanedUsers deberia devolver lista vacia si todos los usuarios siguen vigentes`() {
        whenever(userRepository.findAll()).doReturn(listOf(sampleUser))
        whenever(cognitoService.getUserStatus(sampleUser.email)).doReturn("CONFIRMED")

        val removed = adminService.purgeOrphanedUsers()

        assertTrue(removed.isEmpty())
        verify(userRepository, never()).deleteById(any())
    }

    // ── syncUsersFromCognito ────────────────────────────────────────

    @Test
    fun `syncUsersFromCognito deberia crear usuarios nuevos, omitir no confirmados y contar los existentes`() {
        val nuevo = CognitoUserSummary(sub = "sub-nuevo", email = "nuevo@example.com", name = "Nuevo", status = "CONFIRMED", enabled = true)
        val existente = CognitoUserSummary(sub = "sub-existente", email = "existente@example.com", name = "Existente", status = "CONFIRMED", enabled = true)
        val sinConfirmar = CognitoUserSummary(sub = "sub-pendiente", email = "pendiente@example.com", name = "Pendiente", status = "UNCONFIRMED", enabled = true)

        whenever(cognitoService.listAllUsers()).doReturn(listOf(nuevo, existente, sinConfirmar))
        whenever(userRepository.findByCognitoSub("sub-nuevo")).doReturn(null)
        whenever(userRepository.findByEmail("nuevo@example.com")).doReturn(null)
        whenever(userRepository.findByCognitoSub("sub-existente")).doReturn(sampleUser)
        whenever(userRepository.save(any())).thenAnswer { it.arguments[0] as AdminRosterUser }

        val result = adminService.syncUsersFromCognito()

        assertEquals(3, result.totalEnCognito)
        assertEquals(listOf("nuevo@example.com"), result.creados)
        assertEquals(1, result.yaExistian)
        assertEquals(listOf("pendiente@example.com"), result.omitidosNoConfirmados)
        verify(userRepository, times(1)).save(argThat { user -> user.cognitoSub == "sub-nuevo" })
        // No hay JWT en el SecurityContext durante el test -> el cliente
        // hacia users-microservice nunca deberia ser invocado.
        verify(usersMicroserviceClient, never()).syncUsers(any(), any())
    }

    // ── autoPurgeOrphanedUsersJob ───────────────────────────────────

    @Test
    fun `autoPurgeOrphanedUsersJob deberia delegar en el proxy self para respetar la transaccion`() {
        whenever(self.purgeOrphanedUsers()).doReturn(listOf("huerfano@example.com"))

        adminService.autoPurgeOrphanedUsersJob()

        verify(self, times(1)).purgeOrphanedUsers()
    }

    @Test
    fun `autoPurgeOrphanedUsersJob no deberia propagar la excepcion si la purga falla`() {
        whenever(self.purgeOrphanedUsers()).thenThrow(RuntimeException("DB caida"))

        assertDoesNotThrow {
            adminService.autoPurgeOrphanedUsersJob()
        }
    }

    // ── getDashboardStats ───────────────────────────────────────────

    @Test
    fun `getDashboardStats deberia calcular correctamente las estadisticas`() {
        val hotspot1 = HotSpot(id = 1L, active = true)
        val hotspot2 = HotSpot(id = 2L, active = true)
        val hotspot3 = HotSpot(id = 3L, active = true)

        val report1 = HotSpotReport(id = 100L, modality = "WIFI", hotSpot = hotspot1)
        val report2 = HotSpotReport(id = 101L, modality = "WIFI", hotSpot = hotspot2)
        val report3 = HotSpotReport(id = 102L, modality = "BLUETOOTH", hotSpot = hotspot3)

        whenever(userRepository.count()).doReturn(10L)
        whenever(hotSpotRepository.findByActiveTrue()).doReturn(listOf(hotspot1, hotspot2, hotspot3))
        whenever(hotSpotReportRepository.findByHotSpotIdIn(listOf(1L, 2L, 3L)))
            .doReturn(listOf(report1, report2, report3))
        whenever(locationShareRepository.countByActiveTrue()).doReturn(5L)

        val result = adminService.getDashboardStats()

        assertEquals(10, result.totalUsers)
        assertEquals(3, result.activeHotspotsTotal)
        assertEquals(2, result.hotspotsByModality["WIFI"])
        assertEquals(1, result.hotspotsByModality["BLUETOOTH"])
        assertEquals(5, result.activeShares)
    }
}

private fun AdminRosterUser.copy2(cognitoSub: String? = this.cognitoSub): AdminRosterUser =
    AdminRosterUser(id = this.id, cognitoSub = cognitoSub, name = this.name, email = this.email, number = this.number)