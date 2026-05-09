plugins {
    id("org.springframework.boot")
    id("io.spring.dependency-management")
    kotlin("jvm")
    kotlin("plugin.spring")
}

dependencies {
    implementation(platform("org.springframework.boot:spring-boot-dependencies:3.2.4"))
    testImplementation(platform("org.springframework.boot:spring-boot-dependencies:3.2.4"))

    testImplementation("org.springframework.boot:spring-boot-starter-test")

    // REST Assured para llamadas HTTP a servicios desplegados
    testImplementation("io.rest-assured:rest-assured:5.4.0")
    testImplementation("io.rest-assured:json-path:5.4.0")
    testImplementation("io.rest-assured:spring-mock-mvc:5.4.0")
}

tasks.withType<Test> {
    useJUnitPlatform()
    // La URL base se inyecta desde el pipeline (ej: -Denv=stage)
    systemProperty("env", System.getProperty("env", "local"))
}
