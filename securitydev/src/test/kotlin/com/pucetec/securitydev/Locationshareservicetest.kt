package com.pucetec.securitydev

import com.pucetec.securitydev.dto.LocationShareRequest
import com.pucetec.securitydev.entity.LocationShare
import com.pucetec.securitydev.exceptions.LocationShareNotFoundException
import com.pucetec.securitydev.mappers.LocationShareMapper
import com.pucetec.securitydev.repository.LocationShareRepository
import com.pucetec.securitydev.service.LocationShareService
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.*
import java.time.LocalDateTime

@ExtendWith(MockitoExtension::class)
class LocationShareServiceTest {

    @Mock lateinit var locationShareRepository: LocationShareRepository

    // Mapper puro, sin dependencias externas: se usa la instancia real.
    private val locationShareMapper = LocationShareMapper()

    private lateinit var locationShareService: LocationShareService

    private lateinit var sampleRequest: LocationShareRequest
    private lateinit var sampleEntity: LocationShare
    private val ownerCognitoId = "cognito-sub-juan"

    @BeforeEach
    fun setUp() {
        locationShareService = LocationShareService(locationShareRepository, locationShareMapper)

        sampleRequest = LocationShareRequest(
            latitude = -0.180653,
            longitude = -78.467838
        )

        sampleEntity = LocationShare(
            id = 1L,
            shareId = "share-123",
            latitude = -0.180653,
            longitude = -78.467838,
            active = true,
            expiresAt = LocalDateTime.now().plusHours(1),
            ownerCognitoId = ownerCognitoId
        )
    }

    // ---------------------- startSharing ----------------------

    @Test
    fun `startSharing deberia crear y devolver el location share con el cognitoId del dueno`() {
        whenever(locationShareRepository.save(any())).thenReturn(sampleEntity)

        val result = locationShareService.startSharing(sampleRequest, ownerCognitoId)

        assertEquals("share-123", result.shareId)
        assertEquals(ownerCognitoId, result.ownerCognitoId)
        assertTrue(result.active)
        verify(locationShareRepository, times(1)).save(any())
    }

    @Test
    fun `startSharing deberia permitir ownerCognitoId nulo`() {
        whenever(locationShareRepository.save(any())).thenReturn(sampleEntity.let {
            LocationShare(id = it.id, shareId = it.shareId, latitude = it.latitude, longitude = it.longitude,
                active = it.active, expiresAt = it.expiresAt, ownerCognitoId = null)
        })

        val result = locationShareService.startSharing(sampleRequest, null)

        assertNull(result.ownerCognitoId)
    }

    // ---------------------- updateLocation ----------------------

    @Test
    fun `updateLocation deberia actualizar la ubicacion cuando el share existe y esta activo`() {
        val newLat = 10.0
        val newLng = 20.0

        whenever(locationShareRepository.findByShareIdAndActiveTrue("share-123")).thenReturn(sampleEntity)
        whenever(locationShareRepository.save(any())).thenAnswer { it.arguments[0] as LocationShare }

        val result = locationShareService.updateLocation("share-123", newLat, newLng)

        assertEquals(newLat, result.latitude)
        assertEquals(newLng, result.longitude)
        assertEquals(ownerCognitoId, result.ownerCognitoId) // se conserva el dueno original
        verify(locationShareRepository, times(1)).findByShareIdAndActiveTrue("share-123")
        verify(locationShareRepository, times(1)).save(any())
    }

    @Test
    fun `updateLocation deberia lanzar excepcion cuando el share no existe o esta expirado`() {
        whenever(locationShareRepository.findByShareIdAndActiveTrue("share-inexistente")).thenReturn(null)

        val exception = assertThrows(LocationShareNotFoundException::class.java) {
            locationShareService.updateLocation("share-inexistente", 1.0, 1.0)
        }

        assertTrue(exception.message!!.contains("share-inexistente"))
        verify(locationShareRepository, never()).save(any())
    }

    // ---------------------- getByShareId ----------------------

