plugins {
    java
    application
}

group = "com.gateway"
version = "1.0.0-SNAPSHOT"

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
}

application {
    mainClass = "com.gateway.Main"
}

repositories {
    mavenCentral()
}

dependencies {
    implementation("io.netty:netty-all:4.1.107.Final")
    implementation("jakarta.xml.bind:jakarta.xml.bind-api:4.0.4")
    runtimeOnly("com.sun.xml.bind:jaxb-impl:4.0.4")
testImplementation("org.junit.jupiter:junit-jupiter:5.10.2")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.test {
    useJUnitPlatform()
}
