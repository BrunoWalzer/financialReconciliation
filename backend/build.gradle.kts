plugins {
    java
    id("org.springframework.boot") version "3.5.16"
    id("io.spring.dependency-management") version "1.1.7"
}

group = "dev.fincore"
version = "0.1.0-SNAPSHOT"

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
}

repositories {
    mavenCentral()
}

dependencies {
    // HTTP: superfície da API e o tratamento central de erro RFC 9457.
    implementation("org.springframework.boot:spring-boot-starter-web")
    // Health com liveness/readiness, info e prometheus.
    implementation("org.springframework.boot:spring-boot-starter-actuator")
    // Persistência: primeira entidade do FINCORE é AuditEvent (M1). Traz spring-jdbc e
    // HikariCP transitivamente — não há starter-jdbc separado.
    implementation("org.springframework.boot:spring-boot-starter-data-jpa")
    // Migrations versionadas: o schema é a especificação de correção (TDS P2).
    implementation("org.flywaydb:flyway-core")

    runtimeOnly("org.flywaydb:flyway-database-postgresql")
    runtimeOnly("org.postgresql:postgresql")
    runtimeOnly("io.micrometer:micrometer-registry-prometheus")

    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("org.springframework.boot:spring-boot-testcontainers")
    testImplementation("org.testcontainers:junit-jupiter")
    testImplementation("org.testcontainers:postgresql")
    // Fronteiras arquiteturais verificadas por teste (TDS 4.4).
    testImplementation("com.tngtech.archunit:archunit:1.5.0")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

springBoot {
    // Alimenta /actuator/info com nome, versão e artefato.
    buildInfo()
}

tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
    options.compilerArgs.addAll(listOf("-Xlint:all", "-parameters"))
}

tasks.withType<Test>().configureEach {
    // Teste nao depende do idioma da maquina que o roda: fixture e comparacao de texto
    // se comportam igual no Windows do desenvolvedor e no Linux do CI.
    systemProperty("file.encoding", "UTF-8")
    testLogging {
        events("passed", "skipped", "failed")
        exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.SHORT
    }
}

// As três suítes compartilham o mesmo source set e são separadas por tag JUnit.
// A ordem é normativa (TDS 28): fronteira quebrada torna o resto ruído.
val architectureTest = tasks.register<Test>("architectureTest") {
    group = "verification"
    description = "Regras ArchUnit (TDS 4.4). Roda antes dos testes unitários."
    testClassesDirs = sourceSets.test.get().output.classesDirs
    classpath = sourceSets.test.get().runtimeClasspath
    useJUnitPlatform { includeTags("architecture") }
}

val integrationTest = tasks.register<Test>("integrationTest") {
    group = "verification"
    description = "Testes de integração com Testcontainers PostgreSQL."
    testClassesDirs = sourceSets.test.get().output.classesDirs
    classpath = sourceSets.test.get().runtimeClasspath
    useJUnitPlatform { includeTags("integration") }
}

tasks.named<Test>("test") {
    description = "Testes unitários: tudo que não é arquitetura nem integração."
    useJUnitPlatform { excludeTags("architecture", "integration") }
    mustRunAfter(architectureTest)
}

integrationTest.configure { mustRunAfter(tasks.named("test")) }

tasks.named("check") {
    dependsOn(architectureTest, integrationTest)
}
