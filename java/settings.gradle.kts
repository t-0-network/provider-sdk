pluginManagement {
    repositories {
        mavenCentral()
        gradlePluginPortal()
    }
    plugins {
        id("com.gradleup.nmcp") version "1.4.4"
        id("com.gradleup.nmcp.aggregation") version "1.4.4"
    }
}

rootProject.name = "provider-sdk-java"

include("sdk")
include("starter")
include("starter:template")
include("cli")
