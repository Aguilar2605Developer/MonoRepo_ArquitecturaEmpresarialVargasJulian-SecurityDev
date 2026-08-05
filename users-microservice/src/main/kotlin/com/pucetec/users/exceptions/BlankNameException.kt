package com.pucetec.users.exceptions

// Lanzada cuando el nombre viene vacío
class BlankNameException(message: String) : RuntimeException(message)