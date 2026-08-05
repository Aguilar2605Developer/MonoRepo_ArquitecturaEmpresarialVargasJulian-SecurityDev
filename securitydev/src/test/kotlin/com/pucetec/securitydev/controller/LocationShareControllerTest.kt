package com.pucetec.securitydev.controller

import com.pucetec.securitydev.dto.LocationShareRequest
import com.pucetec.securitydev.dto.LocationShareResponse
import com.pucetec.securitydev.entity.LocationShare
import com.pucetec.securitydev.entity.LocationShareRecipient
import com.pucetec.securitydev.repository.LocationShareRecipientRepository
import com.pucetec.securitydev.service.EmailService
import com.pucetec.securitydev.service.LocationShareService
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
class LocationShareControllerTest {

    @Mock
    private lateinit var locationShareService: LocationShareService

    @Mock
    private lateinit var emailService: EmailService

    @Mock
    private lateinit var locationShareRecipientRepository: LocationShareRecipientRepository

    @InjectMocks
    private lateinit var locationShareController: LocationShareController

    private lateinit var ownedResponse: LocationShareResponse
    private lateinit var anonymousResponse: LocationShareResponse
    private lateinit var sampleRequest: LocationShareRequest
    private lateinit var ownedEntity: LocationShare

    @BeforeEach
    fun setUp() {
        ownedResponse = LocationShareResponse(
            shareId = "share-123", latitude = -0.18, longitude = -78.46,
            active = true, expiresAt = LocalDateTime.now().plusMinutes(10),
            ownerCognitoId = "cognito-sub-1"
        )
        anonymousResponse = LocationShareResponse(
            shareId = "share-anon", latitude = -0.18, longitude = -78.46,
            active = true, expiresAt = LocalDateTime.now().plusMinutes(10),
            ownerCognitoId = null
        )
        sampleRequest = LocationShareRequest(latitude = 1.0, longitude = 1.0)
        ownedEntity = LocationShare(
            id = 1L, shareId = "share-123", latitude = -0.18, longitude = -78.46,
            active = true, expiresAt = LocalDateTime.now().plusMinutes(10), ownerCognitoId = "cognito-sub-1"
        )
    }

    @AfterEach
    fun limpiarContexto() {
        SecurityContextHolder.clearContext()
    }

    private fun buildJwt(
        sub: String,
        email: String = "juan@example.com",
        name: String = "Juan",
        emailVerified: Boolean = true
    ): Jwt {
        return Jwt.withTokenValue("token-de-prueba")
            .header("alg", "RS256")
            .claim("sub", sub)
            .claim("email", email)
            .claim("name", name)
            .claim("email_verified", emailVerified)
            .issuedAt(Instant.now())
            .expiresAt(Instant.now().plusSeconds(3600))
            .build()
    }

    private fun autenticarComo(
        sub: String,
        email: String = "juan@example.com",
        name: String = "Juan",
        emailVerified: Boolean = true
    ) {
        val jwt = buildJwt(sub, email, name, emailVerified)
        val auth = UsernamePasswordAuthenticationToken(jwt, null, emptyList())
        SecurityContextHolder.getContext().authentication = auth
    }

    // ---------------------- startSharing ----------------------

    @Test
    fun `startSharing deberia usar el cognitoId del JWT como dueño`() {
        autenticarComo("cognito-sub-1")
        whenever(locationShareService.startSharing(sampleRequest, "cognito-sub-1")).thenReturn(ownedResponse)

        val result = locationShareController.startSharing(sampleRequest)

        assertEquals(200, result.statusCode.value())
        verify(locationShareService, times(1)).startSharing(sampleRequest, "cognito-sub-1")
    }

    @Test
    fun `startSharing deberia lanzar AccessDenied cuando no hay usuario autenticado`() {
        SecurityContextHolder.clearContext()

        assertThrows(AccessDeniedException::class.java) {
            locationShareController.startSharing(sampleRequest)
        }
        verify(locationShareService, never()).startSharing(any(), anyOrNull())
    }

    // ---------------------- updateLocation ----------------------

    @Test
    fun `updateLocation deberia permitir cuando el usuario es el dueño del share`() {
        autenticarComo("cognito-sub-1")
        whenever(locationShareService.getByShareId("share-123")).thenReturn(ownedResponse)
        whenever(locationShareService.updateLocation("share-123", 1.0, 1.0)).thenReturn(ownedResponse)

        val result = locationShareController.updateLocation("share-123", sampleRequest)

        assertEquals(200, result.statusCode.value())
        verify(locationShareService, times(1)).updateLocation("share-123", 1.0, 1.0)
    }

