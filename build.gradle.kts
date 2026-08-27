import java.util.jar.JarFile

plugins {
    java
}

group = "com.electro"
version = "2.0"

repositories {
    mavenCentral()
    maven("https://maven.hytale.com/release/")
    //maven("https://maven.hytale.com/pre-release/")
}

dependencies {
    compileOnly("com.hypixel.hytale:Server:latest.release")
    implementation(fileTree("libs") { include("*.jar") })

    testImplementation(platform("org.junit:junit-bom:5.10.0"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testImplementation("com.google.code.gson:gson:2.11.0")
    testImplementation("com.hypixel.hytale:Server:latest.release")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
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
    dependsOn("verifyPluginJar")
}

tasks.register("verifyPluginJar") {
    dependsOn("fatJar")
    doLast {
        val artifact = tasks.named<Jar>("fatJar").get().archiveFile.get().asFile
        JarFile(artifact).use { jar ->
            val manifestEntry = jar.getJarEntry("manifest.json")
                ?: error("HyCitizens artifact is missing manifest.json")
            val metadata = jar.getInputStream(manifestEntry).bufferedReader().use { it.readText() }
            require(metadata.contains("\"Group\": \"com.electro\"")) { "Incorrect plugin group in manifest.json" }
            require(metadata.contains("\"Name\": \"HyCitizens\"")) { "Incorrect plugin name in manifest.json" }
            require(metadata.contains("\"Main\": \"com.electro.hycitizens.HyCitizensPlugin\"")) {
                "Incorrect plugin main class in manifest.json"
            }
            require(metadata.contains("\"Version\": \"$version\"")) { "Incorrect plugin version in manifest.json" }
            require(jar.getJarEntry("com/electro/hycitizens/HyCitizensPlugin.class") != null) {
                "HyCitizens plugin entry class is missing"
            }
            require(jar.entries().asSequence().none { it.name.startsWith("com/electro/hyquests/") }) {
                "HyQuests implementation classes must not be bundled into HyCitizens"
            }
        }
    }
}
