plugins {
    `java-library`
    id("xyz.jpenilla.run-paper") version "3.0.2" // Adds the runServer task for testing
    id("net.minecrell.plugin-yml.bukkit") version "0.6.0" // Generates plugin.yml
}

group = "com.airdropmc"
version = "4.0.0-SNAPSHOT"
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
    maven("https://oss.sonatype.org/content/groups/public/") {
        name = "sonatype"
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
    compileOnly("com.github.MilkBowl:VaultAPI:1.7") {
        exclude(group = "org.bukkit", module = "bukkit")
    }
    compileOnly("me.lokka30:treasury-api:2.0.1")
    testRuntimeOnly("net.luckperms:api:5.4")
    testRuntimeOnly("com.github.MilkBowl:VaultAPI:1.7") {
        exclude(group = "org.bukkit", module = "bukkit")
    }
    testRuntimeOnly("me.lokka30:treasury-api:2.0.1")
    
    // Annotations
    compileOnly("org.jetbrains:annotations:24.1.0")
    
    // Test dependencies
    testImplementation("me.lokka30:treasury-api:2.0.1")
    testImplementation("org.junit.jupiter:junit-jupiter:5.10.1")
    testImplementation("org.yaml:snakeyaml:2.2")
    testImplementation("org.mockito:mockito-core:5.14.2")
    testImplementation("org.mockito:mockito-junit-jupiter:5.14.2")
    testImplementation("com.github.seeseemelk:MockBukkit-v1.21:3.133.2")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks {
    runServer {
        minecraftVersion("1.21.8")
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
    }

}

// Configure plugin.yml generation
bukkit {
    load = net.minecrell.pluginyml.bukkit.BukkitPluginDescription.PluginLoadOrder.STARTUP
    main = "com.airdropmc.Airdrop"
    apiVersion = "1.21"
    depend = listOf("LuckPerms")
    softDepend = listOf("Vault", "Treasury")
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
