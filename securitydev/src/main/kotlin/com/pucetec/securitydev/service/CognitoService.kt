package com.pucetec.securitydev.service

import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import software.amazon.awssdk.core.exception.SdkClientException
import software.amazon.awssdk.services.cognitoidentityprovider.CognitoIdentityProviderClient
import software.amazon.awssdk.services.cognitoidentityprovider.model.AdminAddUserToGroupRequest
import software.amazon.awssdk.services.cognitoidentityprovider.model.AdminDeleteUserRequest
import software.amazon.awssdk.services.cognitoidentityprovider.model.AdminGetUserRequest
import software.amazon.awssdk.services.cognitoidentityprovider.model.AttributeType
import software.amazon.awssdk.services.cognitoidentityprovider.model.CognitoIdentityProviderException
import software.amazon.awssdk.services.cognitoidentityprovider.model.ConfirmSignUpRequest
import software.amazon.awssdk.services.cognitoidentityprovider.model.ResendConfirmationCodeRequest
import software.amazon.awssdk.services.cognitoidentityprovider.model.SignUpRequest
import software.amazon.awssdk.services.cognitoidentityprovider.model.UserNotFoundException
import software.amazon.awssdk.services.cognitoidentityprovider.model.UsernameExistsException
import software.amazon.awssdk.services.cognitoidentityprovider.model.ListUsersRequest

// Resumen minimo de un usuario de Cognito, usado para la sincronizacion manual.
data class CognitoUserSummary(
    val sub: String,
    val email: String,
    val name: String,
    val status: String,
    val enabled: Boolean,
    val phoneNumber: String? = null
)

