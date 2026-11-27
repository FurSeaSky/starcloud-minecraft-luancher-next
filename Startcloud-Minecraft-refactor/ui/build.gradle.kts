import org.jetbrains.compose.desktop.application.dsl.TargetFormat

repositories {
    google()
    mavenCentral()
}

plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.compose)
}

kotlin {
    jvmToolchain(21)
}

dependencies {
    implementation(project(":core"))
    implementation(compose.material3)
    implementation(compose.materialIconsExtended)
    implementation(compose.components.resources)
    implementation(libs.kotlinx.coroutines.swing)
    implementation(libs.logback.classic)
    implementation("com.arkivanov.decompose:decompose:3.4.0")
    implementation("com.arkivanov.decompose:extensions-compose:3.4.0")
    implementation("com.arkivanov.decompose:extensions-compose-experimental:3.4.0")
    implementation("org.jetbrains.skiko:skiko-awt-runtime-linux-x64:0.9.4.2")
}
configurations.all {
    resolutionStrategy {
        force("org.jetbrains.skiko:skiko:0.9.4.2")
        force("org.jetbrains.skiko:skiko-awt:0.9.4.2")
    }
}
compose.desktop {
    application {
        mainClass = "cn.starcloud.ui.MainKt"

        nativeDistributions {
            targetFormats(TargetFormat.Msi, TargetFormat.AppImage)
            packageName = "Starcloud"
            packageVersion = "1.0.0"
            description = "Starcloud Launcher"
        }
    }
}
compose.resources {
    publicResClass = true
    packageOfResClass = "cn.starcloud.ui.generated.resources"
}