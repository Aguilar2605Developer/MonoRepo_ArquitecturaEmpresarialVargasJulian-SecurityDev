package com.pucetec.users.security

import com.fasterxml.jackson.databind.ObjectMapper
import com.pucetec.users.controllers.UserController
import com.pucetec.users.dto.CognitoSyncRequest
import com.pucetec.users.dto.CognitoSyncResponse
import com.pucetec.users.dto.CognitoSyncUser
import com.pucetec.users.dto.UserRequest
import com.pucetec.users.dto.UserResponse
import com.pucetec.users.services.UserService
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.whenever
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest
import org.springframework.context.annotation.Import
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.oauth2.jwt.JwtDecoder
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.delete
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.post
import org.springframework.test.web.servlet.put

@WebMvcTest(UserController::class)
@Import(SecurityConfig::class)
class UserControllerSecurityTest {

    @Autowired
    private lateinit var mockMvc: MockMvc

    @MockitoBean
    private lateinit var userService: UserService

    @MockitoBean
    private lateinit var jwtDecoder: JwtDecoder

    private val objectMapper = ObjectMapper()

    private val sampleResponse = UserResponse(
        id = 1L, cognitoId = "cognito-sub-1", name = "Juan Perez",
        email = "juan@example.com", phone = "0999999999"
    )

    @Test
    fun `getAllUsers deberia responder 403 si el usuario autenticado no tiene rol ADMIN`() {
        mockMvc.get("/api/users") {
            with(jwt())
        }.andExpect {
            status { isForbidden() }
        }
    }

    @Test
    fun `getAllUsers deberia responder 200 si el usuario tiene rol ADMIN`() {
        whenever(userService.getAllUsers()).thenReturn(listOf(sampleResponse))

        mockMvc.get("/api/users") {
            with(jwt().authorities(SimpleGrantedAuthority("ROLE_ADMIN")))
        }.andExpect {
            status { isOk() }
        }
    }

    @Test
    fun `getUserById deberia responder 403 sin rol ADMIN`() {
        mockMvc.get("/api/users/1") {
            with(jwt())
        }.andExpect {
            status { isForbidden() }
        }
    }

    @Test
    fun `getUserByCognitoId deberia responder 403 sin rol ADMIN`() {
        mockMvc.get("/api/users/cognito/cognito-sub-1") {
            with(jwt())
        }.andExpect {
            status { isForbidden() }
        }
    }

    @Test
    fun `getUserByCognitoId deberia responder 200 con rol ADMIN`() {
        whenever(userService.getUserByCognitoId("cognito-sub-1")).thenReturn(sampleResponse)

        mockMvc.get("/api/users/cognito/cognito-sub-1") {
            with(jwt().authorities(SimpleGrantedAuthority("ROLE_ADMIN")))
        }.andExpect {
            status { isOk() }
        }
    }

    @Test
    fun `deleteUser deberia responder 403 sin rol ADMIN`() {
        mockMvc.delete("/api/users/1") {
            with(jwt())
        }.andExpect {
            status { isForbidden() }
        }
    }

    @Test
    fun `deleteUser deberia responder 204 con rol ADMIN`() {
        mockMvc.delete("/api/users/1") {
            with(jwt().authorities(SimpleGrantedAuthority("ROLE_ADMIN")))
        }.andExpect {
            status { isNoContent() }
        }
    }

    @Test
    fun `los endpoints administrativos deberian responder 401 sin ningun token`() {
        mockMvc.get("/api/users").andExpect {
            status { isUnauthorized() }
        }
    }

    @Test
    fun `getMe deberia responder 200 para un usuario autenticado sin rol ADMIN`() {
        whenever(userService.getMe(any())).thenReturn(sampleResponse)

        mockMvc.get("/api/users/me") {
            with(jwt())
        }.andExpect {
            status { isOk() }
        }
    }

    @Test
    fun `createMe deberia responder 200 para un usuario autenticado sin rol ADMIN`() {
        val request = UserRequest(name = "Juan Perez", email = "juan@example.com", phone = "0999999999")
        whenever(userService.createOrUpdateMe(any(), any())).thenReturn(sampleResponse)

        mockMvc.post("/api/users/me") {
            with(jwt())
            contentType = org.springframework.http.MediaType.APPLICATION_JSON
            content = objectMapper.writeValueAsString(request)
        }.andExpect {
            status { isOk() }
        }
    }

    @Test
    fun `updateMe deberia responder 200 para un usuario autenticado sin rol ADMIN`() {
        val request = UserRequest(name = "Juan Perez", email = "juan@example.com", phone = "0999999999")
        whenever(userService.createOrUpdateMe(any(), any())).thenReturn(sampleResponse)

        mockMvc.put("/api/users/me") {
            with(jwt())
            contentType = org.springframework.http.MediaType.APPLICATION_JSON
            content = objectMapper.writeValueAsString(request)
        }.andExpect {
            status { isOk() }
        }
    }

    @Test
    fun `me deberia responder 401 sin ningun token`() {
        mockMvc.get("/api/users/me").andExpect {
            status { isUnauthorized() }
        }
    }

    @Test
    fun `syncFromCognito deberia responder 401 sin ningun token`() {
        val request = CognitoSyncRequest(
            users = listOf(CognitoSyncUser(cognitoId = "sub-1", email = null, name = "Nuevo", phone = null))
        )

        mockMvc.post("/api/users/admin/sync-from-cognito") {
            contentType = org.springframework.http.MediaType.APPLICATION_JSON
            content = objectMapper.writeValueAsString(request)
        }.andExpect {
            status { isUnauthorized() }
        }
    }

    @Test
    fun `syncFromCognito deberia responder 403 si el usuario autenticado no tiene rol ADMIN`() {
        val request = CognitoSyncRequest(
            users = listOf(CognitoSyncUser(cognitoId = "sub-1", email = null, name = "Nuevo", phone = null))
        )

        mockMvc.post("/api/users/admin/sync-from-cognito") {
            with(jwt())
            contentType = org.springframework.http.MediaType.APPLICATION_JSON
            content = objectMapper.writeValueAsString(request)
        }.andExpect {
            status { isForbidden() }
        }
    }

    @Test
    fun `syncFromCognito deberia responder 200 con rol ADMIN`() {
        val request = CognitoSyncRequest(
            users = listOf(CognitoSyncUser(cognitoId = "sub-1", email = null, name = "Nuevo", phone = null))
        )
        whenever(userService.syncFromCognito(any())).thenReturn(
            CognitoSyncResponse(totalRecibidos = 1, creados = 1, actualizados = 0)
        )

        mockMvc.post("/api/users/admin/sync-from-cognito") {
            with(jwt().authorities(SimpleGrantedAuthority("ROLE_ADMIN")))
            contentType = org.springframework.http.MediaType.APPLICATION_JSON
            content = objectMapper.writeValueAsString(request)
        }.andExpect {
            status { isOk() }
        }
    }
}