package com.pucetec.users.mappers

import com.pucetec.users.dto.UserResponse
import com.pucetec.users.entities.User
import org.springframework.stereotype.Component

@Component
class UserMapper {

    fun toResponse(user: User): UserResponse {
        return UserResponse(
            id = user.id,
            cognitoId = user.cognitoId,
            name = user.name,
            email = user.email,
            phone = user.phone
        )
    }
}