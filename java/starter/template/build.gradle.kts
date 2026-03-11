plugins {
    java
    application
}

group = "network.t-0"
version = "1.0.0-SNAPSHOT"

// SDK Repository: "jitpack" (default) or "maven-central"
val sdkRepository = "jitpack"

// Check if running as part of the parent multi-project build
val isSubproject = rootProject.name == "provider-sdk-java" || rootProject.name == "java"

repositories {
    mavenCentral()
    if (sdkRepository == "jitpack") {
        maven { url = uri("https://jitpack.io") }
    }
}

dependencies {
    // T-0 Network SDK
    if (isSubproject) {
        // Use local project when building as part of parent
        implementation(project(":sdk"))
    } else {
        // Use Maven Central / JitPack when running standalone
        val sdkDependency = if (sdkRepository == "jitpack") {
            "com.github.t-0-network:provider-sdk:+"
        } else {
            "network.t-0:provider-sdk-java:+"
        }
        implementation(sdkDependency)
    }

    // dotenv for loading .env files
    implementation("io.github.cdimascio:dotenv-java:3.2.0")

    // Logging
    implementation("ch.qos.logback:logback-classic:1.5.32")

    // javax.annotation for generated code
    compileOnly("javax.annotation:javax.annotation-api:1.3.2")
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

application {
    mainClass.set("network.t0.provider.Main")
    applicationName = "provider"
}

tasks.withType<JavaCompile> {
    options.encoding = "UTF-8"
}

// Task to run the application
tasks.named<JavaExec>("run") {
    standardInput = System.`in`
}
