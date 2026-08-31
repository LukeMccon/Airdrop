import java.nio.charset.StandardCharsets
import java.nio.file.Files

plugins {
	java
}

val airdropRepository = providers.gradleProperty("airdropRepository")
	.orNull
	?.takeIf(String::isNotBlank)
	?: throw GradleException("Missing required Gradle property 'airdropRepository'")
val airdropVersion = providers.gradleProperty("airdropVersion")
	.orNull
	?.takeIf(String::isNotBlank)
	?: throw GradleException("Missing required Gradle property 'airdropVersion'")
val paperVersion = providers.gradleProperty("paperVersion")
	.orNull
	?.takeIf(String::isNotBlank)
	?: throw GradleException("Missing required Gradle property 'paperVersion'")

group = "dev.airdropmc.fixture"
version = "1.0.0"

java {
	toolchain.languageVersion.set(JavaLanguageVersion.of(21))
}

repositories {
	maven {
		name = "airdrop-staging"
		url = uri(airdropRepository)
		content {
			includeModule("maven.modrinth", "airdrop")
		}
		metadataSources {
			mavenPom()
			ignoreGradleMetadataRedirection()
		}
	}
	maven("https://repo.papermc.io/repository/maven-public/") {
		name = "papermc"
		content {
			includeGroup("io.papermc.paper")
			includeGroup("com.mojang")
			includeGroup("net.md-5")
		}
	}
	mavenCentral {
		content {
			excludeGroup("maven.modrinth")
		}
	}
}

dependencies {
	compileOnly("maven.modrinth:airdrop:$airdropVersion") {
		isChanging = airdropVersion.endsWith("-SNAPSHOT")
	}
	compileOnly("io.papermc.paper:paper-api:$paperVersion")

	testImplementation(platform("org.junit:junit-bom:6.1.3"))
	testImplementation("org.junit.jupiter:junit-jupiter")
	testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

configurations.configureEach {
	resolutionStrategy.cacheChangingModulesFor(0, "seconds")
}

val generatedPluginResources = layout.buildDirectory.dir("generated/plugin-resources")
val generatePluginDescription = tasks.register("generatePluginDescription") {
	val pluginYml = generatedPluginResources.map { it.file("plugin.yml") }
	outputs.file(pluginYml)

	doLast {
		val output = pluginYml.get().asFile.toPath()
		Files.createDirectories(output.parent)
		Files.writeString(
			output,
			"""
			name: AirdropConsumerFixture
			version: ${project.version}
			main: dev.airdropmc.fixture.AirdropConsumerFixture
			api-version: '1.21.11'
			depend: [Airdrop]
			""".trimIndent() + "\n",
			StandardCharsets.UTF_8
		)
	}
}

sourceSets.main {
	resources.srcDir(generatedPluginResources)
}

tasks.compileJava {
	options.encoding = Charsets.UTF_8.name()
	options.release.set(21)
}

tasks.processResources {
	dependsOn(generatePluginDescription)
}

tasks.test {
	useJUnitPlatform()
	systemProperty("airdrop.repository", file(airdropRepository).absolutePath)
	systemProperty("airdrop.version", airdropVersion)
}

tasks.jar {
	archiveFileName.set("airdrop-consumer-fixture.jar")
}
