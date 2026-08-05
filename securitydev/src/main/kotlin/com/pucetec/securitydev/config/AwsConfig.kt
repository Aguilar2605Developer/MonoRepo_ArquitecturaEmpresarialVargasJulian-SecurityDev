package com.pucetec.securitydev.config

import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import software.amazon.awssdk.regions.Region
import software.amazon.awssdk.services.cognitoidentityprovider.CognitoIdentityProviderClient

@Configuration
class AwsConfig {

    private val logger = LoggerFactory.getLogger(AwsConfig::class.java)

    @Value("\${cognito.region}")
    private lateinit var region: String

    @Bean
    fun cognitoIdentityProviderClient(): CognitoIdentityProviderClient {
        logger.info("event=COGNITO_CLIENT_INIT msg=Configuring CognitoIdentityProviderClient region={}", region)
        return CognitoIdentityProviderClient.builder()
            .region(Region.of(region))
            .build()
    }
}
