package com.pucetec.securitydev.dto

import jakarta.validation.constraints.DecimalMax
import jakarta.validation.constraints.DecimalMin

// Coordenadas para iniciar o actualizar un compartir de ubicación.
class LocationShareRequest(
    @field:DecimalMin(value = "-90.0", message = "La latitud debe ser >= -90")
    @field:DecimalMax(value = "90.0", message = "La latitud debe ser <= 90")
    val latitude: Double = 0.0,

    @field:DecimalMin(value = "-180.0", message = "La longitud debe ser >= -180")
    @field:DecimalMax(value = "180.0", message = "La longitud debe ser <= 180")
    val longitude: Double = 0.0
)