    @Test
    fun `updateLocation deberia lanzar AccessDenied cuando el usuario no es el dueño`() {
        autenticarComo("cognito-sub-2")
        whenever(locationShareService.getByShareId("share-123")).thenReturn(ownedResponse)

        assertThrows(AccessDeniedException::class.java) {
            locationShareController.updateLocation("share-123", sampleRequest)
        }
        verify(locationShareService, never()).updateLocation(any(), any(), any())
    }

    @Test
    fun `updateLocation deberia lanzar AccessDenied en un share sin dueño (nadie puede editarlo)`() {
        autenticarComo("cognito-sub-2")
        whenever(locationShareService.getByShareId("share-anon")).thenReturn(anonymousResponse)

        assertThrows(AccessDeniedException::class.java) {
            locationShareController.updateLocation("share-anon", sampleRequest)
        }
        verify(locationShareService, never()).updateLocation(any(), any(), any())
    }

    // ---------------------- stopSharing ----------------------

    @Test
    fun `stopSharing deberia lanzar AccessDenied cuando el usuario no es el dueño del share`() {
        autenticarComo("cognito-sub-2")
        whenever(locationShareService.getByShareId("share-123")).thenReturn(ownedResponse)

        assertThrows(AccessDeniedException::class.java) {
            locationShareController.stopSharing("share-123")
        }
        verify(locationShareService, never()).stopSharing(any())
    }

    @Test
    fun `stopSharing deberia permitir cuando el usuario es el dueño del share`() {
        autenticarComo("cognito-sub-1")
        whenever(locationShareService.getByShareId("share-123")).thenReturn(ownedResponse)
        whenever(locationShareService.stopSharing("share-123")).thenReturn(ownedResponse)

        val result = locationShareController.stopSharing("share-123")

        assertEquals(200, result.statusCode.value())
        verify(locationShareService, times(1)).stopSharing("share-123")
    }

    // ---------------------- getByShareId ----------------------

    @Test
    fun `getByShareId deberia permitir al dueño sin revisar destinatarios`() {
        autenticarComo("cognito-sub-1")
        whenever(locationShareService.getByShareId("share-123")).thenReturn(ownedResponse)

        val result = locationShareController.getByShareId("share-123")

        assertEquals(200, result.statusCode.value())
        verify(locationShareRecipientRepository, never())
            .existsByLocationShareShareIdAndEmail(any(), any())
    }

    @Test
    fun `getByShareId deberia permitir a un destinatario autorizado con correo verificado`() {
        autenticarComo("cognito-sub-2", email = "invitado@example.com", emailVerified = true)
        whenever(locationShareService.getByShareId("share-123")).thenReturn(ownedResponse)
        whenever(locationShareRecipientRepository.existsByLocationShareShareIdAndEmail("share-123", "invitado@example.com"))
            .thenReturn(true)

        val result = locationShareController.getByShareId("share-123")

        assertEquals(200, result.statusCode.value())
    }

    @Test
    fun `getByShareId deberia lanzar AccessDenied cuando el correo no esta autorizado`() {
        autenticarComo("cognito-sub-2", email = "intruso@example.com", emailVerified = true)
        whenever(locationShareService.getByShareId("share-123")).thenReturn(ownedResponse)
        whenever(locationShareRecipientRepository.existsByLocationShareShareIdAndEmail("share-123", "intruso@example.com"))
            .thenReturn(false)

        assertThrows(AccessDeniedException::class.java) {
            locationShareController.getByShareId("share-123")
        }
    }

    @Test
    fun `getByShareId deberia lanzar AccessDenied cuando el correo no esta verificado`() {
        autenticarComo("cognito-sub-2", email = "invitado@example.com", emailVerified = false)
        whenever(locationShareService.getByShareId("share-123")).thenReturn(ownedResponse)

        assertThrows(AccessDeniedException::class.java) {
            locationShareController.getByShareId("share-123")
        }
        verify(locationShareRecipientRepository, never())
            .existsByLocationShareShareIdAndEmail(any(), any())
    }

    @Test
    fun `getByShareId deberia lanzar AccessDenied cuando no hay usuario autenticado`() {
        SecurityContextHolder.clearContext()
        whenever(locationShareService.getByShareId("share-123")).thenReturn(ownedResponse)

        assertThrows(AccessDeniedException::class.java) {
            locationShareController.getByShareId("share-123")
        }
    }

    // ---------------------- sendShareEmail ----------------------

