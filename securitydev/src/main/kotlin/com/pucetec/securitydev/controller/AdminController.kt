package com.pucetec.securitydev.controller

import com.pucetec.securitydev.dto.*
import com.pucetec.securitydev.security.CurrentUser
import com.pucetec.securitydev.service.AdminService
import com.pucetec.securitydev.service.HotSpotService
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

// Panel administrativo. Protegido a nivel global en SecurityConfig con
// hasRole("ADMIN") sobre /api/admin/**.
@RestController
@RequestMapping("/api/admin")
@CrossOrigin(origins = ["*"])
class AdminController(
    private val adminService: AdminService,
    private val hotSpotService: HotSpotService
) {

    // GET /dashboard -> métricas generales (conteos, resumen) para el panel.
    @GetMapping("/dashboard")
    fun getDashboard(): ResponseEntity<DashboardResponse> {
        return ResponseEntity.ok(adminService.getDashboardStats())
    }

    // GET /users -> lista completa del roster local (no de Cognito directamente).
    @GetMapping("/users")
    fun getAllUsers(): ResponseEntity<List<UserAdminResponse>> {
        return ResponseEntity.ok(adminService.getAllUsers())
    }

    // GET /users/{id} -> detalle de un usuario local por id numérico.
    @GetMapping("/users/{id}")
    fun getUserById(@PathVariable id: Long): ResponseEntity<UserAdminResponse> {
        return ResponseEntity.ok(adminService.getUserById(id))
    }

    // POST /users -> alta manual de usuario (probablemente crea en Cognito + BD local).
    @PostMapping("/users")
    fun createUser(@Valid @RequestBody request: UserCreateRequest): ResponseEntity<UserAdminResponse> {
        return ResponseEntity(adminService.createUser(request), HttpStatus.CREATED)
    }

    // PUT /users/{id} -> edición de datos del usuario (admin, sin chequeo de dueño).
    @PutMapping("/users/{id}")
    fun updateUser(@PathVariable id: Long, @Valid @RequestBody request: UserUpdateRequest): ResponseEntity<UserAdminResponse> {
        return ResponseEntity.ok(adminService.updateUser(id, request))
    }

    // PUT /users/{id}/reset-password -> fuerza un cambio de contraseña (vía Cognito Admin API).
    @PutMapping("/users/{id}/reset-password")
    fun resetPassword(@PathVariable id: Long, @Valid @RequestBody request: ResetPasswordRequest): ResponseEntity<String> {
        adminService.resetPassword(id, request.newPassword)
        return ResponseEntity.ok("Contraseña actualizada")
    }

    // DELETE /users/{id} -> elimina usuario (local y/o Cognito, según AdminService).
    @DeleteMapping("/users/{id}")
    fun deleteUser(@PathVariable id: Long): ResponseEntity<Void> {
        adminService.deleteUser(id)
        return ResponseEntity.noContent().build()
    }

    // POST /users/purge-orphans -> limpia filas locales cuyo usuario ya no existe en Cognito.
    @PostMapping("/users/purge-orphans")
    fun purgeOrphanedUsers(): ResponseEntity<Map<String, Any>> {
        val removed = adminService.purgeOrphanedUsers()
        return ResponseEntity.ok(
            mapOf(
                "message" to "${removed.size} usuario(s) huerfano(s) eliminado(s)",
                "removedEmails" to removed
            )
        )
    }

    // POST /users/sync-from-cognito -> trae usuarios de Cognito que faltan
    // en la BD local y los crea (botón "Sincronizar" del panel).
    @PostMapping("/users/sync-from-cognito")
    fun syncUsersFromCognito(): ResponseEntity<SyncFromCognitoResponse> {
        return ResponseEntity.ok(adminService.syncUsersFromCognito())
    }

    // GET /hotspots -> TODOS los hotspots (activos e inactivos), a diferencia
    // del endpoint público que solo muestra los activos.
    @GetMapping("/hotspots")
    fun getAllHotSpots(): ResponseEntity<List<HotSpotResponse>> {
        return ResponseEntity.ok(hotSpotService.getAllHotSpotsAdmin())
    }

    // POST /hotspots -> el admin puede crear un hotspot igual que un usuario normal.
    @PostMapping("/hotspots")
    fun createHotSpot(@Valid @RequestBody request: HotSpotRequest): ResponseEntity<HotSpotResponse> {
        return ResponseEntity(hotSpotService.createHotSpot(request, CurrentUser.sub()), HttpStatus.CREATED)
    }

    // PUT /hotspots/{id} -> a diferencia de HotSpotController, aquí NO se
    // valida quién fue el reportero original: el admin puede editar cualquiera.
    @PutMapping("/hotspots/{id}")
    fun updateHotSpot(@PathVariable id: Long, @Valid @RequestBody request: HotSpotRequest): ResponseEntity<HotSpotResponse> {
        return ResponseEntity.ok(hotSpotService.updateHotSpot(id, request, CurrentUser.sub()))
    }

    // PUT /hotspots/{id}/deactivate -> desactiva cualquier hotspot sin chequeo de dueño.
    @PutMapping("/hotspots/{id}/deactivate")
    fun deactivateHotSpot(@PathVariable id: Long): ResponseEntity<HotSpotResponse> {
        return ResponseEntity.ok(hotSpotService.deactivateHotSpot(id))
    }

    // DELETE /hotspots/{id} -> borra cualquier hotspot sin chequeo de dueño.
    @DeleteMapping("/hotspots/{id}")
    fun deleteHotSpot(@PathVariable id: Long): ResponseEntity<Void> {
        hotSpotService.deleteHotSpot(id)
        return ResponseEntity.noContent().build()
    }
}