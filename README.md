<div style="text-align: center;" align="center">

<img src="readme/airdrop-banner.png" height="270px" width="200px"/>

<h3> <i> From the skies! </i> </h3>

<br />

![Paper SVG](https://img.shields.io/badge/Paper-1.21+-blue.svg) ![Java SVG](https://img.shields.io/badge/Java-21-orange.svg)

[![License](https://img.shields.io/badge/license-MIT-blue.svg)](LICENSE)
[![Minecraft](https://img.shields.io/badge/Minecraft-1.21+-brightgreen.svg)](https://www.minecraft.net) ![CI](https://github.com/LukeMccon/Airdrop/actions/workflows/ci.yml/badge.svg) [![Download](https://img.shields.io/badge/download-latest-brightgreen.svg)](https://github.com/LukeMccon/Airdrop/releases/latest)

A Paper plugin for customizable care packages with parachutes, effects, economy support, and in-game package editing.

</div>

## Features

- Configurable parachute drops with custom chicken count, fall speed, and drop height
- Landing, flare, glow, and optional smoke particle effects
- In-game GUI package creation and editing
- Economy support through Treasury (preferred) with Vault fallback
- Refund protection when a charged drop fails before spawning
- Language file support (`lang/<language>.yml`) and configurable chat theme colors
- Runtime reload command for config, language, and packages

## Requirements

- Paper `1.21+`
- Java `21`
- [LuckPerms](https://luckperms.net/) (required)
- Economy provider when `economy.enabled: true` (default):
  - Treasury-compatible provider, or
  - [Vault](https://github.com/milkbowl/Vault) with a Vault-compatible economy plugin

If no economy provider is installed, set `economy.enabled: false` in `config.yml` before starting.

## Installation

1. Install plugin dependencies into your server `plugins/` directory:
   - LuckPerms
   - Optional: Vault and your economy plugin (if you keep economy enabled)
2. Download the latest Airdrop release from [Releases](https://github.com/LukeMccon/Airdrop/releases/latest).
3. Place the Airdrop `.jar` in `plugins/`.
4. Start or restart the server.

## Quick Start

1. Create a package:

```bash
/airdrop package create starter 10
```

2. Add items in the package editor GUI and click `Save`.
3. Grant players package permissions, for example:

```bash
/lp group default permission set airdrop.package.starter true
```

4. Call in the package:

```bash
/airdrop starter
```

## Commands

All commands start with `/airdrop` (aliases: `/drop`, `/ad`).

- `/airdrop <packageName>`
  - Player-only
  - Drops a package at your location
  - Requires package permission
- `/airdrop package <name>`
  - Shows package details and item list
- `/airdrop package create <name> <price>`
  - Admin-only
  - Player-only (opens GUI)
- `/airdrop package delete <name>`
  - Admin-only
- `/airdrop packages`
  - Admin-only
  - Player-only (opens package management GUI)
- `/airdrop reload`
  - Admin-only
  - Reloads main config, language, and packages
- `/airdrop version`
  - Shows plugin and API versions

## Permissions

- `airdrop.admin`
  - Full administrative access
- `airdrop.package.all`
  - Use all packages
- `airdrop.package.<packageName>`
  - Use one specific package
- `airdrop.package.*`
  - Wildcard alias for package usage

LuckPerms integration also ensures these groups exist:
- `airdrop-admin` with `airdrop.admin`
- `airdrop-user` with `airdrop.package.all`

## Configuration

Main settings are in `plugins/Airdrop/config.yml`.

```yaml
language: en

drop:
  parachute:
    chicken-count: 5
  particles:
    landing-effects: true
    continuous-effects: true
    flare-effects: true
    smoke:
      enabled: false
      height: 20
  falling-speed: 0.3
  height: 20

economy:
  enabled: true

logging:
  debug: false

ui:
  chat:
    colors:
      primary: BLUE
      text: WHITE
      accent: AQUA
      success: GREEN
      warning: YELLOW
      error: RED
      error-detail: DARK_RED
```

Validation ranges:
- `drop.parachute.chicken-count`: `1` to `64`
- `drop.falling-speed`: `0.01` to `4.0`
- `drop.height`: `1` to `320`
- `drop.particles.smoke.height`: `0` to `128`

Packages are stored in `plugins/Airdrop/packages.yml` and can be managed in-game.
A package can contain up to `27` item stacks (barrel capacity).

## Build and Test

- Build plugin jar: `./gradlew clean build`
- Run tests: `./gradlew test`
- Start local Paper test server: `./gradlew runServer`
