plugins {
    `java-library`
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(21))
    }
}

dependencies {
    api(libs.gson)
    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.platform.launcher)
    testImplementation("com.tngtech.archunit:archunit:1.5.0")
    testImplementation("org.mockito:mockito-core:5.11.0")
    testImplementation("org.mockito:mockito-junit-jupiter:5.11.0")
    implementation("org.slf4j:slf4j-api:2.0.16")
    testImplementation("org.slf4j:slf4j-simple:2.0.16")
    testImplementation("org.junit.jupiter:junit-jupiter-api:5.10.2")
    testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine:5.10.2")
}

tasks.withType<Test>().configureEach {
    useJUnitPlatform()
}

tasks.test {
    useJUnitPlatform()
}