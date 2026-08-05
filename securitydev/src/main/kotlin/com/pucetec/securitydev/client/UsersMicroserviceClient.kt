package com.pucetec.securitydev.client

import com.pucetec.securitydev.dto.UsersServiceSyncRequest
import com.pucetec.securitydev.dto.UsersServiceSyncResponse
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.HttpHeaders
import org.springframework.stereotype.Component
import org.springframework.web.client.RestClient
import org.springframework.web.client.RestClientException

// Único punto de integración server-to-server hacia users-microservice.
// Usa RestClient (incluido en spring-boot-starter-web, ya presente en el
// proyecto -- no hace falta agregar ninguna dependencia nueva).
@Component
class UsersMicroserviceClient(
    @Value("\${users-service.base-url}") private val baseUrl: String
) {

    private val logger = LoggerFactory.getLogger(UsersMicroserviceClient::class.java)

    private val restClient: RestClient by lazy {
        RestClient.builder().baseUrl(baseUrl).build()
    }

    // bearerToken: el JWT del admin que disparó el sync, reenviado tal cual.
    // Best-effort: si falla (servicio caído, red, 403, etc.) devuelve null
    // en vez de lanzar, para no tumbar el sync local que ya se hizo.
    fun syncUsers(request: UsersServiceSyncRequest, bearerToken: String): UsersServiceSyncResponse? {
        return try {
            restClient.post()
                .uri("/api/users/admin/sync-from-cognito")
                .header(HttpHeaders.AUTHORIZATION, "Bearer $bearerToken")
                .body(request)
                .retrieve()
                .body(UsersServiceSyncResponse::class.java)
        } catch (e: RestClientException) {
            logger.error(
                "event=USERS_SERVICE_SYNC_FAILED msg=No se pudo sincronizar hacia users-microservice baseUrl={}",
                baseUrl, e
            )
            null
        }
    }
}