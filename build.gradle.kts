plugins {
    id("java")
    id("org.jetbrains.kotlin.jvm") version "2.2.0"
    id("org.jetbrains.intellij.platform") version "2.10.5"
    id("jacoco")
}

group = "nl.akiar"
version = "0.0.3"

repositories {
    mavenCentral()
    maven { url = uri("https://jitpack.io") }
    intellijPlatform {
        defaultRepositories()
    }
}

dependencies {
    intellijPlatform {
        intellijIdea("2025.3.1.1")
        testFramework(org.jetbrains.intellij.platform.gradle.TestFrameworkType.Platform)
    }
    implementation("org.sonarsource.sslr:sslr-core:1.24.0.633")
    implementation("org.sonarsource.sonarqube:sonar-plugin-api:8.9.10.61524")
    implementation(files("libs/sonar-delphi-plugin-1.18.3.jar"))

    testImplementation("org.junit.jupiter:junit-jupiter-api:5.10.1")
    testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine:5.10.1")
    testRuntimeOnly("org.junit.vintage:junit-vintage-engine:5.10.1")
    testImplementation("junit:junit:4.13.2")
}

intellijPlatform {
    pluginConfiguration {
        ideaVersion {
            sinceBuild.set("253")
            untilBuild.set("")
        }
    }
    instrumentCode.set(false)
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(21))
    }
}

tasks {
    // Set the JVM compatibility versions
    withType<JavaCompile> {
        sourceCompatibility = "21"
        targetCompatibility = "21"
    }

    withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile> {
        compilerOptions {
            jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_21)
        }
    }

    test {
        useJUnitPlatform()
    }

    jacocoTestReport {
        reports {
            xml.required.set(true)
            html.required.set(true)
        }
    }

    signPlugin {
        certificateChain.set(System.getenv("CERTIFICATE_CHAIN"))
        privateKey.set(System.getenv("PRIVATE_KEY"))
        password.set(System.getenv("PRIVATE_KEY_PASSWORD"))
    }

    publishPlugin {
        token.set(System.getenv("PUBLISH_TOKEN"))
    }
}

// Source sets configuration
sourceSets {
    main {
        java {
            srcDirs("src/main/java")
        }
        kotlin {
            srcDirs("src/main/kotlin")
        }
    }
}
