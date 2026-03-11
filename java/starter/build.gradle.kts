import java.security.SecureRandom

plugins {
    application
}

dependencies {
    implementation(project(":sdk"))

    // dotenv for loading .env files
    implementation("io.github.cdimascio:dotenv-java:3.2.0")

    // Logging
    implementation("ch.qos.logback:logback-classic:1.5.25")
}

application {
    mainClass.set("network.t0.starter.Main")
}

// Task to generate a new keypair for the provider
tasks.register("generateKeys") {
    group = "T-0 Network"
    description = "Generates a new secp256k1 keypair for the provider"

    doLast {
        // This will be executed in the context of the template project
        println("Generating new secp256k1 keypair...")

        // Use reflection to avoid compile-time dependency issues
        val signerClass = Class.forName("network.t0.sdk.crypto.Signer")
        val fromBytesMethod = signerClass.getMethod("fromBytes", ByteArray::class.java)

        // Generate a random 32-byte private key
        val secureRandom = SecureRandom()
        val privateKeyBytes = ByteArray(32)
        secureRandom.nextBytes(privateKeyBytes)

        val signer = fromBytesMethod.invoke(null, privateKeyBytes)

        val getPublicKeyHexMethod = signerClass.getMethod("getPublicKeyHex")
        val publicKeyHex = getPublicKeyHexMethod.invoke(signer) as String

        val privateKeyHex = privateKeyBytes.joinToString("") { "%02x".format(it) }

        println("\nGenerated keypair:")
        println("Private key: $privateKeyHex")
        println("Public key:  $publicKeyHex")
        println("\nAdd these to your .env file:")
        println("PROVIDER_PRIVATE_KEY=$privateKeyHex")
        println("PROVIDER_PUBLIC_KEY=$publicKeyHex")
    }
}