    @Test
    fun `sendShareEmail deberia guardar el destinatario nuevo y enviar el correo cuando el usuario es el dueño`() {
        autenticarComo("cognito-sub-1", name = "Juan Perez")
        val request = LocationShareController.ShareEmailRequest(email = "  Amigo@Example.com  ")
        whenever(locationShareService.getByShareId("share-123")).thenReturn(ownedResponse)
        whenever(locationShareService.getEntityByShareId("share-123")).thenReturn(ownedEntity)
        whenever(locationShareRecipientRepository.existsByLocationShareShareIdAndEmail("share-123", "amigo@example.com"))
            .thenReturn(false)

        val result = locationShareController.sendShareEmail("share-123", request)

        assertEquals(200, result.statusCode.value())
        verify(locationShareRecipientRepository, times(1)).save(any())
        verify(emailService, times(1)).sendLocationShareEmail(
            toEmail = "amigo@example.com", username = "Juan Perez", shareId = "share-123"
        )
    }

    @Test
    fun `sendShareEmail no deberia duplicar el destinatario si ya existe pero si reenviar el correo`() {
        autenticarComo("cognito-sub-1", name = "Juan Perez")
        val request = LocationShareController.ShareEmailRequest(email = "amigo@example.com")
        whenever(locationShareService.getByShareId("share-123")).thenReturn(ownedResponse)
        whenever(locationShareService.getEntityByShareId("share-123")).thenReturn(ownedEntity)
        whenever(locationShareRecipientRepository.existsByLocationShareShareIdAndEmail("share-123", "amigo@example.com"))
            .thenReturn(true)

        locationShareController.sendShareEmail("share-123", request)

        verify(locationShareRecipientRepository, never()).save(any())
        verify(emailService, times(1)).sendLocationShareEmail(any(), any(), any())
    }

    @Test
    fun `sendShareEmail deberia lanzar AccessDenied cuando el usuario no es el dueño`() {
        autenticarComo("cognito-sub-2")
        val request = LocationShareController.ShareEmailRequest(email = "amigo@example.com")
        whenever(locationShareService.getByShareId("share-123")).thenReturn(ownedResponse)

        assertThrows(AccessDeniedException::class.java) {
            locationShareController.sendShareEmail("share-123", request)
        }
        verify(emailService, never()).sendLocationShareEmail(any(), any(), any())
    }

    // ---------------------- listRecipients ----------------------

    @Test
    fun `listRecipients deberia devolver los correos cuando el usuario es el dueño`() {
        autenticarComo("cognito-sub-1")
        whenever(locationShareService.getByShareId("share-123")).thenReturn(ownedResponse)
        whenever(locationShareRecipientRepository.findByLocationShareShareId("share-123")).thenReturn(
            listOf(
                LocationShareRecipient(id = 1L, locationShare = ownedEntity, email = "amigo1@example.com"),
                LocationShareRecipient(id = 2L, locationShare = ownedEntity, email = "amigo2@example.com")
            )
        )

        val result = locationShareController.listRecipients("share-123")

        assertEquals(200, result.statusCode.value())
        assertEquals(listOf("amigo1@example.com", "amigo2@example.com"), result.body)
    }

    @Test
    fun `listRecipients deberia lanzar AccessDenied cuando el usuario no es el dueño`() {
        autenticarComo("cognito-sub-2")
        whenever(locationShareService.getByShareId("share-123")).thenReturn(ownedResponse)

        assertThrows(AccessDeniedException::class.java) {
            locationShareController.listRecipients("share-123")
        }
        verify(locationShareRecipientRepository, never()).findByLocationShareShareId(any())
    }

    // ---------------------- revokeRecipient ----------------------

    @Test
    fun `revokeRecipient deberia eliminar al destinatario cuando el usuario es el dueño`() {
        autenticarComo("cognito-sub-1")
        whenever(locationShareService.getByShareId("share-123")).thenReturn(ownedResponse)

        val result = locationShareController.revokeRecipient("share-123", "Amigo@Example.com")

        assertEquals(204, result.statusCode.value())
        verify(locationShareRecipientRepository, times(1))
            .deleteByLocationShareShareIdAndEmail("share-123", "amigo@example.com")
    }

    @Test
    fun `revokeRecipient deberia lanzar AccessDenied cuando el usuario no es el dueño`() {
        autenticarComo("cognito-sub-2")
        whenever(locationShareService.getByShareId("share-123")).thenReturn(ownedResponse)

        assertThrows(AccessDeniedException::class.java) {
            locationShareController.revokeRecipient("share-123", "amigo@example.com")
        }
        verify(locationShareRecipientRepository, never()).deleteByLocationShareShareIdAndEmail(any(), any())
    }
}