package com.pucetec.securitydev.dto

import jakarta.validation.constraints.Email
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Pattern
import jakarta.validation.constraints.Size

// Edición de datos básicos de un usuario desde el panel admin.
data class UserUpdateRequest(
    @field:NotBlank(message = "El nombre es obligatorio")
    @field:Size(max = 100, message = "El nombre no puede superar 100 caracteres")
    val name: String,

    @field:NotBlank(message = "El correo es obligatorio")
    @field:Email(message = "El correo no tiene un formato válido")
    val email: String,

    @field:NotBlank(message = "El teléfono es obligatorio")
    @field:Pattern(regexp = "^[0-9+\\-\\s]{7,20}$", message = "El teléfono no tiene un formato válido")
    val number: String
)