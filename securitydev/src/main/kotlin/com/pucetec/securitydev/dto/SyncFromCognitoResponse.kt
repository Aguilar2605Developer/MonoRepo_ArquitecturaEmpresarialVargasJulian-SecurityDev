package com.pucetec.securitydev.dto

// Resumen de la sincronización manual admin vs. Cognito.
data class SyncFromCognitoResponse(
    val totalEnCognito: Int,             // cuántos usuarios trae Cognito en total
    val creados: List<String>,           // emails que se agregaron a la BD local
    val yaExistian: Int,                 // cuántos ya estaban sincronizados
    val omitidosNoConfirmados: List<String>, // emails sin confirmar, no se crean localmente
    val usersServiceSyncOk: Boolean          // false si la replicacion hacia users-microservice fallo (ver logs event=USERS_SERVICE_SYNC_FAILED)
)