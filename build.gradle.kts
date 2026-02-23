plugins {
    java
}

group = "com.electro"
version = "1.5"

repositories {
    mavenCentral()
    maven("https://maven.hytale.com/release/")
}

dependencies {
    compileOnly("com.hypixel.hytale:Server:latest.release")
    implementation(files("libs/HyUI-0.8.9-all.jar"))

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

tasks.register<Jar>("fatJar") {
    archiveBaseName.set("HyCitizens")
    archiveVersion.set(version.toString())
    archiveClassifier.set("")

    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
    from(sourceSets.main.get().output)
    from(configurations.runtimeClasspath.get().map { if (it.isDirectory) it else zipTree(it) })
}

tasks.build {
    dependsOn("fatJar")
}