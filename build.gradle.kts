import java.io.File
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption
import java.util.jar.JarFile
import org.gradle.api.tasks.Exec
import org.gradle.api.tasks.bundling.Jar

plugins {
    `java-library`
    id("xyz.jpenilla.run-paper") version "3.0.2" // Adds the runServer task for testing
    id("net.minecrell.plugin-yml.bukkit") version "0.6.0" // Generates plugin.yml
}

val releaseVersionPattern = Regex(
    """\d+\.\d+\.\d+(?:-[0-9A-Za-z-]+(?:\.[0-9A-Za-z-]+)*)?(?:\+[0-9A-Za-z-]+(?:\.[0-9A-Za-z-]+)*)?"""
)

fun normalizeReleaseTag(tag: String): String {
    if (tag.isBlank()) {
        throw GradleException("Gradle property 'releaseTag' must not be blank")
    }

    val normalized = tag.removePrefix("v")
    if (normalized.contains("SNAPSHOT", ignoreCase = true)) {
        throw GradleException("Gradle property 'releaseTag' must not contain SNAPSHOT")
    }
    if (!releaseVersionPattern.matches(normalized)) {
        throw GradleException(
            "Gradle property 'releaseTag' must be MAJOR.MINOR.PATCH with optional prerelease and build metadata"
        )
    }
    return normalized
}

val configuredReleaseVersion = providers.gradleProperty("releaseTag").orNull?.let { normalizeReleaseTag(it) }

group = "com.airdropmc"
version = configuredReleaseVersion ?: "4.0.0-SNAPSHOT"
description = "Airdrop - Minecraft care package plugin"

java {
    // Configure the java toolchain. Use Java 21 per Paper recommendations.
    toolchain.languageVersion.set(JavaLanguageVersion.of(21))
}

repositories {
    mavenCentral()
    maven("https://repo.papermc.io/repository/maven-public/") {
        name = "papermc-repo"
    }
    maven("https://repo.codemc.io/repository/maven-public/") {
        name = "codemc"
    }
    maven("https://repo.codemc.io/repository/creatorfromhell/") {
        name = "creatorfromhell"
    }
    maven("https://oss.sonatype.org/content/groups/public/") {
        name = "sonatype"
    }
    maven("https://jitpack.io") {
        name = "jitpack"
    }
}

