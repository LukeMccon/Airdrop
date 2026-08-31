import java.io.File
import java.io.DataInputStream
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption
import java.util.Properties
import java.util.jar.JarFile
import org.gradle.api.publish.maven.MavenPublication
import org.gradle.api.publish.tasks.GenerateModuleMetadata
import org.gradle.api.publish.maven.tasks.PublishToMavenRepository
import org.gradle.api.tasks.Delete
import org.gradle.api.tasks.Exec
import org.gradle.api.tasks.GradleBuild
import org.gradle.api.tasks.JavaExec
import org.gradle.api.tasks.compile.JavaCompile
import org.gradle.api.tasks.bundling.Jar
import org.gradle.api.tasks.javadoc.Javadoc
import org.gradle.api.tasks.testing.TestDescriptor
import org.gradle.api.tasks.testing.TestListener
import org.gradle.api.tasks.testing.TestResult
import org.gradle.api.tasks.testing.Test
import org.gradle.process.CommandLineArgumentProvider
import org.gradle.external.javadoc.StandardJavadocDocletOptions

plugins {
    `java-library`
    `maven-publish`
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
    withSourcesJar()
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
val sourcesJar = tasks.named<Jar>("sourcesJar") {
    dependsOn(generateAirdropApiMetadata)
}
val apiPublicationRepository = layout.buildDirectory.dir("api-publication/repository")
val apiJavadocOutput = layout.buildDirectory.dir("docs/api-javadoc")

val apiJavadoc = tasks.register<Javadoc>("apiJavadoc") {
    group = "documentation"
    description = "Generates warning-free Javadocs for the supported extension API"
    source = sourceSets.main.get().allJava.matching {
        include("com/airdropmc/api/**")
    }
    classpath = sourceSets.main.get().compileClasspath
    destinationDir = apiJavadocOutput.get().asFile
    isFailOnError = true
    (options as StandardJavadocDocletOptions).apply {
        encoding = Charsets.UTF_8.name()
        charSet = Charsets.UTF_8.name()
        docEncoding = Charsets.UTF_8.name()
        addBooleanOption("Werror", true)
    }
}

val apiJavadocJar = tasks.register<Jar>("apiJavadocJar") {
    group = "build"
    description = "Packages Javadocs for only the supported extension API"
    dependsOn(apiJavadoc)
    archiveClassifier.set("javadoc")
    from(apiJavadocOutput)
}

publishing {
    publications {
        create<MavenPublication>("modrinth") {
            groupId = "maven.modrinth"
            artifactId = "airdrop"
            version = project.version.toString()

            artifact(releaseJar)
            artifact(sourcesJar)
            artifact(apiJavadocJar)

            pom {
                name.set("Airdrop")
                description.set(project.description)
                url.set("https://modrinth.com/plugin/airdrop")
            }
        }
    }
    repositories {
        maven {
            name = "staging"
            url = uri(apiPublicationRepository)
        }
    }
}

tasks.withType<GenerateModuleMetadata>().configureEach {
    enabled = false
}

val cleanApiPublicationStaging = tasks.register<Delete>("cleanApiPublicationStaging") {
    group = "build"
    description = "Removes the deterministic local API publication staging repository"
    delete(apiPublicationRepository)
}

tasks.withType<PublishToMavenRepository>().configureEach {
    if (repository.name == "staging") {
        dependsOn(cleanApiPublicationStaging)
    }
}

val publishApiPublication = tasks.named("publishModrinthPublicationToStagingRepository")

val verifyApiPublication = tasks.register("verifyApiPublication") {
    group = "verification"
    description = "Verifies the staged Modrinth Maven artifact set and dependency-free POM"
    dependsOn(publishApiPublication)

    doLast {
        val repository = apiPublicationRepository.get().asFile.toPath()
        val files = Files.walk(repository).use { paths ->
            paths.filter(Files::isRegularFile).sorted().toList()
        }
        val checksumSuffixes = setOf(".md5", ".sha1", ".sha256", ".sha512")
        val primaryFiles = files.filter { path ->
            checksumSuffixes.none { suffix -> path.fileName.toString().endsWith(suffix) }
        }
        val jars = primaryFiles.filter { it.fileName.toString().endsWith(".jar") }
        val poms = primaryFiles.filter { it.fileName.toString().endsWith(".pom") }
        val metadata = primaryFiles.filter { it.fileName.toString() == "maven-metadata.xml" }
        val expectedMetadataCount = if (project.version.toString().endsWith("-SNAPSHOT")) 2 else 1

        if (primaryFiles.size != 4 + expectedMetadataCount
            || jars.size != 3 || poms.size != 1 || metadata.size != expectedMetadataCount) {
            throw GradleException("Unexpected staged API publication files: $primaryFiles")
        }
        if (primaryFiles.any { it.fileName.toString().endsWith(".module") }) {
            throw GradleException("Staged API publication must not contain Gradle module metadata")
        }
        primaryFiles.forEach { publishedFile ->
            checksumSuffixes.forEach { suffix ->
                val checksum = publishedFile.resolveSibling(publishedFile.fileName.toString() + suffix)
                if (!Files.isRegularFile(checksum)) {
                    throw GradleException("Missing checksum $checksum")
                }
            }
        }

        val pomText = Files.readString(poms.single(), StandardCharsets.UTF_8)
        listOf(
            "<groupId>maven.modrinth</groupId>",
            "<artifactId>airdrop</artifactId>",
            "<version>${project.version}</version>"
        ).forEach { required ->
            if (!pomText.contains(required)) {
                throw GradleException("Staged POM is missing $required")
            }
        }
        if (pomText.contains("<dependencies>")
            || pomText.contains("do_not_remove: published-with-gradle-metadata")) {
            throw GradleException("Staged POM must be dependency-free and must not redirect to module metadata")
        }

        val runtimeJar = jars.single { path ->
            !path.fileName.toString().endsWith("-sources.jar")
                && !path.fileName.toString().endsWith("-javadoc.jar")
        }
        val sourcesArchive = jars.single { it.fileName.toString().endsWith("-sources.jar") }
        val javadocArchive = jars.single { it.fileName.toString().endsWith("-javadoc.jar") }
        fun requireEntry(archivePath: Path, entryName: String) {
            JarFile(archivePath.toFile()).use { archive ->
                if (archive.getJarEntry(entryName) == null) {
                    throw GradleException("$archivePath is missing $entryName")
                }
            }
        }
        requireEntry(runtimeJar, "com/airdropmc/api/AirdropApi.class")
        requireEntry(sourcesArchive, "com/airdropmc/api/AirdropApi.java")
        requireEntry(javadocArchive, "com/airdropmc/api/AirdropApi.html")
        JarFile(javadocArchive.toFile()).use { archive ->
            if (archive.entries().asSequence().any {
                    it.name.startsWith("com/airdropmc/internal/")
                        || it.name.startsWith("com/airdropmc/events/")
                }) {
                throw GradleException("API Javadocs must not publish internal or legacy event documentation")
            }
        }
    }
}

releaseJar.configure {
    manifest.attributes(
        "Implementation-Title" to project.name,
        "Implementation-Version" to project.version.toString(),
        "Airdrop-API-Version" to extensionApiVersion,
        "Airdrop-Paper-Version" to supportedPaperVersion,
        "Airdrop-Java-Version" to supportedJavaVersion
    )
}

val consumerFixtureJar = layout.projectDirectory.file(
    "consumer-fixture/build/libs/airdrop-consumer-fixture.jar"
)

val consumerFixtureTest = tasks.register<GradleBuild>("consumerFixtureTest") {
    group = "verification"
    description = "Builds and tests an external consumer against the staged Maven publication"
    dependsOn(verifyApiPublication)
    dir = layout.projectDirectory.dir("consumer-fixture").asFile
    tasks = listOf("clean", "test", "jar")
    startParameter.projectProperties = mapOf(
        "airdropRepository" to apiPublicationRepository.get().asFile.absolutePath,
        "airdropVersion" to project.version.toString(),
        "paperVersion" to supportedPaperApiVersion
    )
    inputs.files(
        fileTree(layout.projectDirectory.dir("consumer-fixture")) {
            exclude("build/**")
            exclude(".gradle/**")
        }
    )
    outputs.file(consumerFixtureJar)
    outputs.upToDateWhen { false }
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
    description = "Cross-checks runtime, sources, API Javadocs, and every published compatibility version"
    dependsOn(releaseJar)
    dependsOn(verifyApiCompatibility)
    dependsOn(sourcesJar)
    dependsOn(apiJavadocJar)

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

        val sourcesArchive = sourcesJar.get().archiveFile.get().asFile
        val javadocArchive = apiJavadocJar.get().archiveFile.get().asFile
        val expectedSourcesFilename = "${project.name}-$releaseVersion-sources.jar"
        val expectedJavadocFilename = "${project.name}-$releaseVersion-javadoc.jar"
        if (sourcesArchive.name != expectedSourcesFilename) {
            throw GradleException(
                "Sources artifact filename must be '$expectedSourcesFilename', " +
                    "but was '${sourcesArchive.name}'"
            )
        }
        if (javadocArchive.name != expectedJavadocFilename) {
            throw GradleException(
                "Javadoc artifact filename must be '$expectedJavadocFilename', " +
                    "but was '${javadocArchive.name}'"
            )
        }

        val relativeArchivePath = project.relativePath(archiveFile).replace(File.separatorChar, '/')
        val relativeSourcesPath = project.relativePath(sourcesArchive).replace(File.separatorChar, '/')
        val relativeJavadocPath = project.relativePath(javadocArchive).replace(File.separatorChar, '/')
        val githubOutput = System.getenv("GITHUB_OUTPUT")
        if (!githubOutput.isNullOrBlank()) {
            Files.writeString(
                Path.of(githubOutput),
                "artifact_name=${archiveFile.name}\n" +
                    "artifact_path=$relativeArchivePath\n" +
                    "sources_artifact_name=${sourcesArchive.name}\n" +
                    "sources_artifact_path=$relativeSourcesPath\n" +
                    "javadoc_artifact_name=${javadocArchive.name}\n" +
                    "javadoc_artifact_path=$relativeJavadocPath\n",
                StandardCharsets.UTF_8,
                StandardOpenOption.CREATE,
                StandardOpenOption.APPEND
            )
        } else {
            logger.lifecycle(
                "Verified release artifacts: " +
                    listOf(relativeArchivePath, relativeSourcesPath, relativeJavadocPath)
            )
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

val verifyModrinthDocs = tasks.register<Exec>("verifyModrinthDocs") {
    group = "verification"
    description = "Validates the canonical Modrinth project description without network access"
    workingDir(layout.projectDirectory)
    commandLine("./scripts/modrinth-docs", "check-local")
    inputs.files(
        layout.projectDirectory.file("docs/modrinth.md"),
        layout.projectDirectory.file("scripts/modrinth-docs")
    )
}

tasks.named("check") {
    dependsOn(verifyDependencyMatrix)
    dependsOn(verifyApiCompatibility)
    dependsOn(verifyModrinthDocs)
    dependsOn(verifyApiPublication)
}

tasks.named<Test>("test") {
    dependsOn(consumerFixtureTest)
    systemProperty(
        "airdrop.apiPublicationRepository",
        apiPublicationRepository.get().asFile.absolutePath
    )
    systemProperty("airdrop.pluginVersion", project.version.toString())
}

// Configure plugin.yml generation
bukkit {
    load = net.minecrell.pluginyml.bukkit.BukkitPluginDescription.PluginLoadOrder.STARTUP
    main = "com.airdropmc.Airdrop"
    apiVersion = supportedPaperVersion
    softDepend = listOf("LuckPerms", "Vault")
    authors = listOf("LukeMccon", "pianoman99987 (gregoryw)")
    description = "Call in customizable care packages that fall from the sky"
    website = "https://modrinth.com/plugin/airdrop"

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
