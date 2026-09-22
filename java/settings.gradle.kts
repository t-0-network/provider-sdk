pluginManagement {
    repositories {
        mavenCentral()
        gradlePluginPortal()
    }
    plugins {
        id("com.gradleup.nmcp") version "1.6.2"
        id("com.gradleup.nmcp.aggregation") version "1.6.2"
    }
}

rootProject.name = "provider-sdk-java"

include("sdk")
include("starter-template")
project(":starter-template").projectDir = file("starter/template")
