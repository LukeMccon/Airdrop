plugins {
    `java-library`
    id("xyz.jpenilla.run-paper") version "2.3.1" // Adds runServer and runMojangMappedServer tasks for testing
    id("net.minecrell.plugin-yml.bukkit") version "0.6.0" // Generates plugin.yml
}

group = "com.airdropmc"
version = "3.3.0-SNAPSHOT"
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
    maven("https://oss.sonatype.org/content/groups/public/") {
        name = "sonatype"
    }
    maven("https://repo.essentialsx.net/releases/") {
        name = "essentialsx-releases"
    }
    maven("https://jitpack.io") {
        name = "jitpack"
    }
}

dependencies {
    // Paper API
    compileOnly("io.papermc.paper:paper-api:1.21.8-R0.1-SNAPSHOT")
    
    // Plugin dependencies
    compileOnly("net.luckperms:api:5.4")
    compileOnly("net.essentialsx:EssentialsX:2.20.1") {
        exclude(group = "org.spigotmc", module = "spigot-api")
        exclude(group = "org.bukkit", module = "bukkit")
    }
    compileOnly("com.github.MilkBowl:VaultAPI:1.7") {
        exclude(group = "org.bukkit", module = "bukkit")
    }
    
    // Annotations
    compileOnly("org.jetbrains:annotations:24.1.0")
    
    // Test dependencies
    testImplementation("org.junit.jupiter:junit-jupiter:5.10.1")
    testImplementation("org.mockito:mockito-core:5.14.2")
    testImplementation("org.mockito:mockito-junit-jupiter:5.14.2")
    testImplementation("com.github.seeseemelk:MockBukkit-v1.21:3.133.2")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks {

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
    }

}

// Configure plugin.yml generation
bukkit {
    load = net.minecrell.pluginyml.bukkit.BukkitPluginDescription.PluginLoadOrder.STARTUP
    main = "com.airdropmc.Airdrop"
    apiVersion = "1.21"
    depend = listOf("LuckPerms", "Essentials", "Vault")
    authors = listOf("LukeMccon", "pianoman99987 (gregoryw)")
    description = "Call in customizable care packages that fall from the sky"
    
    commands {
        register("airdrop") {
            description = "Call in an airdrop!"
            aliases = listOf("drop", "ad")
            usage = "/airdrop <package name>"
        }
    }
}