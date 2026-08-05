package com.pucetec.securitydev

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication
import org.springframework.scheduling.annotation.EnableScheduling

@SpringBootApplication
@EnableScheduling
class SecuritydevApplication

fun main(args: Array<String>) {
	runApplication<SecuritydevApplication>(*args)
}