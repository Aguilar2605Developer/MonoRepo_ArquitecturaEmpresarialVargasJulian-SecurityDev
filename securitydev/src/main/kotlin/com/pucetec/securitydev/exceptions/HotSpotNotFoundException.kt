package com.pucetec.securitydev.exceptions

// Excepción para cuando un punto de peligro no existe en la base de datos.
// Lanzada desde HotSpotService (getHotSpotById, updateHotSpot, deactivate, delete).
class HotSpotNotFoundException(message: String) : RuntimeException(message)