package com.pucetec.users.services

import com.pucetec.users.dto.CognitoSyncRequest
import com.pucetec.users.dto.CognitoSyncUser
import com.pucetec.users.dto.UserRequest
import com.pucetec.users.dto.UserResponse
import com.pucetec.users.entities.User
import com.pucetec.users.exceptions.BlankNameException
import com.pucetec.users.exceptions.UserNotFoundException
import com.pucetec.users.mappers.UserMapper
import com.pucetec.users.repositories.UserRepository
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.InjectMocks
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.*
import java.util.Optional

@ExtendWith(MockitoExtension::class)
class UserServiceTest {

    @Mock
    private lateinit var userRepository: UserRepository

    @Mock
    private lateinit var userMapper: UserMapper

    @InjectMocks
    private lateinit var userService: UserService

    private lateinit var sampleEntity: User

    @BeforeEach
    fun setUp() {
        sampleEntity = User(
            id = 1L,
            cognitoId = "sub-abc-123",
            name = "Juan Perez",
            email = "juanperez@example.com",
            phone = "0999999999"
        )
    }

    // ---------------------- createOrUpdateMe ----------------------

    @Test
    fun `createOrUpdateMe crea un usuario nuevo cuando el cognitoId no existe`() {
        val request = UserRequest(name = "Juan Perez", email = "juanperez@example.com", phone = "0999999999")
        whenever(userRepository.findByCognitoId("sub-abc-123")).thenReturn(null)
        whenever(userRepository.save(any())).thenReturn(sampleEntity)
        whenever(userMapper.toResponse(sampleEntity)).thenReturn(
            UserResponse(1L, "sub-abc-123", "Juan Perez", "juanperez@example.com", "0999999999")
        )

        val result = userService.createOrUpdateMe("sub-abc-123", request)

        assertEquals("Juan Perez", result.name)
        verify(userRepository).save(any())
    }

    @Test
    fun `createOrUpdateMe lanza BlankNameException si el nombre esta vacio`() {
        val request = UserRequest(name = "  ", email = null, phone = null)

        assertThrows(BlankNameException::class.java) {
            userService.createOrUpdateMe("sub-abc-123", request)
        }
        verify(userRepository, never()).save(any())
    }

    // ---------------------- getMe ----------------------

    @Test
    fun `getMe lanza UserNotFoundException si no existe perfil para ese cognitoId`() {
        whenever(userRepository.findByCognitoId("sub-desconocido")).thenReturn(null)

        assertThrows(UserNotFoundException::class.java) {
            userService.getMe("sub-desconocido")
        }
    }

    // ---------------------- getUserById ----------------------

    @Test
    fun `getUserById lanza UserNotFoundException si el id no existe`() {
        whenever(userRepository.findById(99L)).thenReturn(Optional.empty())

        assertThrows(UserNotFoundException::class.java) {
            userService.getUserById(99L)
        }
    }

    // ---------------------- deleteUser ----------------------

    @Test
    fun `deleteUser lanza UserNotFoundException si el id no existe`() {
        whenever(userRepository.existsById(99L)).thenReturn(false)

        assertThrows(UserNotFoundException::class.java) {
            userService.deleteUser(99L)
        }
        verify(userRepository, never()).deleteById(any())
    }

    // ---------------------- getAllUsers ----------------------

    @Test
    fun `getAllUsers devuelve la lista mapeada de todos los usuarios`() {
        val segundoEntity = User(
            id = 2L,
            cognitoId = "sub-def-456",
            name = "Maria Lopez",
            email = "maria@example.com",
            phone = "0988888888"
        )
        whenever(userRepository.findAll()).thenReturn(listOf(sampleEntity, segundoEntity))
        whenever(userMapper.toResponse(sampleEntity)).thenReturn(
            UserResponse(1L, "sub-abc-123", "Juan Perez", "juanperez@example.com", "0999999999")
        )
        whenever(userMapper.toResponse(segundoEntity)).thenReturn(
            UserResponse(2L, "sub-def-456", "Maria Lopez", "maria@example.com", "0988888888")
        )

        val result = userService.getAllUsers()

        assertEquals(2, result.size)
        assertEquals("Juan Perez", result[0].name)
        assertEquals("Maria Lopez", result[1].name)
    }

    @Test
    fun `getAllUsers devuelve lista vacia si no hay usuarios`() {
        whenever(userRepository.findAll()).thenReturn(emptyList())

        val result = userService.getAllUsers()

        assertTrue(result.isEmpty())
    }

    // ---------------------- getUserByCognitoId ----------------------

    @Test
    fun `getUserByCognitoId devuelve el perfil cuando el cognitoId existe`() {
        whenever(userRepository.findByCognitoId("sub-abc-123")).thenReturn(sampleEntity)
        whenever(userMapper.toResponse(sampleEntity)).thenReturn(
            UserResponse(1L, "sub-abc-123", "Juan Perez", "juanperez@example.com", "0999999999")
        )

        val result = userService.getUserByCognitoId("sub-abc-123")

        assertEquals("sub-abc-123", result.cognitoId)
        assertEquals("Juan Perez", result.name)
    }

