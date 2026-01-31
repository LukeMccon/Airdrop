<div style="text-align: center;" align="center">

<img src="readme/airdrop-banner.png" height="270px" width="200px"/>

<h3> <i> From the skies! </i> </h3>

<br />

![Paper SVG](https://img.shields.io/badge/Paper-1.21+-blue.svg) ![Java SVG](https://img.shields.io/badge/Java-21-orange.svg)

[![License](https://img.shields.io/badge/license-MIT-blue.svg)](LICENSE)
[![Minecraft](https://img.shields.io/badge/Minecraft-1.21+-brightgreen.svg)](https://www.minecraft.net) ![build and release](https://github.com/LukeMccon/Airdrop/actions/workflows/main.yml/badge.svg) [![Download](https://img.shields.io/badge/download-latest-brightgreen.svg)](https://github.com/LukeMccon/Airdrop/releases/latest)

A Paper plugin that allows players to call in customizable care packages that fall from the sky

</div>

## Index

- [Usage](#usage)
- [Installation](#installation)
- [Configuration](#configuration)
- [Commands](#commands)

## Usage

As a server operator you can call in packages using:

```
/airdrop starter # drops the starter package
```

Otherwise, using LuckPerms you can allow everyone to use packages through _either_ of these methods:

```
/lp group default permission set airdrop.package.all
```

OR

Add the `airdrop-user` group to the desired players

## Installation

Install the following plugins:

Download and copy the `.jar` files for these plugins into your `plugins` folder:

- [LuckPerms](https://luckperms.net/)
- [Vault](https://github.com/milkbowl/Vault)

Download the latest stable release of `Airdrop` [here](https://github.com/LukeMccon/Airdrop/releases/latest)
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
    - ==: org.bukkit.inventory.ItemStack
      v: 3465
      type: IRON_HELMET
    - ==: org.bukkit.inventory.ItemStack
      v: 3465
      type: IRON_CHESTPLATE
    - ==: org.bukkit.inventory.ItemStack
      v: 3465
      type: IRON_LEGGINGS
    - ==: org.bukkit.inventory.ItemStack
      v: 3465
      type: IRON_BOOTS
    - ==: org.bukkit.inventory.ItemStack
      v: 3465
      type: BREAD
      amount: 2
    price: 10.0
```

## Commands

All commands start with `/airdrop`. The following commands are available:

### Dropping a package

##### `/airdrop <packageName>`
- Drops the specified package at your location
- Requires one of:
  - `airdrop.package.all` permission
  - `airdrop.package.<packageName>` permission for specific package
  - Membership in the `airdrop-user` luckperms group
- Costs in-game currency if package has a price set

### Package Management

##### `/airdrop package create <name> <price>`
- Creates a new package with the specified name and price
- Opens a GUI to configure the items in the package
- Requires `airdrop.admin` permission or server op

##### `/airdrop package delete <name>`
- Deletes an existing package
- Requires `airdrop.admin` permission or server op

##### `/airdrop package <name>`
- Shows detailed information about a specific package
- Available to all players

##### `/airdrop packages`
- Opens a GUI showing all available packages
- Available to all players

### Other Commands

##### `/airdrop version`
- Displays the current version of the plugin and API version

### Permissions

- `airdrop.admin`: Full access to all plugin features (recommended for admins)
- `airdrop.package.all`: Access to use all packages
- `airdrop.package.<packageName>`: Access to use a specific package
- `airdrop-user` group: Access to use all packages (alternative to individual permissions)
- `airdrop-admin` group: Full administrative access (alternative to individual permissions)
