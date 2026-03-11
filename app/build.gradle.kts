plugins {
    id("fabric-loom") version "1.9-SNAPSHOT"
}

version = "1.0.0"
group = "com.alexradu"

base {
    archivesName.set("enigma-fabric")
}

repositories {
    mavenCentral()
}

dependencies {
    minecraft("com.mojang:minecraft:1.20.1")
    mappings("net.fabricmc:yarn:1.20.1+build.10:v2")
    modImplementation("net.fabricmc:fabric-loader:0.14.21")
    modImplementation("net.fabricmc.fabric-api:fabric-api:0.92.2+1.20.1")

    // Gson is bundled with Minecraft; add compileOnly so the compiler can resolve imports
    compileOnly("com.google.code.gson:gson:2.10.1")
}

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
    sourceCompatibility = JavaVersion.VERSION_21
    targetCompatibility = JavaVersion.VERSION_21
}

tasks.jar {
    archiveVersion = version.toString()
    from("LICENSE") {
        duplicatesStrategy = DuplicatesStrategy.EXCLUDE
    }
}

tasks.named<ProcessResources>("processResources") {
    val modVersion = version.toString()
    inputs.property("version", modVersion)
    filesMatching("fabric.mod.json") {
        expand("version" to modVersion)
    }
}
