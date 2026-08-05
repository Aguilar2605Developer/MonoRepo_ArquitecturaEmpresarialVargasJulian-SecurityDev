package com.pucetec.securitydev.dto

import jakarta.validation.constraints.DecimalMax
import jakarta.validation.constraints.DecimalMin
import jakarta.validation.constraints.Max
import jakarta.validation.constraints.Min
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size

// Datos que envía el usuario al reportar/editar un punto de peligro.
data class HotSpotRequest(
    @field:DecimalMin(value = "-90.0", message = "La latitud debe ser >= -90")
    @field:DecimalMax(value = "90.0", message = "La latitud debe ser <= 90")
    val latitude: Double,

    @field:DecimalMin(value = "-180.0", message = "La longitud debe ser >= -180")
    @field:DecimalMax(value = "180.0", message = "La longitud debe ser <= 180")
    val longitude: Double,

    @field:NotBlank(message = "La modalidad es obligatoria")
    @field:Size(max = 50, message = "La modalidad no puede superar 50 caracteres")
    val modality: String = "",       // tipo de incidente (robo, acoso, etc.)

    @field:NotBlank(message = "La descripción es obligatoria")
    @field:Size(max = 500, message = "La descripción no puede superar 500 caracteres")
    val description: String = "",

    @field:Min(value = 1, message = "La duración mínima es 1 hora")
    @field:Max(value = 720, message = "La duración máxima es 720 horas (30 días)")
    val durationHours: Long = 24,    // vida útil antes de auto-desactivarse

    @field:Min(value = 1, message = "Debe haber al menos 1 persona involucrada")
    @field:Max(value = 10000, message = "Valor de personas involucradas no realista")
    val peopleInvolved: Int
)