import java.time.Duration

plugins {
    java
    id("com.gradleup.nmcp.aggregation") version "1.4.4"
    // JReleaser disabled - using NMCP instead. Re-enable when JReleaser bugs are fixed.
    // id("org.jreleaser") version "1.22.0"
}

allprojects {
    repositories {
        mavenCentral()
    }
}

subprojects {
    apply(plugin = "java")

    java {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    tasks.withType<JavaCompile> {
        options.encoding = "UTF-8"
    }

    tasks.withType<Test> {
        useJUnitPlatform()
    }
}

nmcpAggregation {
    centralPortal {
        username = providers.environmentVariable("MAVEN_CENTRAL_USERNAME")
        password = providers.environmentVariable("MAVEN_CENTRAL_PASSWORD")
        publicationName = "T-0 Provider SDK ${project.version}"
        // Wait for Maven Central to validate, but don't wait for full publishing
        validationTimeout = Duration.ofMinutes(30)
        publishingTimeout = Duration.ZERO
    }
}

dependencies {
    nmcpAggregation(project(":sdk"))
}

// ============================================================================
// JReleaser Configuration (DISABLED - using NMCP instead)
// ============================================================================
// To switch back to JReleaser:
// 1. In this file (build.gradle.kts):
//    - Comment out id("com.gradleup.nmcp.aggregation") plugin
//    - Uncomment id("org.jreleaser") plugin
//    - Comment out nmcpAggregation { } block and its dependencies { } block
//    - Uncomment jreleaser { } block below
// 2. In sdk/build.gradle.kts:
//    - Comment out id("com.gradleup.nmcp") plugin
//    - Uncomment the repositories { } block in publishing { }
// 3. In cli/build.gradle.kts:
//    - Comment out id("com.gradleup.nmcp") plugin
//    - Uncomment the repositories { } block in publishing { }
// 4. In .github/workflows/publish.yaml:
//    - Comment out NMCP publishing step
//    - Uncomment JReleaser staging and deploy steps
// 5. (Optional) Remove NMCP plugins from settings.gradle.kts pluginManagement
// ============================================================================
/*
jreleaser {
    project {
        name = "provider-java"
        description = "Java SDK and CLI for T-0 Network providers"
        copyright = "T-0 Network"
    }
    release {
        github {
            skipRelease = true
            skipTag = true
            changelog {
                enabled = false
            }
        }
    }
    signing {
        active = org.jreleaser.model.Active.NEVER
    }
    deploy {
        maven {
            mavenCentral {
                create("sonatype") {
                    active = org.jreleaser.model.Active.ALWAYS
                    url = "https://central.sonatype.com/api/v1/publisher"
                    sign = false
                    skipPublicationCheck = true
                    stagingRepository("sdk/build/staging-deploy")
                }
            }
        }
    }
}
*/
