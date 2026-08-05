package com.pucetec.users.exceptions

import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice

// Manejador centralizado, más simple que el de securitydev (menos tipos
// de excepción porque este microservicio es más chico y no llama a AWS).
@RestControllerAdvice
class GlobalExceptionHandler {

    private val logger = LoggerFactory.getLogger(GlobalExceptionHandler::class.java)

    // Usuario no encontrado -> 404.
    @ExceptionHandler(UserNotFoundException::class)
    fun handleUserNotFound(ex: UserNotFoundException): ResponseEntity<String> {
        return ResponseEntity(ex.message, HttpStatus.NOT_FOUND)
    }

    // cognitoId duplicado -> 409 (aunque, como se anotó arriba, no vi
    // dónde se lanza actualmente en el flujo real).
    @ExceptionHandler(DuplicateCognitoIdException::class)
    fun handleDuplicateCognitoId(ex: DuplicateCognitoIdException): ResponseEntity<String> {
        return ResponseEntity(ex.message, HttpStatus.CONFLICT)
    }

    // Nombre vacío -> 400.
    @ExceptionHandler(BlankNameException::class)
    fun handleBlankName(ex: BlankNameException): ResponseEntity<String> {
        return ResponseEntity(ex.message, HttpStatus.BAD_REQUEST)
    }

    // Red de seguridad: cualquier otra excepción no anticipada -> 500.
    @ExceptionHandler(Exception::class)
    fun handleGenericException(ex: Exception): ResponseEntity<Map<String, String>> {
        logger.error("event=UNHANDLED_EXCEPTION msg=Unhandled exception in users-microservice", ex)
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(
            mapOf("error" to (ex.message ?: "Error interno del servidor"))
        )
    }
}