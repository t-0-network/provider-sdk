plugins {
    `java-library`
    id("build.buf") version "0.10.3"
    id("me.champeau.jmh") version "0.7.3"
    `maven-publish`
    signing
    id("com.gradleup.nmcp")
}

val grpcVersion = "1.79.0"
val protobufVersion = "4.34.0"
val bouncyCastleVersion = "1.83"

dependencies {
    // gRPC dependencies
    api("io.grpc:grpc-okhttp:$grpcVersion")
    api("io.grpc:grpc-netty-shaded:$grpcVersion")
    api("io.grpc:grpc-protobuf:$grpcVersion")
    api("io.grpc:grpc-stub:$grpcVersion")


    // Protobuf
    api("com.google.protobuf:protobuf-java:$protobufVersion")
    api("build.buf:protovalidate:1.1.1")

    // BouncyCastle for crypto (secp256k1, Keccak-256)
    implementation("org.bouncycastle:bcprov-jdk18on:$bouncyCastleVersion")

    // Logging
    implementation("org.slf4j:slf4j-api:2.0.17")

    // javax.annotation for generated code
    compileOnly("javax.annotation:javax.annotation-api:1.3.2")

    // Testing
    testImplementation("org.junit.jupiter:junit-jupiter:6.0.3")
    testImplementation("org.assertj:assertj-core:3.27.7")
    testImplementation("io.grpc:grpc-testing:$grpcVersion")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
    testRuntimeOnly("ch.qos.logback:logback-classic:1.5.32")
}

// Disable buf format and lint checks - proto files are synced from backend and should not be modified
buf {
    enforceFormat = false
}

tasks.configureEach {
    if (name == "bufLint") {
        enabled = false
    }
}

// Buf code generation - integrate generated sources into the build
tasks.named("compileJava").configure {
    dependsOn("bufGenerate")
}

tasks.withType<Jar>().configureEach {
    dependsOn("bufGenerate")
}

sourceSets {
    main {
        java {
            srcDir("${layout.buildDirectory.get().asFile}/bufbuild/generated/gen/java")
        }
    }
}


// JMH benchmark configuration
jmh {
    warmupIterations.set(3)
    iterations.set(5)
    fork.set(1)
    // Uncomment for shorter runs during development:
    // warmupIterations.set(1)
    // iterations.set(1)
}

// Publishing configuration
java {
    withJavadocJar()
    withSourcesJar()
}

tasks.withType<Javadoc> {
    // Suppress warnings for generated protobuf code
    (options as StandardJavadocDocletOptions).addStringOption("Xdoclint:none", "-quiet")
}

publishing {
    publications {
        create<MavenPublication>("mavenJava") {
            artifactId = "provider-sdk-java"
            from(components["java"])

            pom {
                name.set("T-0 Provider SDK")
                description.set("Java SDK for T-0 Network providers")
                url.set("https://github.com/t-0-network/provider-sdk")

                licenses {
                    license {
                        name.set("MIT License")
                        url.set("https://opensource.org/licenses/MIT")
                    }
                }

                developers {
                    developer {
                        id.set("t-0")
                        name.set("T-0 Network")
                        email.set("dev@t-0.network")
                    }
                }

                scm {
                    connection.set("scm:git:git://github.com/t-0-network/provider-sdk.git")
                    developerConnection.set("scm:git:ssh://github.com/t-0-network/provider-sdk.git")
                    url.set("https://github.com/t-0-network/provider-sdk")
                }
            }
        }
    }

    // Staging repository for JReleaser (disabled - using NMCP)
    // Uncomment if switching back to JReleaser
    /*
    repositories {
        maven {
            name = "staging"
            url = uri(layout.buildDirectory.dir("staging-deploy"))
        }
    }
    */
}

signing {
    // Try env var first, then file
    val keyFile = rootProject.file(".signing-key.gpg")
    val signingKey: String? = System.getenv("GPG_PRIVATE_KEY")
        ?: if (keyFile.exists()) keyFile.readText() else null

    if (signingKey != null) {
        useInMemoryPgpKeys(signingKey, "")
        sign(publishing.publications["mavenJava"])
    }
}
