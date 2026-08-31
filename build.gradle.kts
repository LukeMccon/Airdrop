import java.io.File
import java.io.DataInputStream
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption
import java.util.Properties
import java.util.jar.JarFile
import org.gradle.api.tasks.Exec
import org.gradle.api.tasks.JavaExec
import org.gradle.api.tasks.compile.JavaCompile
import org.gradle.api.tasks.bundling.Jar
import org.gradle.api.tasks.testing.TestDescriptor
import org.gradle.api.tasks.testing.TestListener
import org.gradle.api.tasks.testing.TestResult
import org.gradle.api.tasks.testing.Test
import org.gradle.process.CommandLineArgumentProvider

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
fun requiredBuildProperty(name: String): String = providers.gradleProperty(name).orNull
    ?.takeIf { it.isNotBlank() }
    ?: throw GradleException("Required Gradle property '$name' must not be blank")

val sourceDevelopmentVersion = requiredBuildProperty("airdropPluginVersion")
val extensionApiVersion = requiredBuildProperty("airdropExtensionApiVersion")
val supportedPaperVersion = requiredBuildProperty("airdropPaperVersion")
val supportedJavaVersion = requiredBuildProperty("airdropJavaVersion")
val japicmpVersion = requiredBuildProperty("airdropJapicmpVersion")
val supportedPaperApiVersion = "$supportedPaperVersion-R0.1-SNAPSHOT"
val supportedJUnitVersion = "6.1.3"

group = "com.airdropmc"
version = configuredReleaseVersion ?: sourceDevelopmentVersion
description = "Airdrop - Minecraft care package plugin"

java {
    // Configure the java toolchain. Use Java 21 per Paper recommendations.
    toolchain.languageVersion.set(JavaLanguageVersion.of(supportedJavaVersion.toInt()))
}

