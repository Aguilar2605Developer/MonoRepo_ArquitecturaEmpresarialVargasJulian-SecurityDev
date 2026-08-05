package com.pucetec.securitydev.dto

import jakarta.validation.constraints.Email
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size

// Código de verificación que Cognito envió al correo del usuario.
data class ConfirmRegistrationRequest(
    @field:NotBlank(message = "El correo es obligatorio")
    @field:Email(message = "El correo no tiene un formato válido")
    val email: String,

    @field:NotBlank(message = "El código es obligatorio")
    @field:Size(min = 6, max = 6, message = "El código debe tener 6 dígitos")
    val code: String
)