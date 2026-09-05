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

dependencies {
    intellijPlatform {
        intellijIdeaCommunity("2024.3")
        // Java PSI is required: the plugin operates on Java files' PSI.
        bundledPlugin("com.intellij.java")
        testFramework(org.jetbrains.intellij.platform.gradle.TestFrameworkType.Platform)
    }
    testImplementation("org.junit.jupiter:junit-jupiter:5.11.3")
    testImplementation("junit:junit:4.13.2") // LightJavaCodeInsightFixtureTestCase (JUnit3/4 base)
}

java {
    toolchain {
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

tasks.test {
    // LightJavaCodeInsightFixtureTestCase is JUnit3/4-based; keep the platform test runner.
    useJUnit()
}
