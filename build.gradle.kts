import org.gradle.process.CommandLineArgumentProvider

plugins {
    kotlin("jvm") version "2.3.21"
    kotlin("plugin.spring") version "2.3.21"
    id("org.springframework.boot") version "4.1.2"
    id("io.spring.dependency-management") version "1.1.7"
    id("org.graalvm.buildtools.native") version "1.1.14"
}

group = "com.mosta"
version = "0.0.1-SNAPSHOT"

springBoot {
    mainClass.set("com.mosta.coveringletter.CoveringletterApplicationKt")
}

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(25)
    }
}

repositories {
    mavenCentral()
}

dependencies {
    implementation(platform("org.springframework.ai:spring-ai-bom:2.0.2"))
    implementation("org.springframework.ai:spring-ai-starter-mcp-server-webflux")
    implementation("org.springframework.boot:spring-boot-starter-aspectj")
    implementation("org.aspectj:aspectjweaver:1.9.24")
    implementation("org.aspectj:aspectjrt:1.9.24")
    implementation("org.springframework.boot:spring-boot-starter-validation")
    implementation("org.springframework.boot:spring-boot-starter-webclient")
    implementation("org.springframework.boot:spring-boot-starter-webflux")
    implementation("org.springframework.boot:spring-boot-starter-security")
    implementation("org.springframework.boot:spring-boot-starter-oauth2-client")
    implementation("org.springframework.security:spring-security-oauth2-jose")
    implementation("com.google.cloud:google-cloud-storage:2.78.0")
    implementation("org.apache.pdfbox:pdfbox:3.0.6")
    implementation("org.apache.poi:poi-ooxml:5.4.1")
    implementation("io.projectreactor.kotlin:reactor-kotlin-extensions")
    implementation("tools.jackson.module:jackson-module-kotlin")
    testImplementation("org.springframework.boot:spring-boot-starter-webclient-test")
    testImplementation("org.springframework.boot:spring-boot-starter-webflux-test")
    testImplementation("org.jetbrains.kotlin:kotlin-test-junit5")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
    testImplementation(kotlin("test"))
}

kotlin {
    compilerOptions {
        freeCompilerArgs.addAll("-Xjsr305=strict", "-Xannotation-default-target=param-property")
    }
}

tasks.withType<Test> {
    useJUnitPlatform()
}

val migrationArchiveArgument = providers.gradleProperty("archive")
val migrationOwnerEmailArgument = providers.gradleProperty("ownerEmail")

tasks.register<JavaExec>("migrateLegacyData") {
    group = "application"
    description = "Duplicate the shared legacy data archive into one account workspace."
    notCompatibleWithConfigurationCache("This one-off operation takes archive paths and account targets from command-line properties.")
    classpath = sourceSets.main.get().runtimeClasspath
    mainClass.set("com.mosta.coveringletter.persistence.LegacyDataAccountMigrationCli")
    argumentProviders.add(CommandLineArgumentProvider {
        val archive = migrationArchiveArgument.orNull
            ?: throw GradleException("Set -Parchive to the downloaded data-backup.zip path.")
        val ownerEmail = migrationOwnerEmailArgument.orNull
            ?: throw GradleException("Set -PownerEmail to the account that receives the data copy.")
        listOf(archive, ownerEmail)
    })
}
