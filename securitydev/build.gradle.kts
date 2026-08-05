plugins {
	kotlin("jvm") version "1.9.25"
	kotlin("plugin.spring") version "1.9.25"
	id("org.springframework.boot") version "3.4.0"
	id("io.spring.dependency-management") version "1.1.7"
	kotlin("plugin.jpa") version "1.9.25"
}

repositories {
	mavenCentral()
}

dependencies {
	implementation("org.springframework.boot:spring-boot-starter-mail")
	implementation("org.springframework.boot:spring-boot-starter-data-jpa")
	implementation("org.springframework.boot:spring-boot-starter-web")
	implementation("com.fasterxml.jackson.module:jackson-module-kotlin")
	implementation("org.jetbrains.kotlin:kotlin-reflect")

	runtimeOnly("com.h2database:h2")
	runtimeOnly("org.postgresql:postgresql")

	// Spring Security
	implementation("org.springframework.boot:spring-boot-starter-security")

	// Cognito: validación de JWT (Resource Server) + API de administración (crear/resetear/borrar usuarios)
	implementation("org.springframework.boot:spring-boot-starter-oauth2-resource-server")
	implementation("software.amazon.awssdk:cognitoidentityprovider:2.25.60")

	testImplementation("org.springframework.boot:spring-boot-starter-test")
	testImplementation("org.jetbrains.kotlin:kotlin-test-junit5")
	testRuntimeOnly("org.junit.platform:junit-platform-launcher")
	testImplementation("org.junit.jupiter:junit-jupiter:5.10.2")
	testImplementation("org.mockito:mockito-junit-jupiter:5.11.0")
	testImplementation("org.mockito.kotlin:mockito-kotlin:5.4.0")
	implementation("org.springframework.boot:spring-boot-starter-validation")

}

tasks.withType<Test> {
	useJUnitPlatform()
}

tasks.withType<Test> {
	useJUnitPlatform()
}