    @Test
    fun `getUserByCognitoId lanza UserNotFoundException si el cognitoId no existe`() {
        whenever(userRepository.findByCognitoId("sub-inexistente")).thenReturn(null)

        assertThrows(UserNotFoundException::class.java) {
            userService.getUserByCognitoId("sub-inexistente")
        }
    }

    // ---------------------- syncFromCognito ----------------------

    @Test
    fun `syncFromCognito crea un usuario nuevo cuando el cognitoId no existe`() {
        val cognitoUser = CognitoSyncUser(
            cognitoId = "sub-nuevo-1",
            email = "nuevo@example.com",
            name = "Usuario Nuevo",
            phone = "0911111111"
        )
        val request = CognitoSyncRequest(users = listOf(cognitoUser))

        whenever(userRepository.findByCognitoId("sub-nuevo-1")).thenReturn(null)
        whenever(userRepository.save(any())).thenReturn(sampleEntity)

        val result = userService.syncFromCognito(request)

        assertEquals(1, result.totalRecibidos)
        assertEquals(1, result.creados)
        assertEquals(0, result.actualizados)
        verify(userRepository).save(
            check<User> {
                assertEquals("sub-nuevo-1", it.cognitoId)
                assertEquals("Usuario Nuevo", it.name)
                assertEquals("nuevo@example.com", it.email)
                assertEquals("0911111111", it.phone)
            }
        )
    }

    @Test
    fun `syncFromCognito actualiza un usuario existente conservando el id`() {
        val cognitoUser = CognitoSyncUser(
            cognitoId = "sub-abc-123",
            email = "nuevoemail@example.com",
            name = "Juan Perez Actualizado",
            phone = "0922222222"
        )
        val request = CognitoSyncRequest(users = listOf(cognitoUser))

        whenever(userRepository.findByCognitoId("sub-abc-123")).thenReturn(sampleEntity)
        whenever(userRepository.save(any())).thenReturn(sampleEntity)

        val result = userService.syncFromCognito(request)

        assertEquals(1, result.totalRecibidos)
        assertEquals(0, result.creados)
        assertEquals(1, result.actualizados)
        verify(userRepository).save(
            check<User> {
                assertEquals(1L, it.id) // conserva el id existente -> UPDATE, no INSERT
                assertEquals("sub-abc-123", it.cognitoId)
                assertEquals("Juan Perez Actualizado", it.name)
                assertEquals("nuevoemail@example.com", it.email)
                assertEquals("0922222222", it.phone)
            }
        )
    }

    @Test
    fun `syncFromCognito usa el nombre existente si el nombre entrante viene en blanco`() {
        val cognitoUser = CognitoSyncUser(
            cognitoId = "sub-abc-123",
            email = null,
            name = "   ",
            phone = null
        )
        val request = CognitoSyncRequest(users = listOf(cognitoUser))

        whenever(userRepository.findByCognitoId("sub-abc-123")).thenReturn(sampleEntity)
        whenever(userRepository.save(any())).thenReturn(sampleEntity)

        userService.syncFromCognito(request)

        verify(userRepository).save(
            check<User> {
                // name en blanco -> conserva el name existente (ifBlank)
                assertEquals("Juan Perez", it.name)
                // email/phone null -> conserva los existentes (?: fallback)
                assertEquals("juanperez@example.com", it.email)
                assertEquals("0999999999", it.phone)
            }
        )
    }

    @Test
    fun `syncFromCognito procesa una lista mixta y cuenta correctamente creados y actualizados`() {
        val existente = CognitoSyncUser(
            cognitoId = "sub-abc-123",
            email = "juanperez@example.com",
            name = "Juan Perez",
            phone = "0999999999"
        )
        val nuevo1 = CognitoSyncUser(
            cognitoId = "sub-nuevo-1",
            email = "nuevo1@example.com",
            name = "Nuevo Uno",
            phone = null
        )
        val nuevo2 = CognitoSyncUser(
            cognitoId = "sub-nuevo-2",
            email = "nuevo2@example.com",
            name = "Nuevo Dos",
            phone = null
        )
        val request = CognitoSyncRequest(users = listOf(existente, nuevo1, nuevo2))

        whenever(userRepository.findByCognitoId("sub-abc-123")).thenReturn(sampleEntity)
        whenever(userRepository.findByCognitoId("sub-nuevo-1")).thenReturn(null)
        whenever(userRepository.findByCognitoId("sub-nuevo-2")).thenReturn(null)
        whenever(userRepository.save(any())).thenReturn(sampleEntity)

        val result = userService.syncFromCognito(request)

        assertEquals(3, result.totalRecibidos)
        assertEquals(2, result.creados)
        assertEquals(1, result.actualizados)
        verify(userRepository, times(3)).save(any())
    }

    @Test
    fun `syncFromCognito con lista vacia no guarda nada y devuelve contadores en cero`() {
        val request = CognitoSyncRequest(users = emptyList())

        val result = userService.syncFromCognito(request)

        assertEquals(0, result.totalRecibidos)
        assertEquals(0, result.creados)
        assertEquals(0, result.actualizados)
        verify(userRepository, never()).save(any())
    }
}