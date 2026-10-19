plugins {
    java
    id("org.jetbrains.intellij.platform") version "2.19.0"
}

group = "dev.openin"
version = "1.0.2"

repositories {
    mavenCentral()
    intellijPlatform { defaultRepositories() }
}

dependencies {
    intellijPlatform {
        val localIde = providers.gradleProperty("localIdePath")
        if (localIde.isPresent) local(localIde.get())
        else intellijIdea("2026.2.3")
        pluginVerifier()
    }
    testImplementation("org.junit.jupiter:junit-jupiter:5.11.4")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

java {
    toolchain { languageVersion = JavaLanguageVersion.of(25) }
}

tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
    options.release = 25
}

tasks.test { useJUnitPlatform() }

val compileServiceLauncher = tasks.register<Exec>("compileServiceLauncher") {
    group = "build"
    description = "Compiles the bundled macOS service launcher without AppleScript."
    commandLine("clang", "-O2", "-framework", "Cocoa",
        "native/open-in-service-launcher.m", "-o", layout.buildDirectory.file("service-launcher/open-in-service-launcher").get().asFile.absolutePath)
    outputs.file(layout.buildDirectory.file("service-launcher/open-in-service-launcher"))
    inputs.file("native/open-in-service-launcher.m")
}
tasks.named("processResources") { dependsOn(compileServiceLauncher) }
tasks.named<Copy>("processResources") {
    from(layout.buildDirectory.file("service-launcher/open-in-service-launcher")) { into("native") }
}

intellijPlatform {
    // macOS Unix sockets cannot use a long UTF-8 project path as the sandbox root.
    sandboxContainer = file(providers.gradleProperty("sandboxPath").getOrElse(
        "${System.getProperty("user.home")}/.oi/${Integer.toUnsignedString(rootProject.projectDir.absolutePath.hashCode(), 16)}"
    ))
    pluginConfiguration {
        name = "Open In"
        version = project.version.toString()
        ideaVersion {
            sinceBuild = "262"
            untilBuild = "262.*"
        }
    }
    pluginVerification {
        ides { current() }
    }
}
