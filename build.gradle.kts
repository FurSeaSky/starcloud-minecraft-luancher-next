plugins {
    java
    id("com.diffplug.spotless") version "8.5.0" apply false
}

allprojects {
    group = "cn.starcloud"
    version = "0.1.0"
}

subprojects {
    
    apply(plugin = "com.diffplug.spotless")

    repositories {
        mavenCentral()
    }

    configure<com.diffplug.gradle.spotless.SpotlessExtension> {
        java {
            target("src/**/*.java")
            googleJavaFormat("1.28.0").reflowLongStrings()
            removeUnusedImports()
            trimTrailingWhitespace()
            endWithNewline()
        }
    }
}

tasks.register("packageApp") {
    group = "distribution"
    description = "Package the desktop app (delegates to :desktop:packageApp)"
    dependsOn(":desktop:packageApp")
}

tasks.named("check") {
    dependsOn("spotlessCheck")
}