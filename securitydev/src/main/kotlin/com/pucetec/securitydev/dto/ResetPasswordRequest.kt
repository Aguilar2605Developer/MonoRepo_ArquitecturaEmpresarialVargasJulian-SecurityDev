package com.pucetec.securitydev.dto

import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size

// Solo trae la nueva contraseña; el admin decide a quién resetear vía {id}.
data class ResetPasswordRequest(
    @field:NotBlank(message = "La contraseña es obligatoria")
    @field:Size(min = 8, max = 100, message = "La contraseña debe tener entre 8 y 100 caracteres")
    val newPassword: String
)