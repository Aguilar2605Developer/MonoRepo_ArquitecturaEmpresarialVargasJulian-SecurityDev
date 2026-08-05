package com.pucetec.securitydev.controller

import com.pucetec.securitydev.dto.HotSpotRequest
import com.pucetec.securitydev.dto.HotSpotResponse
import com.pucetec.securitydev.service.HotSpotService
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.InjectMocks
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.*
import org.springframework.security.access.AccessDeniedException
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.security.oauth2.jwt.Jwt
import java.time.Instant
import java.time.LocalDateTime

@ExtendWith(MockitoExtension::class)
class HotSpotControllerTest {

    @Mock
    private lateinit var hotSpotService: HotSpotService

    @InjectMocks
    private lateinit var hotSpotController: HotSpotController

    private lateinit var sampleRequest: HotSpotRequest
    private lateinit var ownedResponse: HotSpotResponse
    private lateinit var anonymousResponse: HotSpotResponse

    @BeforeEach
    fun setUp() {
        sampleRequest = HotSpotRequest(
            latitude = -0.18, longitude = -78.46, modality = "robo",
            description = "test", durationHours = 24, peopleInvolved = 1
        )
        ownedResponse = HotSpotResponse(
            id = 10L, latitude = -0.18, longitude = -78.46, modality = "robo",
            description = "test", reporterCognitoId = "cognito-sub-1", active = true,
            expiresAt = LocalDateTime.now().plusHours(24), peopleInvolved = 1
        )
        anonymousResponse = HotSpotResponse(
            id = 11L, latitude = -0.18, longitude = -78.46, modality = "robo",
            description = "test", reporterCognitoId = null, active = true,
            expiresAt = LocalDateTime.now().plusHours(24), peopleInvolved = 1
        )
    }

    @AfterEach
    fun limpiarContexto() {
        SecurityContextHolder.clearContext()
    }

    private fun buildJwt(sub: String): Jwt {
        return Jwt.withTokenValue("token-de-prueba")
            .header("alg", "RS256")
            .claim("sub", sub)
            .claim("email", "juan@example.com")
            .issuedAt(Instant.now())
            .expiresAt(Instant.now().plusSeconds(3600))
            .build()
    }

    // Autentica al "usuario actual" simulando un JWT valido en el SecurityContext.
    // Ya no hace falta resolveLocalId: el propio "sub" del JWT ES el identificador
    // que usa el controller (via CurrentUser.sub()).
    private fun autenticarComo(sub: String) {
        val jwt = buildJwt(sub)
        val auth = UsernamePasswordAuthenticationToken(jwt, null, emptyList())
        SecurityContextHolder.getContext().authentication = auth
    }

    // ---------------------- createHotSpot ----------------------

    @Test
    fun `createHotSpot deberia usar el sub del JWT como reporterCognitoId`() {
        autenticarComo("cognito-sub-1")
        whenever(hotSpotService.createHotSpot(sampleRequest, "cognito-sub-1")).thenReturn(ownedResponse)

        val result = hotSpotController.createHotSpot(sampleRequest)

        assertEquals(201, result.statusCode.value())
        verify(hotSpotService, times(1)).createHotSpot(sampleRequest, "cognito-sub-1")
    }

    @Test
    fun `createHotSpot deberia lanzar AccessDenied si no hay usuario autenticado`() {
        // No se llama a autenticarComo(): no hay JWT en el SecurityContext.
        assertThrows(AccessDeniedException::class.java) {
            hotSpotController.createHotSpot(sampleRequest)
        }
        verify(hotSpotService, never()).createHotSpot(any(), anyOrNull())
    }

    // ---------------------- getAllHotSpots / getHotSpotById ----------------------

    @Test
    fun `getAllHotSpots deberia delegar directamente en el service`() {
        whenever(hotSpotService.getAllHotSpots()).thenReturn(listOf(ownedResponse, anonymousResponse))

        val result = hotSpotController.getAllHotSpots()

        assertEquals(200, result.statusCode.value())
        assertEquals(2, result.body?.size)
    }

    @Test
    fun `getHotSpotById deberia delegar directamente en el service`() {
        whenever(hotSpotService.getHotSpotById(10L)).thenReturn(ownedResponse)

        val result = hotSpotController.getHotSpotById(10L)

        assertEquals(200, result.statusCode.value())
        assertEquals(ownedResponse.id, result.body?.id)
    }

    // ---------------------- updateHotSpot ----------------------

    @Test
    fun `updateHotSpot deberia permitir la edicion cuando el usuario es el dueño`() {
        autenticarComo("cognito-sub-1")
        whenever(hotSpotService.getHotSpotById(10L)).thenReturn(ownedResponse)
        whenever(hotSpotService.updateHotSpot(10L, sampleRequest, "cognito-sub-1")).thenReturn(ownedResponse)

        val result = hotSpotController.updateHotSpot(10L, sampleRequest)

        assertEquals(200, result.statusCode.value())
        verify(hotSpotService, times(1)).updateHotSpot(10L, sampleRequest, "cognito-sub-1")
    }

