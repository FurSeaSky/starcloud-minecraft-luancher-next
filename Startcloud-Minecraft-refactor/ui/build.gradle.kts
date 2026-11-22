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
    implementation(compose.desktop.currentOs)
    implementation(compose.material3)
    implementation(compose.materialIconsExtended)
    implementation(libs.kotlinx.coroutines.swing)
    implementation(libs.logback.classic)

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