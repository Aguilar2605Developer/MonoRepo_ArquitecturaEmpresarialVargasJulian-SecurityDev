package com.pucetec.securitydev.controller

import com.pucetec.securitydev.dto.LocationShareRequest
import com.pucetec.securitydev.dto.LocationShareResponse
import com.pucetec.securitydev.entity.LocationShareRecipient
import com.pucetec.securitydev.repository.LocationShareRecipientRepository
import com.pucetec.securitydev.security.CurrentUser
import com.pucetec.securitydev.service.EmailService
import com.pucetec.securitydev.service.LocationShareService
import jakarta.validation.Valid
import jakarta.validation.constraints.Email
import jakarta.validation.constraints.NotBlank
import org.springframework.http.ResponseEntity
import org.springframework.security.access.AccessDeniedException
import org.springframework.web.bind.annotation.*

// Compartir ubicación en tiempo real por shareId, con lista de
// destinatarios autorizados por email. El más sensible en permisos.
@RestController
@RequestMapping("/api/location-shares")
class LocationShareController(
    private val locationShareService: LocationShareService,
    private val emailService: EmailService,
    private val locationShareRecipientRepository: LocationShareRecipientRepository
) {

    data class ShareEmailRequest(
        @field:NotBlank(message = "El correo es obligatorio")
        @field:Email(message = "El correo no tiene un formato válido")
        val email: String
    )

    // POST /api/location-shares -> requiere JWT. Crea un share nuevo,
    // el dueño queda fijado al "sub" del token.
    @PostMapping
    fun startSharing(@Valid @RequestBody request: LocationShareRequest): ResponseEntity<LocationShareResponse> {
        val cognitoId = requireCurrentCognitoId()
        val response = locationShareService.startSharing(request, cognitoId)
        return ResponseEntity.ok(response)
    }

    // PUT /{shareId} -> actualiza lat/lng; SOLO el dueño puede mover su ubicación.
    @PutMapping("/{shareId}")
    fun updateLocation(
        @PathVariable shareId: String,
        @Valid @RequestBody request: LocationShareRequest
    ): ResponseEntity<LocationShareResponse> {
        val existing = locationShareService.getByShareId(shareId)
        requireOwner(existing.ownerCognitoId, "No puedes actualizar la ubicación de otro usuario")
        val response = locationShareService.updateLocation(shareId, request.latitude, request.longitude)
        return ResponseEntity.ok(response)
    }

    // PUT /{shareId}/stop -> SOLO el dueño puede terminar de compartir.
    @PutMapping("/{shareId}/stop")
    fun stopSharing(@PathVariable shareId: String): ResponseEntity<LocationShareResponse> {
        val existing = locationShareService.getByShareId(shareId)
        requireOwner(existing.ownerCognitoId, "No puedes detener la ubicación de otro usuario")
        val response = locationShareService.stopSharing(shareId)
        return ResponseEntity.ok(response)
    }

    // GET /{shareId} -> lectura con doble camino de autorización:
    // (a) eres el dueño, o (b) tu email (verificado) está en la lista
    // de destinatarios de este share concreto.
    @GetMapping("/{shareId}")
    fun getByShareId(@PathVariable shareId: String): ResponseEntity<LocationShareResponse> {
        val response = locationShareService.getByShareId(shareId)

        val currentCognitoId = CurrentUser.sub()
        val isOwner = response.ownerCognitoId != null && response.ownerCognitoId == currentCognitoId

        if (!isOwner) {
            // No es el dueño -> debe ser un destinatario con correo verificado.
            if (!CurrentUser.emailVerified()) {
                throw AccessDeniedException("Tu correo no esta verificado. Confirma tu cuenta antes de continuar.")
            }
            val callerEmail = CurrentUser.email()?.trim()?.lowercase()
                ?: throw AccessDeniedException("No autorizado para ver esta ubicación")

            // Chequeo contra la tabla de recipients: ¿este email fue invitado a ESTE shareId?
            val isAuthorizedRecipient = locationShareRecipientRepository
                .existsByLocationShareShareIdAndEmail(shareId, callerEmail)

            if (!isAuthorizedRecipient) {
                throw AccessDeniedException("Esta ubicación no fue compartida con tu correo")
            }
        }

        return ResponseEntity.ok(response)
    }

    // POST /{shareId}/share-email -> SOLO el dueño invita a un nuevo
    // destinatario (lo guarda + envía email de notificación).
    @PostMapping("/{shareId}/share-email")
    fun sendShareEmail(
        @PathVariable shareId: String,
        @Valid @RequestBody request: ShareEmailRequest
    ): ResponseEntity<Any> {
        val shareResponse = locationShareService.getByShareId(shareId)
        requireOwner(shareResponse.ownerCognitoId, "Solo el dueño puede compartir esta ubicación")

        val normalizedEmail = request.email.trim().lowercase()
        val shareEntity = locationShareService.getEntityByShareId(shareId)

        // Evita duplicar el destinatario si ya estaba invitado.
        if (!locationShareRecipientRepository.existsByLocationShareShareIdAndEmail(shareId, normalizedEmail)) {
            locationShareRecipientRepository.save(
                LocationShareRecipient(locationShare = shareEntity, email = normalizedEmail)
            )
        }

        emailService.sendLocationShareEmail(
            toEmail = normalizedEmail,
            username = CurrentUser.name() ?: CurrentUser.email() ?: "Usuario",
            shareId = shareId
        )

        return ResponseEntity.ok(mapOf("message" to "Correo enviado a $normalizedEmail"))
    }

    // GET /{shareId}/recipients -> SOLO el dueño ve la lista de invitados.
    @GetMapping("/{shareId}/recipients")
    fun listRecipients(@PathVariable shareId: String): ResponseEntity<List<String>> {
        val shareResponse = locationShareService.getByShareId(shareId)
        requireOwner(shareResponse.ownerCognitoId, "Solo el dueño puede ver los destinatarios")
        val emails = locationShareRecipientRepository.findByLocationShareShareId(shareId).map { it.email }
        return ResponseEntity.ok(emails)
    }

    // DELETE /{shareId}/recipients/{email} -> SOLO el dueño revoca acceso
    // a un destinatario puntual.
    @DeleteMapping("/{shareId}/recipients/{email}")
    fun revokeRecipient(@PathVariable shareId: String, @PathVariable email: String): ResponseEntity<Void> {
        val shareResponse = locationShareService.getByShareId(shareId)
        requireOwner(shareResponse.ownerCognitoId, "Solo el dueño puede revocar destinatarios")
        locationShareRecipientRepository.deleteByLocationShareShareIdAndEmail(shareId, email.trim().lowercase())
        return ResponseEntity.noContent().build()
    }

    // Helper: obtiene el "sub" del token o rechaza la petición.
    private fun requireCurrentCognitoId(): String {
        return CurrentUser.sub()
            ?: throw AccessDeniedException("No se pudo verificar tu usuario. Vuelve a iniciar sesión.")
    }

    // Helper: valida que el usuario actual sea el dueño registrado del share.
    private fun requireOwner(shareOwnerCognitoId: String?, message: String) {
        val currentCognitoId = CurrentUser.sub()
        if (shareOwnerCognitoId == null || shareOwnerCognitoId != currentCognitoId) {
            throw AccessDeniedException(message)
        }
    }
}