dependencies {
    // Paper API
    compileOnly("io.papermc.paper:paper-api:1.21.11-R0.1-SNAPSHOT")
    
    // Plugin dependencies
    compileOnly("net.luckperms:api:5.4")
    compileOnly("net.milkbowl.vault:VaultUnlockedAPI:2.20")
    testRuntimeOnly("net.luckperms:api:5.4")
    
    // Annotations
    compileOnly("org.jetbrains:annotations:24.1.0")
    
    // Test dependencies
    testImplementation("net.milkbowl.vault:VaultUnlockedAPI:2.20")
    testImplementation("org.junit.jupiter:junit-jupiter:5.10.1")
    testImplementation("org.yaml:snakeyaml:2.2")
    testImplementation("org.mockito:mockito-core:5.14.2")
    testImplementation("org.mockito:mockito-junit-jupiter:5.14.2")
    testImplementation("com.github.seeseemelk:MockBukkit-v1.21:3.133.2")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks {
    runServer {
        minecraftVersion("1.21.11")
    }

    compileJava {
        options.encoding = Charsets.UTF_8.name() // We want UTF-8 for everything
        // Set the release flag. This configures what version bytecode the compiler will emit, as well as what JDK APIs are usable.
        // See https://openjdk.java.net/jeps/247 for more information.
        options.release.set(21)
    }
    
    javadoc {
        options.encoding = Charsets.UTF_8.name() // We want UTF-8 for everything
    }

    test {
        useJUnitPlatform()
        systemProperty("airdrop.projectVersion", project.version.toString())
    }

    clean {
        delete(layout.projectDirectory.dir("lightkeeper/target"))
    }

}

val releaseJar = tasks.named<Jar>("jar")

val prepareLightkeeperPluginAdapter = tasks.register<Exec>("prepareLightkeeperPluginAdapter") {
    group = "verification"
    description = "Repairs the pinned JitPack LightKeeper plugin descriptor in a generated local repository"
    workingDir(layout.projectDirectory.dir("lightkeeper"))
    commandLine("./bootstrap-lightkeeper-plugin.sh")
    inputs.files(
        layout.projectDirectory.file("lightkeeper/bootstrap-lightkeeper-plugin.sh"),
        layout.projectDirectory.file("lightkeeper/lightkeeper-maven-plugin-adapter.pom.xml")
    )
    outputs.dir(
        layout.projectDirectory.dir(
            "lightkeeper/target/lightkeeper-plugin-repository/com/airdropmc/lightkeeper-adapter/" +
                "lightkeeper-maven-plugin/be585af08221c37bcbc8c9d7f5a40a27dbd2dff1-airdrop1"
        )
    )
}

tasks.register<Exec>("lightkeeperTest") {
    group = "verification"
    description = "Runs LightKeeper integration tests against a real Paper server"
    dependsOn("jar")
    dependsOn(prepareLightkeeperPluginAdapter)
    workingDir(layout.projectDirectory.dir("lightkeeper"))
    inputs.file(releaseJar.flatMap { it.archiveFile })
    outputs.upToDateWhen { false }

    doFirst {
        val pluginJar = releaseJar.get().archiveFile.get().asFile.absoluteFile
        commandLine(
            "./mvnw",
            "--batch-mode",
            "--no-transfer-progress",
            "verify",
            "-Dairdrop.jar.path=${pluginJar.path}"
        )
    }
}

tasks.register("verifyReleaseArtifact") {
    group = "verification"
    description = "Verifies the release JAR filename and embedded plugin version"
    dependsOn(releaseJar)

    doLast {
        val releaseVersion = configuredReleaseVersion
            ?: throw GradleException("verifyReleaseArtifact requires the Gradle property 'releaseTag'")
        val archiveFile = releaseJar.get().archiveFile.get().asFile
        val expectedFilename = "${project.name}-$releaseVersion.jar"
        if (archiveFile.name != expectedFilename) {
            throw GradleException(
                "Release artifact filename must be '$expectedFilename', but was '${archiveFile.name}'"
            )
        }

        val pluginVersion = JarFile(archiveFile).use { archive ->
            val pluginYml = archive.getJarEntry("plugin.yml")
                ?: throw GradleException("Release artifact must contain a root plugin.yml")
            archive.getInputStream(pluginYml)
                .bufferedReader(StandardCharsets.UTF_8)
                .useLines { lines ->
                    lines.singleOrNull { it.startsWith("version:") }
                        ?.substringAfter(':')
                        ?.trim()
                        ?.removeSurrounding("\"")
                        ?.removeSurrounding("'")
                        ?: throw GradleException(
                            "Root plugin.yml must contain exactly one top-level version scalar"
                        )
                }
        }
        if (pluginVersion != releaseVersion) {
            throw GradleException(
                "Root plugin.yml version must be '$releaseVersion', but was '$pluginVersion'"
            )
        }

        val relativeArchivePath = project.relativePath(archiveFile).replace(File.separatorChar, '/')
        val githubOutput = System.getenv("GITHUB_OUTPUT")
        if (!githubOutput.isNullOrBlank()) {
            Files.writeString(
                Path.of(githubOutput),
                "artifact_name=${archiveFile.name}\nartifact_path=$relativeArchivePath\n",
                StandardCharsets.UTF_8,
                StandardOpenOption.CREATE,
                StandardOpenOption.APPEND
            )
        } else {
            logger.lifecycle("Verified release artifact: $relativeArchivePath")
        }
    }
}

// Configure plugin.yml generation
bukkit {
    load = net.minecrell.pluginyml.bukkit.BukkitPluginDescription.PluginLoadOrder.STARTUP
    main = "com.airdropmc.Airdrop"
    apiVersion = "1.21.11"
    depend = listOf("LuckPerms")
    softDepend = listOf("Vault")
    authors = listOf("LukeMccon", "pianoman99987 (gregoryw)")
    description = "Call in customizable care packages that fall from the sky"

    commands {
        register("airdrop") {
            description = "Call in an airdrop!"
            aliases = listOf("drop", "ad")
            usage = "/airdrop <package name>"
        }
    }

    permissions {
        register("airdrop.package.all") {
            description = "Allows players to use all configured airdrop packages"
            default = net.minecrell.pluginyml.bukkit.BukkitPluginDescription.Permission.Default.FALSE
        }
        register("airdrop.package.*") {
            description = "Wildcard alias for package usage permissions"
            default = net.minecrell.pluginyml.bukkit.BukkitPluginDescription.Permission.Default.FALSE
            children = listOf("airdrop.package.all")
        }
        register("airdrop.cooldown.bypass") {
            description = "Bypasses only the per-player airdrop request cooldown"
            default = net.minecrell.pluginyml.bukkit.BukkitPluginDescription.Permission.Default.OP
        }
        register("airdrop.admin") {
            description = "Allows full administrative access to Airdrop commands and GUIs"
            default = net.minecrell.pluginyml.bukkit.BukkitPluginDescription.Permission.Default.OP
            children = listOf("airdrop.package.all", "airdrop.package.*", "airdrop.cooldown.bypass")
        }
    }
}
