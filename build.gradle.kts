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
    // sonar-delphi is provided via a patched jar produced by tasks defined below
    implementation(files(layout.buildDirectory.file("thirdparty/sonar-delphi-plugin-patched.jar")))

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
        // Enable focused unit logging when requested
        val unitFilter = System.getProperty("nl.akiar.pascal.log.unitFilter") ?: System.getenv("PASCAL_UNIT_LOG_FILTER")
        if (!unitFilter.isNullOrBlank()) {
            jvmArgs("-Dnl.akiar.pascal.log.unitFilter=$unitFilter")
        }
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
        java { srcDirs("src/main/java") }
        kotlin { srcDirs("src/main/kotlin") }
        resources { srcDirs("src/main/resources") }
    }
    // Dedicated source set for patch classes that replace classes inside sonar-delphi jar
    create("sonarPatch") {
        java { srcDirs("patches/sonar-delphi/src/main/java") }
        kotlin { srcDirs("patches/sonar-delphi/src/main/kotlin") }
        resources { srcDirs("patches/sonar-delphi/src/main/resources") }
        compileClasspath += main.get().compileClasspath
        runtimeClasspath += main.get().runtimeClasspath
    }
}

// ---------- Sonar-Delphi patching pipeline ----------
val sonarDelphiVersion = (project.findProperty("sonarDelphiVersion") as String?) ?: "1.18.3"
val sonarDelphiFileName = (project.findProperty("sonarDelphiFileName") as String?) ?: "sonar-delphi-plugin-${sonarDelphiVersion}.jar"
val sonarDelphiUrl = (project.findProperty("sonarDelphiUrl") as String?) ?: System.getenv("SONAR_DELPHI_URL") ?: ""

val thirdpartyDirFile = layout.buildDirectory.dir("thirdparty").get().asFile
val libsJarFile = file("libs/${sonarDelphiFileName}")
val patchedJarFile = File(thirdpartyDirFile, "sonar-delphi-plugin-patched.jar")

// Optional: download upstream sonar-delphi jar to libs/ via curl
val downloadSonarDelphi = tasks.register("downloadSonarDelphi", Exec::class.java) {
    group = "thirdparty"
    description = "Download sonar-delphi jar to libs/ if URL provided"
    onlyIf { sonarDelphiUrl.isNotBlank() && !libsJarFile.exists() }
    commandLine("bash", "-lc", "curl -fSL '${sonarDelphiUrl}' -o '${libsJarFile.absolutePath}'")
}

// Verify libs jar exists
val ensureLibsJar = tasks.register("ensureLibsJar") {
    group = "verification"
    description = "Verify sonar-delphi jar exists in libs/"
    dependsOn(downloadSonarDelphi)
    doLast {
        if (!libsJarFile.exists()) {
            throw GradleException("Missing libs/${sonarDelphiFileName}. Provide it or set -PsonarDelphiUrl/SONAR_DELPHI_URL to download.")
        }
    }
}

// Unzip original from libs/, overlay patched classes/resources, and re-zip into a patched jar
val unzipSonarDelphiOrig = tasks.register("unzipSonarDelphiOrig", Copy::class.java) {
    group = "thirdparty"
    description = "Unpack original sonar-delphi jar into build/tmp"
    dependsOn(ensureLibsJar)
    from(zipTree(libsJarFile))
    into(layout.buildDirectory.dir("tmp/sonar-delphi-orig").get())
}

val buildPatchedSonarJar = tasks.register("buildPatchedSonarJar", Zip::class.java) {
    group = "thirdparty"
    description = "Produce patched sonar-delphi jar by overlaying patch classes/resources"
    dependsOn(unzipSonarDelphiOrig, tasks.named("compileSonarPatchKotlin"), tasks.named("compileSonarPatchJava"))
    archiveFileName.set(patchedJarFile.name)
    destinationDirectory.set(layout.buildDirectory.dir("thirdparty"))

    val unzipDir = layout.buildDirectory.dir("tmp/sonar-delphi-orig").get().asFile
    from(unzipDir)

    // Overlay: compiled patch classes/resources
    val patchClassesDirK = layout.buildDirectory.dir("classes/kotlin/sonarPatch").get().asFile
    val patchClassesDirJ = layout.buildDirectory.dir("classes/java/sonarPatch").get().asFile
    val patchResourcesDir = file("patches/sonar-delphi/src/main/resources")

    from(patchClassesDirK)
    from(patchClassesDirJ)
    from(patchResourcesDir) { include("**/*") }
}

// Ensure compilation/tests use the patched jar
dependencies {
    // ...existing code...
    implementation(files(patchedJarFile))
}

tasks.named("compileJava").configure { dependsOn(buildPatchedSonarJar) }
tasks.named("compileKotlin").configure { dependsOn(buildPatchedSonarJar) }
tasks.named("test").configure { dependsOn(buildPatchedSonarJar) }

// Optional: decompile helper task using javap for quick inspection of classes
val decompileClasses = tasks.register("decompileClasses", Exec::class.java) {
    group = "verification"
    description = "Run javap on key classes from sonar-delphi jar in libs/ and write output to libs/decompiled"
    dependsOn(ensureLibsJar)
    doFirst {
        file("libs/decompiled").parentFile.mkdirs()
    }
    commandLine(
        "bash", "-lc",
        "javap -classpath '${libsJarFile.absolutePath}' -p " +
            "\"au.com.integradev.delphi.preprocessor.DelphiPreprocessor\" " +
            "\"au.com.integradev.delphi.preprocessor.directive.IncludeDirectiveImpl\" " +
            "\"au.com.integradev.delphi.preprocessor.directive.ConditionalDirectiveImpl\" " +
            "\"au.com.integradev.delphi.antlr.DelphiParser\" " +
            "\"org.sonar.plugins.communitydelphi.api.ast.UnitDeclarationNode\" " +
            "\"org.sonar.plugins.communitydelphi.api.ast.UsesClauseNode\" " +
            "\"org.sonar.plugins.communitydelphi.api.ast.FileHeaderNode\" " +
            "> 'libs/decompiled'"
    )
}