// Cubre el flujo PÚBLICO de auth (signup con código real al correo).
// Distinto de CognitoAdminService, que usa la API Admin* para altas
// hechas directamente por un admin humano.
@Service
class CognitoService(
    private val cognitoClient: CognitoIdentityProviderClient,
    @Value("\${cognito.user-pool-id}") private val userPoolId: String,
    @Value("\${cognito.client-id}") private val clientId: String,
    @Value("\${cognito.region}") private val region: String
) {

    private val logger = LoggerFactory.getLogger(CognitoService::class.java)

    // Inicia el registro público real: Cognito manda el código al correo.
    fun signUpPublic(email: String, name: String, phoneNumber: String, password: String): String {
        try {
            val request = SignUpRequest.builder()
                .clientId(clientId)
                .username(email)
                .password(password)
                .userAttributes(
                    AttributeType.builder().name("email").value(email).build(),
                    AttributeType.builder().name("name").value(name).build(),
                    AttributeType.builder().name("phone_number").value(phoneNumber).build()
                )
                .build()

            val response = cognitoClient.signUp(request)
            logger.info("event=SIGNUP_INITIATED msg=Public sign-up started, Cognito sent verification code email={}", email)
            return response.userSub()
        } catch (e: UsernameExistsException) {
            val status = getUserStatus(email)
            if (status == "UNCONFIRMED") {
                logger.warn("event=SIGNUP_UNCONFIRMED_RETRY msg=User already existed unconfirmed, resending code email={}", email)
                resendConfirmationCode(email)
                return getUserSub(email)
            }
            logger.warn("event=SIGNUP_ALREADY_CONFIRMED msg=User already exists and is confirmed in Cognito email={}", email)
            throw IllegalArgumentException("Ya existe una cuenta confirmada con ese correo. Intenta iniciar sesion.")
        } catch (e: CognitoIdentityProviderException) {
            logger.error("event=COGNITO_SIGNUP_FAILED msg=Cognito SignUp failed email={} aws={}", email, awsErrorSummary(e), e)
            throw RuntimeException("No se pudo iniciar el registro: ${awsErrorMessage(e)}", e)
        } catch (e: SdkClientException) {
            logger.error("event=COGNITO_SDK_SIGNUP_FAILED msg=AWS SDK could not execute SignUp email={}", email, e)
            throw RuntimeException("No se pudo iniciar el registro: ${e.message}", e)
        }
    }

    // Confirma con el código REAL enviado al correo. Recién aquí email_verified = true.
    fun confirmSignUpPublic(email: String, code: String) {
        try {
            val request = ConfirmSignUpRequest.builder()
                .clientId(clientId)
                .username(email)
                .confirmationCode(code)
                .build()
            cognitoClient.confirmSignUp(request)
            logger.info("event=EMAIL_CONFIRMED msg=User confirmed email with a real Cognito code email={}", email)
        } catch (e: CognitoIdentityProviderException) {
            logger.warn("event=CONFIRMATION_CODE_INVALID msg=Invalid or expired confirmation code email={} aws={}", email, awsErrorSummary(e))
            throw IllegalArgumentException("Codigo invalido o expirado: ${awsErrorMessage(e)}", e)
        } catch (e: SdkClientException) {
            logger.error("event=COGNITO_SDK_CONFIRM_FAILED msg=AWS SDK could not execute ConfirmSignUp email={}", email, e)
            throw RuntimeException("No se pudo confirmar la cuenta: ${e.message}", e)
        }
    }

    // Por si el codigo expiro o el correo no llego.
    fun resendConfirmationCode(email: String) {
        try {
            val request = ResendConfirmationCodeRequest.builder()
                .clientId(clientId)
                .username(email)
                .build()
            cognitoClient.resendConfirmationCode(request)
            logger.info("event=CONFIRMATION_CODE_RESENT msg=Confirmation code resent email={}", email)
        } catch (e: CognitoIdentityProviderException) {
            logger.error("event=RESEND_CODE_FAILED msg=Could not resend confirmation code email={} aws={}", email, awsErrorSummary(e), e)
            throw RuntimeException("No se pudo reenviar el codigo: ${awsErrorMessage(e)}", e)
        }
    }

    // Usado por AdminService/paneles: lectura administrativa, no fuerza verificacion de nada.
    fun getUserSub(email: String): String {
        return getUserAttributes(email)["sub"]
            ?: throw IllegalStateException("Cognito no devolvio el atributo sub para $email")
    }

    // Devuelve null si el usuario no existe en Cognito -> clave para detectar "huérfanos".
    fun getUserStatus(email: String): String? {
        return try {
            val request = AdminGetUserRequest.builder().userPoolId(userPoolId).username(email).build()
            cognitoClient.adminGetUser(request).userStatusAsString()
        } catch (e: UserNotFoundException) {
            null
        }
    }

    fun getUserAttributes(email: String): Map<String, String> {
        try {
            val request = AdminGetUserRequest.builder().userPoolId(userPoolId).username(email).build()
            val response = cognitoClient.adminGetUser(request)
            return response.userAttributes().associate { it.name() to it.value() }
        } catch (e: CognitoIdentityProviderException) {
            logger.error("event=COGNITO_ADMIN_GET_USER_FAILED msg=Cognito AdminGetUser failed email={} aws={}", email, awsErrorSummary(e), e)
            throw RuntimeException("No se pudo obtener el usuario de Cognito: ${awsErrorMessage(e)}", e)
        }
    }

    fun addUserToGroup(email: String, groupName: String) {
        try {
            val request = AdminAddUserToGroupRequest.builder()
                .userPoolId(userPoolId)
                .username(email)
                .groupName(groupName)
                .build()
            cognitoClient.adminAddUserToGroup(request)
            logger.info("event=USER_ADDED_TO_GROUP msg=User added to Cognito group email={} group={}", email, groupName)
        } catch (e: CognitoIdentityProviderException) {
            logger.error(
                "event=COGNITO_ADD_TO_GROUP_FAILED msg=Cognito AdminAddUserToGroup failed email={} group={} aws={}",
                email, groupName, awsErrorSummary(e), e
            )
            throw RuntimeException("No se pudo asignar el grupo al usuario: ${awsErrorMessage(e)}", e)
        }
    }

    // Rollback: borra un usuario de Cognito si algo falla despues del SignUp (confirmado o no).
    fun deleteUserIfExists(email: String) {
        try {
            val request = AdminDeleteUserRequest.builder().userPoolId(userPoolId).username(email).build()
            cognitoClient.adminDeleteUser(request)
            logger.info("event=COGNITO_ROLLBACK_DELETE msg=User deleted from Cognito during rollback email={}", email)
        } catch (e: Exception) {
            logger.error("event=COGNITO_ROLLBACK_DELETE_FAILED msg=Could not delete Cognito user during rollback email={}", email, e)
        }
    }

    // Trae TODOS los usuarios del User Pool (pagina automaticamente con
    // paginationToken, Cognito devuelve maximo 60 por página). Se usa para
    // el boton "Sincronizar con Cognito" del panel admin: compara esta lista
    // contra la BD local y crea las filas que falten, y tambien alimenta el
    // sync hacia users-microservice.
    fun listAllUsers(): List<CognitoUserSummary> {
        val allUsers = mutableListOf<CognitoUserSummary>()
        var paginationToken: String? = null

        try {
            do {
                val requestBuilder = ListUsersRequest.builder()
                    .userPoolId(userPoolId)
                    .limit(60)
                if (paginationToken != null) {
                    requestBuilder.paginationToken(paginationToken)
                }

                val response = cognitoClient.listUsers(requestBuilder.build())

                response.users().forEach { cognitoUser ->
                    val attributes = cognitoUser.attributes().associate { it.name() to it.value() }
                    val sub = attributes["sub"]
                    val email = attributes["email"]
                    if (sub != null && email != null) {
                        allUsers.add(
                            CognitoUserSummary(
                                sub = sub,
                                email = email,
                                name = attributes["name"] ?: email,
                                status = cognitoUser.userStatusAsString(),
                                enabled = cognitoUser.enabled(),
                                // Atributo estandar phone_number del User Pool
                                // (formato E.164, ej. +593999999999).
                                phoneNumber = attributes["phone_number"]
                            )
                        )
                    }
                }

                paginationToken = response.paginationToken()
            } while (paginationToken != null)
        } catch (e: CognitoIdentityProviderException) {
            logger.error("event=COGNITO_LIST_USERS_FAILED msg=Cognito ListUsers failed aws={}", awsErrorSummary(e), e)
            throw RuntimeException("No se pudo listar los usuarios de Cognito: ${awsErrorMessage(e)}", e)
        }

        return allUsers
    }

    // Helpers de logging: arman un mensaje de error legible desde la excepción AWS.
    private fun awsErrorMessage(e: CognitoIdentityProviderException): String {
        return e.awsErrorDetails()?.errorMessage() ?: e.message ?: e.javaClass.simpleName
    }

    private fun awsErrorSummary(e: CognitoIdentityProviderException): String {
        val details = e.awsErrorDetails()
        return "statusCode=${e.statusCode()}, requestId=${e.requestId()}, errorCode=${details?.errorCode()}, errorMessage=${details?.errorMessage()}"
    }
}