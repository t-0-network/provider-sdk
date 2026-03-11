plugins {
    application
    id("com.gradleup.shadow") version "9.3.1"
}

val picocliVersion = "4.7.7"

dependencies {
    implementation(project(":sdk"))
    implementation("info.picocli:picocli:$picocliVersion")
    annotationProcessor("info.picocli:picocli-codegen:$picocliVersion")
}

application {
    mainClass.set("network.t0.cli.InitCommand")
}

// Copy template files from starter/template to resources during build
tasks.register<Sync>("copyTemplateResources") {
    from("../starter/template")
    into(layout.buildDirectory.dir("resources/main/template"))
    exclude(
        ".gradle",
        "build",
        ".env",
        "libs/",
        "*.class",
        ".idea",
        "*.iml"
    )
}

tasks.processResources {
    dependsOn("copyTemplateResources")
}

// Generate version.properties with current version
tasks.register("generateVersionProperties") {
    val outputDir = layout.buildDirectory.dir("resources/main")
    outputs.dir(outputDir)
    doLast {
        val propsFile = outputDir.get().file("version.properties").asFile
        propsFile.parentFile.mkdirs()
        propsFile.writeText("version=${project.version}\n")
    }
}

tasks.processResources {
    dependsOn("generateVersionProperties")
}

// Shadow JAR configuration
tasks.shadowJar {
    archiveBaseName.set("provider-init")
    archiveClassifier.set("")
    archiveVersion.set("${project.version}")

    manifest {
        attributes(
            "Main-Class" to "network.t0.cli.InitCommand",
            "Implementation-Title" to "T-0 Provider Init",
            "Implementation-Version" to project.version
        )
    }

    // Minimize JAR size by excluding unused classes
    minimize {
        exclude(dependency("org.bouncycastle:.*:.*"))
    }
}

// Make shadowJar the default artifact
tasks.build {
    dependsOn(tasks.shadowJar)
}

