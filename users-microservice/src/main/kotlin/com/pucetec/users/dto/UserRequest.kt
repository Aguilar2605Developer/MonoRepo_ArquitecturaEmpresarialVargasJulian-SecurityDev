package com.pucetec.users.dto



data class UserRequest(
    val name: String = "",
    val email: String? = null,
    val phone: String? = null
)