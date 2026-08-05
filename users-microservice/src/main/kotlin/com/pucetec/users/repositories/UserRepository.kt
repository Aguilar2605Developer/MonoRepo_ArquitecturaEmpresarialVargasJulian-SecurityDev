package com.pucetec.users.repositories

import com.pucetec.users.entities.User
import org.springframework.data.jpa.repository.JpaRepository

// Único repository del microservicio. Nota: sin @Repository explícito
// (no hace falta — Spring Data JPA lo detecta igual por extender
// JpaRepository), a diferencia de los repos de securitydev que sí lo llevan.
interface UserRepository : JpaRepository<User, Long> {
    fun findByCognitoId(cognitoId: String): User?     // el lookup central: usado en casi todos los métodos de UserService
    fun existsByCognitoId(cognitoId: String): Boolean
}