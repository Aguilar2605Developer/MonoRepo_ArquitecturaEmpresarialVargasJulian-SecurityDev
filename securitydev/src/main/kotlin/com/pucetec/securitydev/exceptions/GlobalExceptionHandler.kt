package com.pucetec.securitydev.exceptions

import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.http.converter.HttpMessageNotReadableException
import org.springframework.security.access.AccessDeniedException
import org.springframework.web.bind.MethodArgumentNotValidException
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException
import software.amazon.awssdk.awscore.exception.AwsServiceException

// Manejador centralizado de errores para TODOS los controllers: convierte
// cada excepción en un HTTP status + body coherente, en vez de dejar que
// cada controller haga su propio try/catch repetitivo.
@RestControllerAdvice
class GlobalExceptionHandler {

    private val logger = LoggerFactory.getLogger(GlobalExceptionHandler::class.java)

    // Hotspot no encontrado -> 404, devuelve el mensaje tal cual.
    @ExceptionHandler(HotSpotNotFoundException::class)
    fun handleHotSpotNotFound(ex: HotSpotNotFoundException): ResponseEntity<String> {
        return ResponseEntity(ex.message, HttpStatus.NOT_FOUND)
    }

    // Location-share no encontrado/expirado -> 404.
    @ExceptionHandler(LocationShareNotFoundException::class)
    fun handleLocationShareNotFound(ex: LocationShareNotFoundException): ResponseEntity<String> {
        return ResponseEntity(ex.message, HttpStatus.NOT_FOUND)
    }

    // JSON mal formado en el body de la request -> 400 genérico (no expone detalles internos).
    @ExceptionHandler(HttpMessageNotReadableException::class)
    fun handleMalformedJson(ex: HttpMessageNotReadableException): ResponseEntity<String> {
        logger.warn("event=MALFORMED_JSON msg=Malformed or incomplete request body", ex)
        return ResponseEntity("El cuerpo de la solicitud esta mal formado o incompleto", HttpStatus.BAD_REQUEST)
    }

    // Fallos de validación (@Valid en un DTO) -> 400 con mapa campo->mensaje.
    @ExceptionHandler(MethodArgumentNotValidException::class)
    fun handleValidationErrors(ex: MethodArgumentNotValidException): ResponseEntity<Map<String, String?>> {
        val errors = ex.bindingResult.fieldErrors.associate { it.field to it.defaultMessage }
        return ResponseEntity(errors, HttpStatus.BAD_REQUEST)
    }

    // Un @PathVariable/@RequestParam con tipo incorrecto (ej. id="abc" en vez de Long) -> 400.
    @ExceptionHandler(MethodArgumentTypeMismatchException::class)
    fun handleTypeMismatch(ex: MethodArgumentTypeMismatchException): ResponseEntity<String> {
        return ResponseEntity("El parametro '${ex.name}' tiene un formato invalido", HttpStatus.BAD_REQUEST)
    }

    // Validaciones de negocio lanzadas manualmente (ej. password corto, email duplicado) -> 400.
    @ExceptionHandler(IllegalArgumentException::class)
    fun handleIllegalArgument(ex: IllegalArgumentException): ResponseEntity<String> {
        logger.warn("event=INVALID_ARGUMENT msg=Invalid argument detail={}", ex.message)
        return ResponseEntity(ex.message, HttpStatus.BAD_REQUEST)
    }

    // Reglas de propiedad violadas (ej. "no puedes editar el hotspot de otro") -> 403.
    @ExceptionHandler(AccessDeniedException::class)
    fun handleAccessDenied(ex: AccessDeniedException): ResponseEntity<String> {
        logger.warn("event=ACCESS_DENIED msg=Access denied detail={}", ex.message)
        return ResponseEntity(ex.message, HttpStatus.FORBIDDEN)
    }

    // Cualquier fallo real de AWS (Cognito, etc.) -> 500 con detalles del
    // error de AWS incluidos en la respuesta (útil para debug, ver logs).
    @ExceptionHandler(AwsServiceException::class)
    fun handleAwsServiceException(ex: AwsServiceException): ResponseEntity<Map<String, String?>> {
        val details = ex.awsErrorDetails()
        logger.error(
            "event=AWS_SERVICE_EXCEPTION msg=AWS call failed statusCode={} requestId={} errorCode={} errorMessage={}",
            ex.statusCode(),
            ex.requestId(),
            details?.errorCode(),
            details?.errorMessage(),
            ex
        )
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(
            mapOf(
                "error" to "Error llamando a AWS",
                "awsErrorCode" to details?.errorCode(),
                "awsMessage" to details?.errorMessage(),
                "requestId" to ex.requestId()
            )
        )
    }

    // Red de seguridad para RuntimeException no capturadas explícitamente
    // más arriba (ej. "Usuario no encontrado con ID: X" lanzado con RuntimeException a secas) -> 500.
    @ExceptionHandler(RuntimeException::class)
    fun handleRuntimeException(ex: RuntimeException): ResponseEntity<Map<String, String>> {
        logger.error("event=UNHANDLED_RUNTIME_EXCEPTION msg=Unhandled RuntimeException", ex)
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(
            mapOf("error" to (ex.message ?: "Error interno del servidor"))
        )
    }

    // Última red de seguridad: cualquier Exception que se escape de todo lo anterior -> 500.
    @ExceptionHandler(Exception::class)
    fun handleGenericException(ex: Exception): ResponseEntity<Map<String, String>> {
        logger.error("event=UNHANDLED_EXCEPTION msg=Unhandled exception", ex)
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(
            mapOf("error" to (ex.message ?: "Error interno del servidor"))
        )
    }
}