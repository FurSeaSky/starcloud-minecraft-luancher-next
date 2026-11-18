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
    api(platform("org.apache.logging.log4j:log4j-bom:2.23.1"))
    api("org.apache.logging.log4j:log4j-api")
    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.platform.launcher)
    testImplementation("com.tngtech.archunit:archunit:1.5.0")
    testImplementation("org.mockito:mockito-core:5.11.0")
    testImplementation("org.mockito:mockito-junit-jupiter:5.11.0")
    runtimeOnly("org.apache.logging.log4j:log4j-core")

}

tasks.withType<Test>().configureEach {
    useJUnitPlatform()
}

tasks.test {
    useJUnitPlatform()
}