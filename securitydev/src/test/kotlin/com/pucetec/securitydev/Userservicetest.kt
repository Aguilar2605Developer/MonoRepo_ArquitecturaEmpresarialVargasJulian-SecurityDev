package com.pucetec.securitydev

import com.pucetec.securitydev.entity.AdminRosterUser
import com.pucetec.securitydev.repository.AdminRosterUserRepository
import com.pucetec.securitydev.service.CognitoService
import com.pucetec.securitydev.service.UserService
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.*

@ExtendWith(MockitoExtension::class)
class UserServiceTest {

    @Mock lateinit var cognitoService: CognitoService
    @Mock lateinit var userRepository: AdminRosterUserRepository

    private lateinit var userService: UserService

    @BeforeEach
    fun setUp() {
        userService = UserService(cognitoService, userRepository)
    }

    // ---------------------- registerNewUser ----------------------

    @Test
    fun `registerNewUser deberia normalizar el correo y delegar en Cognito`() {
        userService.registerNewUser(
            email = "  Nuevo@Example.com  ",
            name = "Nuevo Usuario",
            number = "0999999999",
            password = "clave1234"
        )

        verify(cognitoService, times(1))
            .signUpPublic("nuevo@example.com", "Nuevo Usuario", "0999999999", "clave1234")
    }

    @Test
    fun `registerNewUser deberia recortar espacios de la contrasena antes de validarla`() {
        userService.registerNewUser(
            email = "user@example.com",
            name = "User",
            number = "0999999999",
            password = "  clave1234  "
        )

        verify(cognitoService, times(1))
            .signUpPublic("user@example.com", "User", "0999999999", "clave1234")
    }

    @Test
    fun `registerNewUser deberia lanzar excepcion si la contrasena tiene menos de 8 caracteres`() {
        val exception = assertThrows(IllegalArgumentException::class.java) {
            userService.registerNewUser(
                email = "user@example.com",
                name = "User",
                number = "0999999999",
                password = "1234567" // 7 caracteres
            )
        }

        assertTrue(exception.message!!.contains("8 caracteres"))
        verify(cognitoService, never()).signUpPublic(any(), any(), any(), any())
    }

    @Test
    fun `registerNewUser deberia aceptar una contrasena de exactamente 8 caracteres`() {
        userService.registerNewUser(
            email = "user@example.com",
            name = "User",
            number = "0999999999",
            password = "12345678"
        )

        verify(cognitoService, times(1)).signUpPublic(any(), any(), any(), any())
    }

    // ---------------------- confirmRegistration ----------------------

    @Test
    fun `confirmRegistration deberia normalizar el correo, confirmar y agregar al grupo USER`() {
        userService.confirmRegistration("  Nuevo@Example.com  ", "123456")

        verify(cognitoService, times(1)).confirmSignUpPublic("nuevo@example.com", "123456")
        verify(cognitoService, times(1)).addUserToGroup("nuevo@example.com", "USER")
    }

    @Test
    fun `confirmRegistration deberia completar aunque falle el agregado al grupo`() {
        whenever(cognitoService.addUserToGroup("user@example.com", "USER"))
            .thenThrow(RuntimeException("Cognito no disponible"))

        assertDoesNotThrow {
            userService.confirmRegistration("user@example.com", "123456")
        }
        verify(cognitoService, times(1)).confirmSignUpPublic("user@example.com", "123456")
    }

    @Test
    fun `confirmRegistration deberia propagar la excepcion si el codigo es invalido`() {
        whenever(cognitoService.confirmSignUpPublic("user@example.com", "000000"))
            .thenThrow(IllegalArgumentException("Codigo invalido"))

        assertThrows(IllegalArgumentException::class.java) {
            userService.confirmRegistration("user@example.com", "000000")
        }
        verify(cognitoService, never()).addUserToGroup(any(), any())
    }

    // ---------------------- resendConfirmationCode ----------------------

    @Test
    fun `resendConfirmationCode deberia normalizar el correo y delegar en Cognito`() {
        userService.resendConfirmationCode("  Nuevo@Example.com  ")

        verify(cognitoService, times(1)).resendConfirmationCode("nuevo@example.com")
    }

    // ---------------------- syncCurrentUser ----------------------

    @Test
    fun `syncCurrentUser deberia devolver el usuario existente por cognitoSub sin crear uno nuevo`() {
        val existing = AdminRosterUser(id = 5L, cognitoSub = "sub-123", name = "Ana", email = "ana@example.com", number = "0999")
        whenever(userRepository.findByCognitoSub("sub-123")).thenReturn(existing)

        val response = userService.syncCurrentUser("sub-123", "ana@example.com", "Ana Nueva")

        assertEquals(5L, response.id)
        assertEquals("Ana", response.name)
        verify(userRepository, never()).save(any())
    }

    @Test
    fun `syncCurrentUser deberia crear un usuario nuevo si no existe ni por sub ni por email`() {
        whenever(userRepository.findByCognitoSub("sub-999")).thenReturn(null)
        whenever(userRepository.findByEmail("nuevo@example.com")).thenReturn(null)
        whenever(userRepository.save(any<AdminRosterUser>())).thenAnswer { invocation ->
            val toSave = invocation.getArgument<AdminRosterUser>(0)
            AdminRosterUser(id = 42L, cognitoSub = toSave.cognitoSub, name = toSave.name, email = toSave.email, number = toSave.number)
        }

        val response = userService.syncCurrentUser("sub-999", "Nuevo@Example.com", "Nuevo Usuario")

        assertEquals(42L, response.id)
        assertEquals("Nuevo Usuario", response.name)
        verify(userRepository, times(1)).save(any())
    }
}