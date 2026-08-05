package com.pucetec.securitydev.dto

import jakarta.validation.constraints.Email
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Pattern
import jakarta.validation.constraints.Size

// Datos crudos que llegan del formulario de registro público.
data class RegisterRequest(
    @field:NotBlank(message = "El correo es obligatorio")
    @field:Email(message = "El correo no tiene un formato válido")
    val email: String,

    @field:NotBlank(message = "El nombre es obligatorio")
    @field:Size(max = 100, message = "El nombre no puede superar 100 caracteres")
    val name: String,

    @field:NotBlank(message = "El teléfono es obligatorio")
    @field:Pattern(regexp = "^[0-9+\\-\\s]{7,20}$", message = "El teléfono no tiene un formato válido")
    val number: String,   // teléfono

    @field:NotBlank(message = "La contraseña es obligatoria")
    @field:Size(min = 8, max = 100, message = "La contraseña debe tener entre 8 y 100 caracteres")
    val password: String  // se valida (mín. 8 chars) también en UserService
)