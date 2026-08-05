package com.pucetec.users.controllers

import com.pucetec.users.dto.CognitoSyncRequest
import com.pucetec.users.dto.CognitoSyncResponse
import com.pucetec.users.dto.UserRequest
import com.pucetec.users.dto.UserResponse
import com.pucetec.users.services.UserService
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.web.bind.annotation.*

// CRUD del perfil de usuario. A diferencia de securitydev, aquí SÍ vive
// el perfil completo (nombre, email, teléfono) — este es el dueño real
// de esos datos en toda la app.
@RestController
@RequestMapping("/api/users")
class UserController(
    private val userService: UserService
) {

    // POST /api/users/me -> requiere JWT (cualquier usuario autenticado).
    // "me" siempre opera sobre jwt.subject, nunca sobre un id que venga
    // del cliente, así que es intrínsecamente seguro sin chequeo extra de dueño.
    @PostMapping("/me")
    fun createMe(@AuthenticationPrincipal jwt: Jwt, @RequestBody request: UserRequest): ResponseEntity<UserResponse> {
        return ResponseEntity.ok(userService.createOrUpdateMe(jwt.subject, request))
    }

    // GET /api/users/me -> perfil del usuario logueado actualmente.
    @GetMapping("/me")
    fun getMe(@AuthenticationPrincipal jwt: Jwt): ResponseEntity<UserResponse> {
        return ResponseEntity.ok(userService.getMe(jwt.subject))
    }

    // PUT /api/users/me -> mismo comportamiento que POST /me (create-or-update).
    @PutMapping("/me")
    fun updateMe(@AuthenticationPrincipal jwt: Jwt, @RequestBody request: UserRequest): ResponseEntity<UserResponse> {
        return ResponseEntity.ok(userService.createOrUpdateMe(jwt.subject, request))
    }

    // GET /api/users -> lista TODOS los usuarios. Requiere rol ADMIN (ver SecurityConfig).
    @GetMapping
    fun getAllUsers(): ResponseEntity<List<UserResponse>> {
        return ResponseEntity.ok(userService.getAllUsers())
    }

    // GET /api/users/{id} -> perfil de CUALQUIER usuario por id local. Solo ADMIN.
    @GetMapping("/{id}")
    fun getUserById(@PathVariable id: Long): ResponseEntity<UserResponse> {
        return ResponseEntity.ok(userService.getUserById(id))
    }

    // GET /api/users/cognito/{cognitoId} -> lookup por el "sub" de Cognito
    // en vez del id numérico local. Solo ADMIN. Útil para que securitydev
    // (u otro consumidor) resuelva el perfil completo a partir del JWT.
    @GetMapping("/cognito/{cognitoId}")
    fun getUserByCognitoId(@PathVariable cognitoId: String): ResponseEntity<UserResponse> {
        return ResponseEntity.ok(userService.getUserByCognitoId(cognitoId))
    }

    // DELETE /api/users/{id} -> borra cualquier usuario por id. Solo ADMIN.
    @DeleteMapping("/{id}")
    fun deleteUser(@PathVariable id: Long): ResponseEntity<Void> {
        userService.deleteUser(id)
        return ResponseEntity.noContent().build()
    }

    // POST /api/users/admin/sync-from-cognito -> llamado internamente por
    // securitydev (no por el frontend). Recibe la lista completa de
    // usuarios de Cognito y hace upsert masivo por cognitoId. Solo ADMIN.
    @PostMapping("/admin/sync-from-cognito")
    fun syncFromCognito(@RequestBody request: CognitoSyncRequest): ResponseEntity<CognitoSyncResponse> {
        return ResponseEntity.ok(userService.syncFromCognito(request))
    }
}