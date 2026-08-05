package com.pucetec.securitydev.controller

import com.pucetec.securitydev.dto.HotSpotRequest
import com.pucetec.securitydev.dto.HotSpotResponse
import com.pucetec.securitydev.security.CurrentUser
import com.pucetec.securitydev.service.HotSpotService
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.access.AccessDeniedException
import org.springframework.web.bind.annotation.*

// CRUD de "puntos de peligro" reportados por usuarios, con reglas de
// propiedad: solo quien reportó el hotspot puede editarlo/borrarlo.
@RestController
@RequestMapping("/api/hotspots")
@CrossOrigin(origins = ["*"])
class HotSpotController(
    private val hotSpotService: HotSpotService
) {

    // POST /api/hotspots -> requiere JWT. El "sub" del token queda
    // guardado como reporterCognitoId del hotspot.
    @PostMapping
    fun createHotSpot(@Valid @RequestBody request: HotSpotRequest): ResponseEntity<HotSpotResponse> {
        val cognitoId = requireCurrentCognitoId()
        val savedHotSpot = hotSpotService.createHotSpot(request, cognitoId)
        return ResponseEntity(savedHotSpot, HttpStatus.CREATED)
    }

    // GET /api/hotspots -> lectura PÚBLICA (permitAll), solo hotspots activos.
    @GetMapping
    fun getAllHotSpots(): ResponseEntity<List<HotSpotResponse>> {
        return ResponseEntity.ok(hotSpotService.getAllHotSpots())
    }

    // GET /api/hotspots/{id} -> lectura pública de un hotspot puntual.
    @GetMapping("/{id}")
    fun getHotSpotById(@PathVariable id: Long): ResponseEntity<HotSpotResponse> {
        return ResponseEntity.ok(hotSpotService.getHotSpotById(id))
    }

    // PUT /api/hotspots/{id} -> solo el creador puede editar; si no
    // coincide el cognitoId -> 403 AccessDenied.
    @PutMapping("/{id}")
    fun updateHotSpot(@PathVariable id: Long, @Valid @RequestBody request: HotSpotRequest): ResponseEntity<HotSpotResponse> {
        val existing = hotSpotService.getHotSpotById(id)
        val cognitoId = requireCurrentCognitoId()
        if (existing.reporterCognitoId != null && existing.reporterCognitoId != cognitoId) {
            throw AccessDeniedException("No puedes editar un punto de peligro de otro usuario")
        }
        return ResponseEntity.ok(hotSpotService.updateHotSpot(id, request, cognitoId))
    }

    // PUT /api/hotspots/{id}/deactivate -> mismo chequeo de propiedad,
    // pero solo desactiva (soft delete) en vez de eliminar.
    @PutMapping("/{id}/deactivate")
    fun deactivateHotSpot(@PathVariable id: Long): ResponseEntity<HotSpotResponse> {
        val existing = hotSpotService.getHotSpotById(id)
        val cognitoId = requireCurrentCognitoId()
        if (existing.reporterCognitoId != null && existing.reporterCognitoId != cognitoId) {
            throw AccessDeniedException("No puedes desactivar un punto de peligro de otro usuario")
        }
        return ResponseEntity.ok(hotSpotService.deactivateHotSpot(id))
    }

    // DELETE /api/hotspots/{id} -> borrado definitivo, mismo chequeo de propiedad.
    @DeleteMapping("/{id}")
    fun deleteHotSpot(@PathVariable id: Long): ResponseEntity<Void> {
        val existing = hotSpotService.getHotSpotById(id)
        val cognitoId = requireCurrentCognitoId()
        if (existing.reporterCognitoId != null && existing.reporterCognitoId != cognitoId) {
            throw AccessDeniedException("No puedes eliminar un punto de peligro de otro usuario")
        }
        hotSpotService.deleteHotSpot(id)
        return ResponseEntity.noContent().build()
    }

    // Helper: extrae el "sub" del JWT validado; sin ID local, el sub ES
    // el identificador del usuario.
    private fun requireCurrentCognitoId(): String {
        return CurrentUser.sub()
            ?: throw AccessDeniedException("No se pudo verificar tu usuario. Vuelve a iniciar sesión.")
    }
}