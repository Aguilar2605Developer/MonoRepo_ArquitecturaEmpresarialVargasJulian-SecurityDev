package com.pucetec.users.exceptions

// Lanzada cuando no existe un usuario con el id o cognitoId buscado.
class UserNotFoundException(message: String) : RuntimeException(message)