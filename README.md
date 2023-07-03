<div style="text-align: center;" align="center">
<h1>Airdrop</h1>

![Spigot SVG](readme/spigot-1.19.svg) ![Java SVG](readme/java-19.svg)

[![License](https://img.shields.io/badge/license-MIT-blue.svg)](LICENSE)
[![Minecraft](https://img.shields.io/badge/Minecraft-1.16+-brightgreen.svg)](https://www.minecraft.net) [![Build Status](https://img.shields.io/travis/your-username/airdrop-plugin/master.svg)](https://travis-ci.org/your-username/airdrop-plugin) [![Download](https://img.shields.io/badge/download-latest-brightgreen.svg)](https://github.com/your-username/airdrop-plugin/releases/latest)

A Spigot plugin that allows players to call in customizable care packages that fall from the sky

</div>

## Index

- [Usage](#usage)
- [Installation](#installation)
- [Configuration](#configuration)
- [Commands](#commands)

## Usage

```
/airdrop starter # drops the starter package
```

## Installation

Install the following plugins:

Download and copy the `.jar` files for these plugins into your `plugins` folder:

- [LuckPerms](https://luckperms.net/)
- [EssentialsX](https://essentialsx.net/)

Download the latest stable release of `Airdrop`
Place the `.jar` into your plugins folder

## Configuration

#### Setting up packages

In your `plugins` folder look for the `Airdrop` folder.
Open the `packages.yml` in your favorite text editor (reccommended to use one that supports YAML)

Use the existing `starter` package as an example.

An example of adding another package to the config in addition to the `starter` package:

```yaml
packages:
  starter:
    items:
      "1":
        ==: org.bukkit.inventory.ItemStack
        v: 3337
        type: IRON_HELMET
      "2":
        ==: org.bukkit.inventory.ItemStack
        v: 3337
        type: IRON_CHESTPLATE
      "3":
        ==: org.bukkit.inventory.ItemStack
        v: 3337
        type: IRON_LEGGINGS
      "4":
        ==: org.bukkit.inventory.ItemStack
        v: 3337
        type: IRON_BOOTS
      "5":
        ==: org.bukkit.inventory.ItemStack
        v: 3337
        type: BREAD
        amount: 2
    price: 10
  mypackage:
    items:
      "1":
        ==: org.bukkit.inventory.ItemStack
        v: 3337
        type: OAK_LOG
        amount: 3
    price: 3
```

## Commands

`/airdrop <command> [args]`

##### /airdrop [packageName]

Drops a package on the current players location

##### /airdrop version

Displays the current version of the plugin

##### /airdrop packages

Lists all the airdrop packages that are installed

##### /airdrop package [packageName]