val japicmp by configurations.creating {
    isCanBeConsumed = false
    isCanBeResolved = true
    description = "Isolated JApiCmp CLI used only for an explicitly supplied prior API JAR"
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
    japicmp("com.github.siom79.japicmp:japicmp:$japicmpVersion")

    // Paper API
    compileOnly("io.papermc.paper:paper-api:$supportedPaperApiVersion")
    
    // Plugin dependencies
    compileOnly("net.luckperms:api:5.4")
    compileOnly("net.milkbowl.vault:VaultUnlockedAPI:2.20")
    testCompileOnly("net.luckperms:api:5.4")
    testRuntimeOnly("net.luckperms:api:5.4")
    
    // Annotations
    compileOnly("org.jetbrains:annotations:24.1.0")
    
    // Test dependencies
    testImplementation("io.papermc.paper:paper-api:$supportedPaperApiVersion")
    testImplementation("net.milkbowl.vault:VaultUnlockedAPI:2.20")
    testImplementation(platform("org.junit:junit-bom:$supportedJUnitVersion"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testImplementation("org.yaml:snakeyaml:2.2")
    testImplementation("org.mockito:mockito-core:5.14.2")
    testImplementation("org.mockito:mockito-junit-jupiter:5.14.2")
    testImplementation("org.mockbukkit.mockbukkit:mockbukkit-v1.21:4.116.3")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks {
    runServer {
        minecraftVersion(supportedPaperVersion)
    }

    compileJava {
        options.encoding = Charsets.UTF_8.name() // We want UTF-8 for everything
        // Set the release flag. This configures what version bytecode the compiler will emit, as well as what JDK APIs are usable.
        // See https://openjdk.java.net/jeps/247 for more information.
        options.release.set(supportedJavaVersion.toInt())
    }
    
    javadoc {
        options.encoding = Charsets.UTF_8.name() // We want UTF-8 for everything
    }

    test {
        useJUnitPlatform()
        systemProperty("airdrop.projectVersion", project.version.toString())
        systemProperty("airdrop.sourceDevelopmentVersion", sourceDevelopmentVersion)
        systemProperty("airdrop.extensionApiVersion", extensionApiVersion)
        systemProperty("airdrop.paperVersion", supportedPaperVersion)
        systemProperty("airdrop.javaVersion", supportedJavaVersion)
        addTestListener(object : TestListener {
            override fun beforeSuite(suite: TestDescriptor) = Unit

            override fun afterSuite(suite: TestDescriptor, result: TestResult) = Unit

            override fun beforeTest(testDescriptor: TestDescriptor) = Unit

            override fun afterTest(testDescriptor: TestDescriptor, result: TestResult) {
                if (result.resultType != TestResult.ResultType.SKIPPED) {
                    return
                }

                val testIdentifier = listOfNotNull(testDescriptor.className, testDescriptor.name)
                    .joinToString(".")
                throw GradleException("Skipped test '$testIdentifier' is not allowed.")
            }
        })
    }

    clean {
        delete(layout.projectDirectory.dir("lightkeeper/target"))
    }

}

val generatedAirdropMetadataDirectory = layout.buildDirectory.dir("generated/resources/airdrop-metadata")
val generatedAirdropMetadataFile = generatedAirdropMetadataDirectory.map {
    it.file("airdrop-api.properties")
}

val generateAirdropApiMetadata = tasks.register("generateAirdropApiMetadata") {
    group = "build"
    description = "Generates the runtime extension API compatibility metadata"
    inputs.property("pluginVersion", project.version.toString())
    inputs.property("extensionApiVersion", extensionApiVersion)
    inputs.property("paperCompatibilityVersion", supportedPaperVersion)
    inputs.property("javaVersion", supportedJavaVersion)
    outputs.file(generatedAirdropMetadataFile)

    doLast {
        val output = generatedAirdropMetadataFile.get().asFile.toPath()
        Files.createDirectories(output.parent)
        Files.writeString(
            output,
            buildString {
                append("plugin-version=").append(project.version).append('\n')
                append("extension-api-version=").append(extensionApiVersion).append('\n')
                append("paper-compatibility-version=").append(supportedPaperVersion).append('\n')
                append("java-version=").append(supportedJavaVersion).append('\n')
            },
            StandardCharsets.UTF_8
        )
    }
}

sourceSets.named("main") {
    resources.srcDir(generatedAirdropMetadataDirectory)
}

tasks.named("processResources") {
    dependsOn(generateAirdropApiMetadata)
}

val mainSourceSet = sourceSets.named("main")
val generatedApiSignature = layout.buildDirectory.file("api-signatures/current.txt")
val recordedApiBaseline = layout.projectDirectory.file(
    "config/api-signatures/$extensionApiVersion.txt"
)
val apiSignatureToolClasses = layout.buildDirectory.dir("api-signature-tool/classes")

val compileApiSignatureTool = tasks.register<JavaCompile>("compileApiSignatureTool") {
    group = "build"
    description = "Compiles the isolated classfile API signature generator"
    source(fileTree("build-tools/src/main/java") { include("**/*.java") })
    classpath = japicmp
    destinationDirectory.set(apiSignatureToolClasses)
    options.release.set(supportedJavaVersion.toInt())
    options.encoding = Charsets.UTF_8.name()
}

val generateApiSignature = tasks.register<JavaExec>("generateApiSignature") {
    group = "verification"
    description = "Generates a deterministic signature of the supported com.airdropmc.api surface"
    dependsOn(tasks.named("classes"))
    dependsOn(compileApiSignatureTool)
    inputs.files(mainSourceSet.map { it.output.classesDirs })
    outputs.file(generatedApiSignature)

    classpath(files(apiSignatureToolClasses), japicmp)
    mainClass.set("com.airdropmc.tools.ApiSignatureGenerator")
    argumentProviders.add(CommandLineArgumentProvider {
        listOf(generatedApiSignature.get().asFile.absolutePath) +
            mainSourceSet.get().output.classesDirs.files
                .map { it.absolutePath }
                .sorted()
    })
    doFirst {
        Files.createDirectories(generatedApiSignature.get().asFile.toPath().parent)
    }
}

tasks.withType<Test>().configureEach {
    dependsOn(generateApiSignature)
    inputs.file(generatedApiSignature)
}

val previousApiJar = providers.gradleProperty("previousApiJar")
val comparePreviousApiJar = tasks.register<JavaExec>("comparePreviousApiJar") {
    group = "verification"
    description = "Runs binary and source compatibility checks against -PpreviousApiJar"
    dependsOn(tasks.named("jar"))
    classpath = japicmp
    mainClass.set("japicmp.JApiCmp")
    onlyIf {
        if (!previousApiJar.isPresent) {
            logger.lifecycle("No -PpreviousApiJar supplied; skipping prior-JAR comparison")
            false
        } else {
            true
        }
    }
    doFirst {
        val oldJar = layout.projectDirectory.file(previousApiJar.get()).asFile
        if (!oldJar.isFile) {
            throw GradleException("-PpreviousApiJar must name an existing JAR: $oldJar")
        }
        val previousApiVersion = JarFile(oldJar).use { archive ->
            val metadataEntry = archive.getJarEntry("airdrop-api.properties")
                ?: throw GradleException(
                    "Previous API JAR must contain airdrop-api.properties: $oldJar"
                )
            Properties().apply {
                archive.getInputStream(metadataEntry).use(::load)
            }.getProperty("extension-api-version")
                ?.takeIf { it.isNotBlank() }
                ?: throw GradleException(
                    "Previous API JAR metadata must declare extension-api-version: $oldJar"
                )
        }
        fun apiMajor(version: String): Int = version.substringBefore('.').toIntOrNull()
            ?: throw GradleException("Invalid extension API version '$version'")
        val previousApiMajor = apiMajor(previousApiVersion)
        val currentApiMajor = apiMajor(extensionApiVersion)
        val reviewedBaselineChange = providers.gradleProperty("reviewedApiBaselineChange").orNull
        val newJar = tasks.named<Jar>("jar").get().archiveFile.get().asFile
        val comparisonArguments = mutableListOf(
            "--old", oldJar.absolutePath,
            "--new", newJar.absolutePath,
            "--include", "com.airdropmc.api.*",
            "--only-modified",
            "--ignore-missing-classes"
        )
        if (previousApiMajor != currentApiMajor) {
            logger.lifecycle(
                "Reporting API changes without failing because the extension API major changed " +
                    "from $previousApiVersion to $extensionApiVersion"
            )
        } else if (reviewedBaselineChange == extensionApiVersion) {
            logger.warn(
                "Reporting same-major incompatibilities under explicitly reviewed API " +
                    "$extensionApiVersion baseline change"
            )
        } else {
            comparisonArguments.add("--error-on-binary-incompatibility")
            comparisonArguments.add("--error-on-source-incompatibility")
        }
        setArgs(comparisonArguments)
    }
}

val verifyApiCompatibility = tasks.register("verifyApiCompatibility") {
    group = "verification"
    description = "Verifies the current API signature and any explicitly supplied previous API JAR"
    dependsOn(generateApiSignature)
    dependsOn(comparePreviousApiJar)
    inputs.file(generatedApiSignature)
    inputs.files(providers.provider {
        providers.gradleProperty("previousApiBaseline").orNull
            ?.let { layout.projectDirectory.file(it).asFile }
            ?: recordedApiBaseline.asFile
    }).optional()
    inputs.property(
        "reviewedApiBaselineChange",
        providers.gradleProperty("reviewedApiBaselineChange").orElse("")
    )

    doLast {
        val explicitBaseline = providers.gradleProperty("previousApiBaseline").orNull
        val baseline = explicitBaseline?.let { layout.projectDirectory.file(it).asFile }
            ?: recordedApiBaseline.asFile
        if (!baseline.isFile) {
            throw GradleException(
                "API baseline is missing: $baseline. Regenerate with ./gradlew generateApiSignature " +
                    "and review/copy build/api-signatures/current.txt to " +
                    "config/api-signatures/$extensionApiVersion.txt"
            )
        }

        val currentLines = Files.readAllLines(
            generatedApiSignature.get().asFile.toPath(), StandardCharsets.UTF_8
        )
        val baselineLines = Files.readAllLines(baseline.toPath(), StandardCharsets.UTF_8)
        if (currentLines != baselineLines) {
            val removed = baselineLines.toSet().minus(currentLines.toSet()).sorted().take(20)
            val added = currentLines.toSet().minus(baselineLines.toSet()).sorted().take(20)
            val reviewedVersion = providers.gradleProperty("reviewedApiBaselineChange").orNull
            if (reviewedVersion == extensionApiVersion) {
                logger.warn(
                    "Allowing explicitly reviewed API $extensionApiVersion baseline change; " +
                        "removed=$removed added=$added"
                )
                return@doLast
            }
            throw GradleException(
                "Supported API differs from ${project.relativePath(baseline)}. " +
                    "Removed: $removed Added: $added. " +
                    "Bump the extension API as required or pass " +
                    "-PreviewedApiBaselineChange=$extensionApiVersion only for a reviewed baseline update."
            )
        }
        logger.lifecycle("Verified API $extensionApiVersion signature against ${project.relativePath(baseline)}")
    }
}

val releaseJar = tasks.named<Jar>("jar")

releaseJar.configure {
    manifest.attributes(
        "Implementation-Title" to project.name,
        "Implementation-Version" to project.version.toString(),
        "Airdrop-API-Version" to extensionApiVersion,
        "Airdrop-Paper-Version" to supportedPaperVersion,
        "Airdrop-Java-Version" to supportedJavaVersion
    )
}

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
    description = "Cross-checks the release artifact and every published compatibility version"
    dependsOn(releaseJar)
    dependsOn(verifyApiCompatibility)

    doLast {
        val releaseVersion = configuredReleaseVersion
            ?: throw GradleException("verifyReleaseArtifact requires the Gradle property 'releaseTag'")
        val sourceReleaseVersion = sourceDevelopmentVersion.removeSuffix("-SNAPSHOT")
        val releaseCoreVersion = releaseVersion.substringBefore('-').substringBefore('+')
        if (releaseCoreVersion != sourceReleaseVersion) {
            throw GradleException(
                "Release tag '$releaseVersion' does not match source version '$sourceDevelopmentVersion'"
            )
        }
        val archiveFile = releaseJar.get().archiveFile.get().asFile
        val expectedFilename = "${project.name}-$releaseVersion.jar"
        if (archiveFile.name != expectedFilename) {
            throw GradleException(
                "Release artifact filename must be '$expectedFilename', but was '${archiveFile.name}'"
            )
        }

        JarFile(archiveFile).use { archive ->
            fun textEntry(name: String): String {
                val entry = archive.getJarEntry(name)
                    ?: throw GradleException("Release artifact must contain $name")
                return archive.getInputStream(entry)
                    .bufferedReader(StandardCharsets.UTF_8)
                    .use { it.readText() }
            }

            fun yamlScalar(yaml: String, key: String): String = yaml.lineSequence()
                .singleOrNull { it.startsWith("$key:") }
                ?.substringAfter(':')
                ?.trim()
                ?.removeSurrounding("\"")
                ?.removeSurrounding("'")
                ?: throw GradleException(
                    "Root plugin.yml must contain exactly one top-level $key scalar"
                )

            fun requireValue(label: String, actual: String?, expected: String) {
                if (actual != expected) {
                    throw GradleException("$label must be '$expected', but was '$actual'")
                }
            }

            val pluginYml = textEntry("plugin.yml")
            val pluginVersion = yamlScalar(pluginYml, "version")
            val pluginPaperVersion = yamlScalar(pluginYml, "api-version")
            requireValue("Root plugin.yml version", pluginVersion, releaseVersion)
            requireValue("Root plugin.yml api-version", pluginPaperVersion, supportedPaperVersion)
            if (pluginPaperVersion == extensionApiVersion) {
                throw GradleException(
                    "Root plugin.yml api-version must describe Paper, not extension API $extensionApiVersion"
                )
            }

            val apiMetadata = Properties().apply {
                load(textEntry("airdrop-api.properties").reader())
            }
            requireValue("API metadata plugin-version",
                apiMetadata.getProperty("plugin-version"), releaseVersion)
            requireValue("API metadata extension-api-version",
                apiMetadata.getProperty("extension-api-version"), extensionApiVersion)
            requireValue("API metadata paper-compatibility-version",
                apiMetadata.getProperty("paper-compatibility-version"), supportedPaperVersion)
            requireValue("API metadata java-version",
                apiMetadata.getProperty("java-version"), supportedJavaVersion)

            val manifest = archive.manifest?.mainAttributes
                ?: throw GradleException("Release artifact must contain a manifest")
            requireValue("Manifest Implementation-Version",
                manifest.getValue("Implementation-Version"), releaseVersion)
            requireValue("Manifest Airdrop-API-Version",
                manifest.getValue("Airdrop-API-Version"), extensionApiVersion)
            requireValue("Manifest Airdrop-Paper-Version",
                manifest.getValue("Airdrop-Paper-Version"), supportedPaperVersion)
            requireValue("Manifest Airdrop-Java-Version",
                manifest.getValue("Airdrop-Java-Version"), supportedJavaVersion)

            val mainClass = archive.getJarEntry("com/airdropmc/Airdrop.class")
                ?: throw GradleException("Release artifact must contain com/airdropmc/Airdrop.class")
            val classMajorVersion = archive.getInputStream(mainClass).use { stream ->
                DataInputStream(stream).use { classInput ->
                    if (classInput.readInt() != 0xCAFEBABE.toInt()) {
                        throw GradleException("Airdrop.class has an invalid class-file header")
                    }
                    classInput.readUnsignedShort()
                    classInput.readUnsignedShort()
                }
            }
            val expectedClassMajor = supportedJavaVersion.toInt() + 44
            if (classMajorVersion != expectedClassMajor) {
                throw GradleException(
                    "Class-file Java target must be $supportedJavaVersion (major $expectedClassMajor), " +
                        "but Airdrop.class uses major $classMajorVersion"
                )
            }
        }

        val changelogHeading = Files.readAllLines(
            layout.projectDirectory.file("CHANGELOG.md").asFile.toPath(), StandardCharsets.UTF_8
        ).firstOrNull()
        if (changelogHeading == null || !changelogHeading.startsWith("# Airdrop $releaseCoreVersion")) {
            throw GradleException(
                "CHANGELOG.md must start with an Airdrop $releaseCoreVersion release heading"
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

val verifyDependencyMatrix = tasks.register("verifyDependencyMatrix") {
    group = "verification"
    description = "Verifies exact Paper and JUnit versions on the test classpaths"

    doLast {
        listOf("testCompileClasspath", "testRuntimeClasspath").forEach { configurationName ->
            val resolvedModules = configurations.getByName(configurationName)
                .incoming
                .resolutionResult
                .allComponents
                .mapNotNull { it.moduleVersion }

            val paperVersions = resolvedModules
                .filter { it.group == "io.papermc.paper" && it.name == "paper-api" }
                .map { it.version }
                .toSet()
            if (paperVersions != setOf(supportedPaperApiVersion)) {
                throw GradleException(
                    "$configurationName must resolve Paper $supportedPaperApiVersion exactly, but resolved $paperVersions"
                )
            }

            val junitVersions = resolvedModules
                .filter { it.group.startsWith("org.junit") }
                .associate { "${it.group}:${it.name}" to it.version }
            if (junitVersions.isEmpty()) {
                throw GradleException("$configurationName must resolve JUnit modules")
            }
            val unexpectedJUnit = junitVersions.filterValues { it != supportedJUnitVersion }
            if (unexpectedJUnit.isNotEmpty()) {
                throw GradleException(
                    "$configurationName must resolve every JUnit module to $supportedJUnitVersion, " +
                        "but resolved $unexpectedJUnit"
                )
            }
        }
    }
}

tasks.named("check") {
    dependsOn(verifyDependencyMatrix)
    dependsOn(verifyApiCompatibility)
}

// Configure plugin.yml generation
bukkit {
    load = net.minecrell.pluginyml.bukkit.BukkitPluginDescription.PluginLoadOrder.STARTUP
    main = "com.airdropmc.Airdrop"
    apiVersion = supportedPaperVersion
    softDepend = listOf("LuckPerms", "Vault")
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
