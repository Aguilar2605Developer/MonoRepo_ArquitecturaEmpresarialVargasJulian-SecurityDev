package com.pucetec.securitydev.controller

import com.pucetec.securitydev.dto.ConfirmRegistrationRequest
import com.pucetec.securitydev.dto.RegisterRequest
import com.pucetec.securitydev.dto.ResendCodeRequest
import com.pucetec.securitydev.service.UserService
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.InjectMocks
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.*
import software.amazon.awssdk.services.cognitoidentityprovider.model.UsernameExistsException

@ExtendWith(MockitoExtension::class)
class UserControllerTest {

    @Mock
    private lateinit var userService: UserService

    @InjectMocks
    private lateinit var userController: UserController

    private lateinit var registerRequest: RegisterRequest
    private lateinit var confirmRequest: ConfirmRegistrationRequest
    private lateinit var resendRequest: ResendCodeRequest

    @BeforeEach
    fun setUp() {
        registerRequest = RegisterRequest(
            email = "juan@example.com",
            name = "Juan Perez",
            number = "0999999999",
            password = "clave1234"
        )
        confirmRequest = ConfirmRegistrationRequest(email = "juan@example.com", code = "123456")
        resendRequest = ResendCodeRequest(email = "juan@example.com")
    }

    // ---------------------- registerUser ----------------------

    @Test
    fun `registerUser deberia devolver 201 cuando el registro es exitoso`() {
        doNothing().whenever(userService).registerNewUser(
            registerRequest.email, registerRequest.name, registerRequest.number, registerRequest.password
        )

        val result = userController.registerUser(registerRequest)

        assertEquals(201, result.statusCode.value())
        verify(userService, times(1)).registerNewUser(
            registerRequest.email, registerRequest.name, registerRequest.number, registerRequest.password
        )
    }

    @Test
    fun `registerUser deberia devolver 409 cuando el correo ya existe en Cognito`() {
        whenever(
            userService.registerNewUser(
                registerRequest.email, registerRequest.name, registerRequest.number, registerRequest.password
            )
        ).thenThrow(UsernameExistsException.builder().message("ya existe").build())

        val result = userController.registerUser(registerRequest)

        assertEquals(409, result.statusCode.value())
    }

    @Test
    fun `registerUser deberia devolver 400 cuando los datos son invalidos`() {
        whenever(
            userService.registerNewUser(
                registerRequest.email, registerRequest.name, registerRequest.number, registerRequest.password
            )
        ).thenThrow(IllegalArgumentException("La contraseña debe tener al menos 8 caracteres."))

        val result = userController.registerUser(registerRequest)

        assertEquals(400, result.statusCode.value())
        val body = result.body as Map<*, *>
        assertTrue((body["error"] as String).contains("8 caracteres"))
    }

    @Test
    fun `registerUser deberia devolver 500 cuando ocurre un error inesperado`() {
        whenever(
            userService.registerNewUser(
                registerRequest.email, registerRequest.name, registerRequest.number, registerRequest.password
            )
        ).thenThrow(RuntimeException("AWS no disponible"))

        val result = userController.registerUser(registerRequest)

        assertEquals(500, result.statusCode.value())
    }

    // ---------------------- confirmRegistration ----------------------

    @Test
    fun `confirmRegistration deberia devolver 200 cuando el codigo es valido`() {
        doNothing().whenever(userService).confirmRegistration(confirmRequest.email, confirmRequest.code)

        val result = userController.confirmRegistration(confirmRequest)

        assertEquals(200, result.statusCode.value())
        verify(userService, times(1)).confirmRegistration(confirmRequest.email, confirmRequest.code)
    }

    @Test
    fun `confirmRegistration deberia devolver 400 cuando el codigo es invalido o expiro`() {
        whenever(userService.confirmRegistration(confirmRequest.email, confirmRequest.code))
            .thenThrow(IllegalArgumentException("Codigo invalido o expirado"))

        val result = userController.confirmRegistration(confirmRequest)

        assertEquals(400, result.statusCode.value())
    }

    // ---------------------- resendCode ----------------------

    @Test
    fun `resendCode deberia devolver 200 cuando el reenvio es exitoso`() {
        doNothing().whenever(userService).resendConfirmationCode(resendRequest.email)

        val result = userController.resendCode(resendRequest)

        assertEquals(200, result.statusCode.value())
        verify(userService, times(1)).resendConfirmationCode(resendRequest.email)
    }

    @Test
    fun `resendCode deberia devolver 500 cuando ocurre un error inesperado`() {
        whenever(userService.resendConfirmationCode(resendRequest.email))
            .thenThrow(RuntimeException("AWS no disponible"))

        val result = userController.resendCode(resendRequest)

        assertEquals(500, result.statusCode.value())
    }
}