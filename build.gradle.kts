// Versions copied from NoammAddons' 26.2 build (unobfuscated MC: no mappings line).
plugins { id("net.fabricmc.fabric-loom") version providers.gradleProperty("loom_version").get() }

version = property("mod_version") as String

dependencies {
    minecraft("com.mojang:minecraft:${property("minecraft_version")}")
    implementation("net.fabricmc:fabric-loader:${property("loader_version")}")
    implementation("net.fabricmc.fabric-api:fabric-api:${property("fabric_version")}")
}

tasks.withType<JavaCompile>().configureEach { options.release.set(25) }

tasks.processResources {
    inputs.property("version", version)
    filesMatching("fabric.mod.json") { expand("version" to version) }
}
