plugins {
    id("java")
    id("org.jetbrains.intellij.platform") version "2.18.1"
}

group = "guru.mocker.composition"
version = "0.1.0-SNAPSHOT"

repositories {
    mavenCentral()
    intellijPlatform {
        defaultRepositories()
    }
}

// Build the plugin against IntelliJ IDEA Community with Java support (needed to
// observe the Java PSI for concise method bodies during the injection spike).
dependencies {
    intellijPlatform {
        intellijIdeaCommunity("2024.3")
        bundledPlugin("com.intellij.java")
    }
}

java {
    toolchain {
        // IntelliJ Platform Gradle Plugin 2.x requires JDK 17+; IDEA 2024.3 targets 21.
        languageVersion.set(JavaLanguageVersion.of(21))
    }
}

intellijPlatform {
    pluginConfiguration {
        ideaVersion {
            sinceBuild.set("243")
        }
    }
}
