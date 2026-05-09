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
    testImplementation("org.springframework.boot:spring-boot-starter-web")
    testImplementation("org.springframework.kafka:spring-kafka")
    testImplementation("org.springframework.kafka:spring-kafka-test")
    testImplementation("org.springframework.boot:spring-boot-starter-data-redis")

    // TestContainers
    testImplementation("org.testcontainers:junit-jupiter:1.19.3")
    testImplementation("org.testcontainers:kafka:1.19.3")
    testImplementation("org.testcontainers:postgresql:1.19.3")

    // HTTP client para llamadas REST entre servicios
    testImplementation("io.rest-assured:rest-assured:5.4.0")
    testImplementation("io.rest-assured:json-path:5.4.0")
}

tasks.withType<Test> {
    useJUnitPlatform()
}
