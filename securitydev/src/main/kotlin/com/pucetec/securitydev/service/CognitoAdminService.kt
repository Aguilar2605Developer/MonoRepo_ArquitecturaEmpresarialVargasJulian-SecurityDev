package com.pucetec.securitydev.service

import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import software.amazon.awssdk.regions.Region
import software.amazon.awssdk.services.cognitoidentityprovider.CognitoIdentityProviderClient
import software.amazon.awssdk.services.cognitoidentityprovider.model.*

// API de administración de Cognito (Admin*): usada SOLO cuando un admin
// aquí SÍ se marca email_verified=true sin que el usuario reciba código real.
@Service
class CognitoAdminService(
    @Value("\${cognito.user-pool-id}") private val userPoolId: String,
    @Value("\${cognito.region}") private val region: String
) {

    // Cliente propio (lazy), separado del bean de AwsConfig usado en CognitoService.
    private val client: CognitoIdentityProviderClient by lazy {
        CognitoIdentityProviderClient.builder()
            .region(Region.of(region))
            .build()
    }

    // Crea el usuario en Cognito y devuelve su 'sub' (identificador único e inmutable)
    fun createUser(email: String, name: String, temporaryPassword: String): String {
        val request = AdminCreateUserRequest.builder()
            .userPoolId(userPoolId)
            .username(email)
            .userAttributes(
                AttributeType.builder().name("email").value(email).build(),
                AttributeType.builder().name("email_verified").value("true").build(),
                AttributeType.builder().name("name").value(name).build()
            )
            .temporaryPassword(temporaryPassword)
            .messageAction(MessageActionType.SUPPRESS) // no manda el correo automático de Cognito
            .build()

        val response = client.adminCreateUser(request)
        return response.user().attributes()
            .first { it.name() == "sub" }
            .value()
    }

    fun addUserToGroup(email: String, groupName: String) {
        val request = AdminAddUserToGroupRequest.builder()
            .userPoolId(userPoolId)
            .username(email)
            .groupName(groupName)
            .build()
        client.adminAddUserToGroup(request)
    }

    // Fuerza password permanente sin flujo de "cambiar en primer login".
    fun resetPassword(email: String, newPassword: String) {
        val request = AdminSetUserPasswordRequest.builder()
            .userPoolId(userPoolId)
            .username(email)
            .password(newPassword)
            .permanent(true)
            .build()
        client.adminSetUserPassword(request)
    }

    fun deleteUser(email: String) {
        val request = AdminDeleteUserRequest.builder()
            .userPoolId(userPoolId)
            .username(email)
            .build()
        client.adminDeleteUser(request)
    }
}