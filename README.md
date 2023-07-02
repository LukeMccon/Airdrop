# Airdrop

A Spigot plugin that allows players to call in customizable care packages that fall from the sky

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
      '1':
        ==: org.bukkit.inventory.ItemStack
        v: 3337
        type: IRON_HELMET
      '2':
        ==: org.bukkit.inventory.ItemStack
        v: 3337
        type: IRON_CHESTPLATE
      '3':
        ==: org.bukkit.inventory.ItemStack
        v: 3337
        type: IRON_LEGGINGS
      '4':
        ==: org.bukkit.inventory.ItemStack
        v: 3337
        type: IRON_BOOTS
      '5':
        ==: org.bukkit.inventory.ItemStack
        v: 3337
        type: BREAD
        amount: 2
    price: 10
  mypackage:
    items:
      '1':
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
