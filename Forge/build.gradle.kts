plugins {
    id("com.github.johnrengelman.shadow")
}

architectury {
    platformSetupLoomIde()
    forge()
}

repositories {
    mavenLocal()
    mavenCentral()
    maven("https://maven.minecraftforge.net")
    maven("https://maven.architectury.dev/")
    // Flywheel (older group)
    maven("https://modmaven.dev") {
        name = "ModMaven"
        content { includeGroup("com.jozufozu.flywheel") }
    }
    // Flywheel (newer group)
    maven("https://maven.createmod.net") {
        name = "CreateMaven"
        content { includeGroup("dev.engine-room.flywheel") }
    }
    // Modrinth for Embeddium/Oculus
    maven("https://api.modrinth.com/maven") {
        name = "Modrinth"
    }
    // Optional: CurseMaven fallback if you decide to pull other mods from Curse later
    maven("https://cursemaven.com") { name = "CurseMaven" }
}

dependencies {
    forge("net.minecraftforge:forge:$forge_version")
}

loom {
    forge {
        mixinConfig("$mod_id.mixins.json")
        mixinConfig("$mod_id.forge.mixins.json")
    }
}

val common by configurations.creating
val shadowCommon by configurations.creating
val developmentForge = configurations.named("developmentForge")

configurations {
    compileClasspath.get().extendsFrom(common)
    runtimeClasspath.get().extendsFrom(common)
    developmentForge.get().extendsFrom(common)
}

dependencies {
    // Forge loader itself
    forge("net.minecraftforge:forge:$forge_version")

    // Link the Common module (standard Architectury pattern)
    common(project(path = ":Common", configuration = "namedElements")) { isTransitive = false }
    shadowCommon(project(path = ":Common", configuration = "transformProductionForge")) { isTransitive = false }

    // Mixin extras (provided by your version catalog / root build)
    include(mixinExtras)
    forgeRuntimeLibrary(mixinExtras)

    // Flywheel (Forge) – resolve via ModMaven/CreateMaven repos above
    modImplementation("com.jozufozu.flywheel:flywheel-forge-$minecraft_version:$forge_flywheel_version")

    // ModernUI
    forgeRuntimeLibrary("icyllis.modernui:ModernUI-Core:$modernui_core_version")
    modCompileOnly("icyllis.modernui:ModernUI-Forge:${minecraft_version}-${modernui_version}")

    // === Renderer deps for mixin targets ===
    // Embeddium & Oculus from Modrinth:
    // Use compileOnly for AP/compilation and runtimeOnly for dev runs.
    modCompileOnly("maven.modrinth:embeddium:0.3.4+mc1.20.1")
    modRuntimeOnly("maven.modrinth:embeddium:0.3.4+mc1.20.1")

    modCompileOnly("maven.modrinth:oculus:1.20.1-1.7.0")
    modRuntimeOnly("maven.modrinth:oculus:1.20.1-1.7.0")
}

tasks.processResources {
    inputs.property("version", project.version)
    filesMatching("META-INF/mods.toml") {
        expand("version" to project.version)
    }
}

tasks.shadowJar {
    exclude("fabric.mod.json")
    exclude("architectury.common.json")

    configurations = listOf(shadowCommon)
    archiveClassifier.set("dev-shadow")
}

tasks.remapJar {
    val shadowJarTask = tasks.shadowJar.get()
    inputFile.set(shadowJarTask.archiveFile)
    dependsOn(shadowJarTask)
    archiveClassifier.set(null as String?)
}

tasks.jar {
    archiveClassifier.set("dev")
}

tasks.sourcesJar {
    val commonSources = project(":Common").tasks.sourcesJar
    dependsOn(commonSources)
    from(commonSources.get().archiveFile.map(project::zipTree))
}

components.getByName<SoftwareComponent>("java") {
    (this as AdhocComponentWithVariants).apply {
        withVariantsFromConfiguration(project.configurations.shadowRuntimeElements.get()) {
            skip()
        }
    }
}