    @Test
    fun `updateHotSpot deberia lanzar AccessDenied cuando el usuario no es el dueño`() {
        autenticarComo("cognito-sub-2")
        whenever(hotSpotService.getHotSpotById(10L)).thenReturn(ownedResponse)

        assertThrows(AccessDeniedException::class.java) {
            hotSpotController.updateHotSpot(10L, sampleRequest)
        }
        verify(hotSpotService, never()).updateHotSpot(any(), any(), anyOrNull())
    }

    @Test
    fun `updateHotSpot deberia permitir la edicion cuando el hotspot es anonimo (sin dueño) y asignarlo al editor`() {
        autenticarComo("cognito-sub-2")
        whenever(hotSpotService.getHotSpotById(11L)).thenReturn(anonymousResponse)
        whenever(hotSpotService.updateHotSpot(11L, sampleRequest, "cognito-sub-2")).thenReturn(anonymousResponse)

        val result = hotSpotController.updateHotSpot(11L, sampleRequest)

        assertEquals(200, result.statusCode.value())
        verify(hotSpotService, times(1)).updateHotSpot(11L, sampleRequest, "cognito-sub-2")
    }

    @Test
    fun `updateHotSpot deberia lanzar AccessDenied si no hay usuario autenticado`() {
        whenever(hotSpotService.getHotSpotById(10L)).thenReturn(ownedResponse)

        assertThrows(AccessDeniedException::class.java) {
            hotSpotController.updateHotSpot(10L, sampleRequest)
        }
        verify(hotSpotService, never()).updateHotSpot(any(), any(), anyOrNull())
    }

    // ---------------------- deactivateHotSpot ----------------------

    @Test
    fun `deactivateHotSpot deberia permitir desactivar cuando el usuario es el dueño`() {
        autenticarComo("cognito-sub-1")
        whenever(hotSpotService.getHotSpotById(10L)).thenReturn(ownedResponse)
        whenever(hotSpotService.deactivateHotSpot(10L)).thenReturn(ownedResponse.copy(active = false))

        val result = hotSpotController.deactivateHotSpot(10L)

        assertEquals(200, result.statusCode.value())
        verify(hotSpotService, times(1)).deactivateHotSpot(10L)
    }

    @Test
    fun `deactivateHotSpot deberia lanzar AccessDenied cuando el usuario NO es el dueño`() {
        autenticarComo("cognito-sub-2")
        whenever(hotSpotService.getHotSpotById(10L)).thenReturn(ownedResponse)

        assertThrows(AccessDeniedException::class.java) {
            hotSpotController.deactivateHotSpot(10L)
        }
        verify(hotSpotService, never()).deactivateHotSpot(any())
    }

    @Test
    fun `deactivateHotSpot deberia permitir desactivar un hotspot anonimo (sin dueño)`() {
        autenticarComo("cognito-sub-2")
        whenever(hotSpotService.getHotSpotById(11L)).thenReturn(anonymousResponse)
        whenever(hotSpotService.deactivateHotSpot(11L)).thenReturn(anonymousResponse.copy(active = false))

        val result = hotSpotController.deactivateHotSpot(11L)

        assertEquals(200, result.statusCode.value())
        verify(hotSpotService, times(1)).deactivateHotSpot(11L)
    }

    // ---------------------- deleteHotSpot ----------------------

    @Test
    fun `deleteHotSpot deberia lanzar AccessDenied cuando el usuario no es el dueño`() {
        autenticarComo("cognito-sub-2")
        whenever(hotSpotService.getHotSpotById(10L)).thenReturn(ownedResponse)

        assertThrows(AccessDeniedException::class.java) {
            hotSpotController.deleteHotSpot(10L)
        }
        verify(hotSpotService, never()).deleteHotSpot(any())
    }

    @Test
    fun `deleteHotSpot deberia permitir eliminar cuando el usuario es el dueño`() {
        autenticarComo("cognito-sub-1")
        whenever(hotSpotService.getHotSpotById(10L)).thenReturn(ownedResponse)
        doNothing().whenever(hotSpotService).deleteHotSpot(10L)

        val result = hotSpotController.deleteHotSpot(10L)

        assertEquals(204, result.statusCode.value())
        verify(hotSpotService, times(1)).deleteHotSpot(10L)
    }

    @Test
    fun `deleteHotSpot deberia permitir eliminar un hotspot anonimo (sin dueño)`() {
        autenticarComo("cognito-sub-2")
        whenever(hotSpotService.getHotSpotById(11L)).thenReturn(anonymousResponse)
        doNothing().whenever(hotSpotService).deleteHotSpot(11L)

        val result = hotSpotController.deleteHotSpot(11L)

        assertEquals(204, result.statusCode.value())
        verify(hotSpotService, times(1)).deleteHotSpot(11L)
    }
}