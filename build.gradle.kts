import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar

plugins {
    java
    id("com.gradleup.shadow") version "9.3.1"
}

group = "com.electro"
version = "1.4.1"

repositories {
    mavenCentral()
    maven("https://maven.hytale.com/release")
    maven("https://www.cursemaven.com")
}

dependencies {
    compileOnly("com.hypixel.hytale:Server:2026.02.06-aa1b071c2")
    implementation("curse.maven:hyui-1431415:7598682")

    testImplementation(platform("org.junit:junit-bom:5.10.0"))
    testImplementation("org.junit.jupiter:junit-jupiter")
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(25))
    }
}

tasks.withType<JavaCompile>().configureEach {
    options.release.set(25)
}

tasks.test {
    useJUnitPlatform()
}

tasks.withType<ShadowJar> {
    archiveBaseName.set("HyCitizens")
    archiveVersion.set(project.version.toString())
    archiveClassifier.set("")

    relocate("au.ellie.hyui", "com.electro.hycitizens.shaded.hyui")
    relocate("org.jsoup", "com.electro.hycitizens.shaded.jsoup")
}

tasks.build {
    dependsOn("shadowJar")
}
