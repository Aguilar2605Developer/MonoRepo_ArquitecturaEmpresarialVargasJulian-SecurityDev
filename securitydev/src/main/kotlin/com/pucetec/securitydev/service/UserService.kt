package com.pucetec.securitydev.service

import com.pucetec.securitydev.dto.SyncResponse
import com.pucetec.securitydev.entity.AdminRosterUser
import com.pucetec.securitydev.repository.AdminRosterUserRepository
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service

// Orquesta el flujo público de registro contra Cognito. El perfil completo
// (nombre, email, teléfono) vive en users-microservice; aquí solo se
// mantiene un roster local minimo (tabla `admin_roster_users`) usado por
// el panel admin y por syncCurrentUser para devolver un id numerico local
// al frontend.
@Service
class UserService(
    private val cognitoService: CognitoService,
    private val userRepository: AdminRosterUserRepository
) {

    private val logger = LoggerFactory.getLogger(UserService::class.java)

    // Validación mínima + delega el signUp real a CognitoService.
    fun registerNewUser(email: String, name: String, number: String, password: String) {
        val normalizedEmail = email.trim().lowercase()

        val cleanPassword = password.trim()
        if (cleanPassword.length < 8) {
            throw IllegalArgumentException("La contraseña debe tener al menos 8 caracteres.")
        }

        cognitoService.signUpPublic(normalizedEmail, name, number, cleanPassword)
        logger.info("event=SIGNUP_STARTED msg=Sign-up started, pending code confirmation email={}", normalizedEmail)
    }

    // Confirma el código real de Cognito y, si funciona, agrega al usuario
    // al grupo "USER" (fallo en esto NO revierte la confirmación).
    fun confirmRegistration(email: String, code: String) {
        val normalizedEmail = email.trim().lowercase()

        cognitoService.confirmSignUpPublic(normalizedEmail, code)

        try {
            cognitoService.addUserToGroup(normalizedEmail, "USER")
        } catch (groupError: Exception) {
            logger.error(
                "event=ADD_TO_GROUP_FAILED msg=Could not add user to USER group, registration continues since email is already confirmed email={}",
                normalizedEmail,
                groupError
            )
        }

        logger.info("event=USER_CONFIRMED msg=User confirmed in Cognito email={}", normalizedEmail)
    }

    fun resendConfirmationCode(email: String) {
        cognitoService.resendConfirmationCode(email.trim().lowercase())
    }

    // Get-or-create en el roster local a partir de los claims ya validados
    // del JWT. Si el usuario no existe todavia (login justo despues de
    // confirmar su cuenta en Cognito), se crea una fila minima; si ya
    // existe, se devuelve su id y nombre actuales sin pisar otros campos
    // (por ejemplo el "number", que puede haberse editado desde el panel admin).
    fun syncCurrentUser(cognitoSub: String, email: String, name: String): SyncResponse {
        val normalizedEmail = email.trim().lowercase()

        // Busca primero por sub (más confiable), luego por email como fallback.
        val existing = userRepository.findByCognitoSub(cognitoSub)
            ?: userRepository.findByEmail(normalizedEmail)

        if (existing != null) {
            return SyncResponse(id = existing.id, name = existing.name)
        }

        val created = userRepository.save(
            AdminRosterUser(
                cognitoSub = cognitoSub,
                name = name,
                email = normalizedEmail,
                number = ""
            )
        )
        logger.info("event=USER_CREATED_LOCAL_ROSTER msg=User created in local roster via /api/users/sync email={}", normalizedEmail)

        return SyncResponse(id = created.id, name = created.name)
    }
}