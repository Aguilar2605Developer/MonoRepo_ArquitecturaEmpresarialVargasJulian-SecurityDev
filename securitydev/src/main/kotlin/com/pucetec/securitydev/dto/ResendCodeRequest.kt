package com.pucetec.securitydev.dto

import jakarta.validation.constraints.Email
import jakarta.validation.constraints.NotBlank

// Solo necesita el email para pedirle a Cognito que reenvíe el código.
data class ResendCodeRequest(
    @field:NotBlank(message = "El correo es obligatorio")
    @field:Email(message = "El correo no tiene un formato válido")
    val email: String
)