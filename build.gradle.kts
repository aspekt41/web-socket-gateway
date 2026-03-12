plugins {
    java
    application
    jacoco
    pmd
    id("com.gradleup.shadow") version "9.3.2"
    id("com.diffplug.spotless") version "7.0.3"
}

group = "net.aspekt.gateway"
version = "1.0.0-SNAPSHOT"

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
}

jacoco {
    toolVersion = "0.8.12"
}

application {
    mainClass = "com.gateway.Main"
}

repositories {
    mavenCentral()
}

dependencies {
    implementation("io.netty:netty-all:4.+")
    implementation("jakarta.xml.bind:jakarta.xml.bind-api:4.+")
    runtimeOnly("com.sun.xml.bind:jaxb-impl:4.+")
    testImplementation("org.junit.jupiter:junit-jupiter:5.+")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.test {
    useJUnitPlatform()
    finalizedBy(tasks.jacocoTestReport)
}

tasks.jacocoTestReport {
    dependsOn(tasks.test)

    reports {
        html.required = true
        xml.required  = true
        csv.required  = false
    }
}

tasks.shadowJar {
    archiveBaseName = "gateway"
    archiveClassifier = ""
    archiveVersion = ""
    manifest {
        attributes["Main-Class"] = "com.gateway.Main"
    }
    mergeServiceFiles()
}

spotless {
    java {
        palantirJavaFormat("2.50.0")
        importOrder()
        removeUnusedImports()
    }
}

pmd {
    toolVersion = "7.10.0"
    isConsoleOutput = true
    rulesMinimumPriority = 2
    ruleSetFiles = files("config/pmd/ruleset.xml")
}
