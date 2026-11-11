import java.net.URI
import java.util.zip.ZipFile

plugins {
    application
    id("org.openjfx.javafxplugin") version "0.1.0"
}


java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(21))
    }
}

javafx {
    version = "21"
    modules = listOf("javafx.controls")
}

dependencies {
    implementation(project(":core"))
}

application {
    mainClass.set("cn.starcloud.desktop.Main")
}

val javafxVersion = "21.0.6"
val appName = "Starcloud"

val buildDir = layout.buildDirectory
val javafxJmodsDir = buildDir.dir("javafx-jmods").get().asFile
val runtimeDir = buildDir.dir("runtime").get().asFile
val installLibDir = buildDir.dir("install/desktop/lib").get().asFile
val distDir = buildDir.dir("dist").get().asFile

val currentOs: String = System.getProperty("os.name").lowercase()
val currentPlatform: String = when {
    currentOs.contains("linux") -> "linux"
    currentOs.contains("windows") -> "windows"
    currentOs.contains("mac") -> "osx"
    else -> error("Unsupported platform: $currentOs")
}

val pathSeparator: String = System.getProperty("path.separator")

val toolchainJavaHome: String = javaToolchains.launcherFor {
    languageVersion.set(JavaLanguageVersion.of(21))
}.get().metadata.installationPath.asFile.absolutePath

val downloadJavafxJmods by tasks.registering {
    description = "Download JavaFX JMODs from Gluon for the current platform"
    group = "distribution"

    val zipFile = buildDir.file("javafx-jmods.zip").get().asFile

    outputs.dir(javafxJmodsDir)

    doLast {
        if (javafxJmodsDir.exists() && javafxJmodsDir.listFiles()?.isNotEmpty() == true) {
            logger.lifecycle("JMODs already present, skipping download: $javafxJmodsDir")
            return@doLast
        }

        val url = "https://download2.gluonhq.com/openjfx/$javafxVersion/openjfx-${javafxVersion}_${currentPlatform}-x64_bin-jmods.zip"
        logger.lifecycle("Downloading: $url")

        zipFile.parentFile.mkdirs()
        URI(url).toURL().openStream().use { input ->
            zipFile.outputStream().use { output -> input.copyTo(output) }
        }

        logger.lifecycle("Extracting to: $javafxJmodsDir")
        javafxJmodsDir.mkdirs()
        ZipFile(zipFile).use { zip ->
            zip.entries().asSequence().forEach { entry ->
                val target = javafxJmodsDir.resolve(entry.name)
                if (entry.isDirectory) {
                    target.mkdirs()
                } else {
                    target.parentFile.mkdirs()
                    zip.getInputStream(entry).use { input ->
                        target.outputStream().use { output -> input.copyTo(output) }
                    }
                }
            }
        }
        zipFile.delete()
    }
}

val createRuntime by tasks.registering(Exec::class) {
    description = "Build a custom runtime image with JavaFX bundled"
    group = "distribution"
    dependsOn(downloadJavafxJmods, tasks.named("installDist"))

    executable = "$toolchainJavaHome/bin/jlink"

    doFirst {
        if (runtimeDir.exists()) runtimeDir.deleteRecursively()

        val actualJmodsDir = javafxJmodsDir.walkTopDown()
            .firstOrNull { it.isDirectory && it.listFiles()?.any { f -> f.extension == "jmod" } == true }
            ?: error("No .jmod files found under $javafxJmodsDir")

        logger.lifecycle("Building runtime image at: $runtimeDir")
        logger.lifecycle("JavaFX JMODs: ${actualJmodsDir.absolutePath}")

        args = listOf(
            "--module-path", "$toolchainJavaHome/jmods$pathSeparator${actualJmodsDir.absolutePath}",
            "--add-modules", "javafx.controls",
            "--output", runtimeDir.absolutePath,
            "--strip-debug",
            "--no-header-files",
            "--no-man-pages",
            "--compress", "2"
        )
    }
}

val packageApp by tasks.registering(Exec::class) {
    description = "Package the application as a self-contained app image"
    group = "distribution"
    dependsOn(createRuntime, tasks.named("installDist"))

    executable = "$toolchainJavaHome/bin/jpackage"

    doFirst {
        if (distDir.exists()) distDir.deleteRecursively()
        distDir.mkdirs()

        val mainJar = tasks.named<Jar>("jar").get().archiveFileName.get()

        if (!installLibDir.resolve(mainJar).exists()) {
            error("Main jar not found: ${installLibDir.resolve(mainJar)}")
        }

        logger.lifecycle("Packaging $appName to: $distDir")

        args = listOf(
            "--type", "app-image",
            "--name", appName,
            "--input", installLibDir.absolutePath,
            "--main-jar", mainJar,
            "--main-class", "cn.starcloud.desktop.Main",
            "--runtime-image", runtimeDir.absolutePath,
            "--dest", distDir.absolutePath
        )
    }
}