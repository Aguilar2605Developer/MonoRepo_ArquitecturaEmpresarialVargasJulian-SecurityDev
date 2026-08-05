package com.pucetec.securitydev.controller

import com.pucetec.securitydev.dto.ConfirmRegistrationRequest
import com.pucetec.securitydev.dto.RegisterRequest
import com.pucetec.securitydev.dto.ResendCodeRequest
import com.pucetec.securitydev.dto.SyncResponse
import com.pucetec.securitydev.security.CurrentUser
import com.pucetec.securitydev.service.UserService
import jakarta.validation.Valid
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.CrossOrigin
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import software.amazon.awssdk.services.cognitoidentityprovider.model.UsernameExistsException

// Flujo público de registro/login contra Cognito. El perfil completo
// (nombre, email, teléfono) vive en users-microservice, NO aquí.
@RestController
@RequestMapping("/api/users")
@CrossOrigin(origins = ["*"])
class UserController(private val userService: UserService) {

    private val logger = LoggerFactory.getLogger(UserController::class.java)

    // POST /api/users/register -> ruta pública. Crea el usuario en Cognito
    // (aún sin confirmar) y dispara el envío del código de verificación.
    @PostMapping("/register")
    fun registerUser(@Valid @RequestBody request: RegisterRequest): ResponseEntity<Any> {
        return try {
            userService.registerNewUser(request.email, request.name, request.number, request.password)
            ResponseEntity.status(HttpStatus.CREATED).body(
                mapOf("message" to "Cuenta creada. Revisa tu correo e ingresa el codigo para confirmarla.")
            )
        } catch (e: UsernameExistsException) {
            // Ya existe una cuenta confirmada con ese correo -> 409.
            logger.warn("event=REGISTER_REJECTED msg=Registration rejected, account already exists in Cognito email={}", request.email)
            ResponseEntity.status(HttpStatus.CONFLICT).body(mapOf("error" to "Ya existe una cuenta con ese correo."))
        } catch (e: IllegalArgumentException) {
            // Validación de negocio (ej. password corto) -> 400.
            logger.warn("event=REGISTER_REJECTED msg=Registration rejected email={} detail={}", request.email, e.message)
            ResponseEntity.status(HttpStatus.BAD_REQUEST).body(mapOf("error" to e.message))
        } catch (e: Exception) {
            logger.error("event=REGISTER_ERROR msg=Error processing POST /api/users/register email={}", request.email, e)
            ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(
                mapOf("error" to (e.message ?: "Error interno del servidor"))
            )
        }
    }

    // POST /api/users/confirm -> ruta pública. Valida el código real que
    // Cognito envió al correo; recién aquí email_verified pasa a true.
    @PostMapping("/confirm")
    fun confirmRegistration(@Valid @RequestBody request: ConfirmRegistrationRequest): ResponseEntity<Any> {
        return try {
            userService.confirmRegistration(request.email, request.code)
            ResponseEntity.ok(mapOf("message" to "Cuenta confirmada. Ya puedes iniciar sesion."))
        } catch (e: Exception) {
            logger.warn("event=CONFIRM_REJECTED msg=Confirmation rejected email={} detail={}", request.email, e.message)
            ResponseEntity.status(HttpStatus.BAD_REQUEST).body(mapOf("error" to e.message))
        }
    }

    // POST /api/users/resend-code -> ruta pública. Por si el código expiró
    // o el correo no llegó.
    @PostMapping("/resend-code")
    fun resendCode(@Valid @RequestBody request: ResendCodeRequest): ResponseEntity<Any> {
        return try {
            userService.resendConfirmationCode(request.email)
            ResponseEntity.ok(mapOf("message" to "Codigo reenviado."))
        } catch (e: Exception) {
            logger.error("event=RESEND_CODE_ERROR msg=Error resending confirmation code email={}", request.email, e)
            ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(
                mapOf("error" to (e.message ?: "Error interno del servidor"))
            )
        }
    }

    // POST /api/users/sync -> REQUIERE JWT válido. Get-or-create en el
    // roster local: si el usuario recién confirmó su cuenta y aún no
    // tiene fila local, la crea; si ya existe, solo devuelve su id/nombre.
    @PostMapping("/sync")
    fun syncCurrentUser(): ResponseEntity<Any> {
        val sub = CurrentUser.sub()
        val email = CurrentUser.email()
        val name = CurrentUser.name()

        // Defensa extra: si el JWT no trae los claims esperados -> 401.
        if (sub == null || email == null) {
            logger.warn("event=SYNC_INVALID_JWT_CLAIMS msg=POST /api/users/sync missing valid JWT claims sub={} email={}", sub, email)
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(
                mapOf("error" to "Token invalido: no se pudieron leer los claims del usuario.")
            )
        }

        return try {
            val response = userService.syncCurrentUser(sub, email, name ?: email)
            ResponseEntity.ok(response)
        } catch (e: Exception) {
            logger.error("event=SYNC_ERROR msg=Error processing POST /api/users/sync email={}", email, e)
            ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(
                mapOf("error" to (e.message ?: "Error interno del servidor"))
            )
        }
    }
}