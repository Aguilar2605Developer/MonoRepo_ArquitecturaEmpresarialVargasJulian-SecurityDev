package com.pucetec.securitydev.exceptions

// Excepción para cuando un shareId no existe o ya expiró.
// Lanzada desde LocationShareService (getByShareId, updateLocation, stopSharing).
class LocationShareNotFoundException(message: String) : RuntimeException(message)