    @Test
    fun `getByShareId deberia devolver el share cuando existe`() {
        whenever(locationShareRepository.findByShareId("share-123")).thenReturn(sampleEntity)

        val result = locationShareService.getByShareId("share-123")

        assertEquals("share-123", result.shareId)
        verify(locationShareRepository, times(1)).findByShareId("share-123")
    }

    @Test
    fun `getByShareId deberia lanzar excepcion cuando el share no existe`() {
        whenever(locationShareRepository.findByShareId("share-inexistente")).thenReturn(null)

        val exception = assertThrows(LocationShareNotFoundException::class.java) {
            locationShareService.getByShareId("share-inexistente")
        }

        assertTrue(exception.message!!.contains("share-inexistente"))
    }

    // ---------------------- getEntityByShareId ----------------------

    @Test
    fun `getEntityByShareId deberia devolver la entidad cruda cuando existe`() {
        whenever(locationShareRepository.findByShareId("share-123")).thenReturn(sampleEntity)

        val result = locationShareService.getEntityByShareId("share-123")

        assertEquals(sampleEntity, result)
    }

    @Test
    fun `getEntityByShareId deberia lanzar excepcion cuando no existe`() {
        whenever(locationShareRepository.findByShareId("share-inexistente")).thenReturn(null)

        assertThrows(LocationShareNotFoundException::class.java) {
            locationShareService.getEntityByShareId("share-inexistente")
        }
    }

    // ---------------------- stopSharing ----------------------

    @Test
    fun `stopSharing deberia desactivar el share cuando existe y esta activo`() {
        whenever(locationShareRepository.findByShareIdAndActiveTrue("share-123")).thenReturn(sampleEntity)
        whenever(locationShareRepository.save(any())).thenAnswer { it.arguments[0] as LocationShare }

        val result = locationShareService.stopSharing("share-123")

        assertFalse(result.active)
        assertEquals(ownerCognitoId, result.ownerCognitoId)
        verify(locationShareRepository, times(1)).findByShareIdAndActiveTrue("share-123")
        verify(locationShareRepository, times(1)).save(any())
    }

    @Test
    fun `stopSharing deberia lanzar excepcion cuando el share no existe o esta expirado`() {
        whenever(locationShareRepository.findByShareIdAndActiveTrue("share-inexistente")).thenReturn(null)

        assertThrows(LocationShareNotFoundException::class.java) {
            locationShareService.stopSharing("share-inexistente")
        }

        verify(locationShareRepository, never()).save(any())
    }

    // ---------------------- deactivateExpiredShares ----------------------

    @Test
    fun `deactivateExpiredShares deberia desactivar todos los shares expirados`() {
        val expiredShare1 = LocationShare(
            id = 2L, shareId = "share-expired-1", latitude = 1.0, longitude = 1.0,
            active = true, expiresAt = LocalDateTime.now().minusHours(1), ownerCognitoId = ownerCognitoId
        )
        val expiredShare2 = LocationShare(
            id = 3L, shareId = "share-expired-2", latitude = 2.0, longitude = 2.0,
            active = true, expiresAt = LocalDateTime.now().minusMinutes(30), ownerCognitoId = null
        )

        whenever(locationShareRepository.findByActiveTrueAndExpiresAtBefore(any()))
            .thenReturn(listOf(expiredShare1, expiredShare2))
        whenever(locationShareRepository.save(any())).thenAnswer { it.arguments[0] as LocationShare }

        locationShareService.deactivateExpiredShares()

        verify(locationShareRepository, times(1)).findByActiveTrueAndExpiresAtBefore(any())
        verify(locationShareRepository, times(2)).save(any())
    }

    @Test
    fun `deactivateExpiredShares no deberia guardar nada cuando no hay shares expirados`() {
        whenever(locationShareRepository.findByActiveTrueAndExpiresAtBefore(any()))
            .thenReturn(emptyList())

        locationShareService.deactivateExpiredShares()

        verify(locationShareRepository, never()).save(any())
    }
}