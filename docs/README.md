# Set up and use Airdrop

Airdrop delivers item packages from the sky in crates carried by chickens.
You choose the items, the price, and who can request each package.

## Install Airdrop and try the starter package

1. [Download Airdrop](https://modrinth.com/plugin/airdrop/versions) for Paper
   **1.21.11** and **Java 21**. Put the JAR in your server's `plugins/` folder
   and restart the server.
2. For paid packages, install **VaultUnlocked or Vault** and an economy plugin,
   such as EssentialsX. Run `/airdrop status` as an operator to check the setup.
3. As an operator with at least **10** in your server's currency, stand under
   clear sky and run `/airdrop starter`. Open the crate after it lands.

On a fresh installation, the starter contains iron armor and bread and costs
**10**. Regular players cannot request it until you grant
`airdrop.package.starter`. Operators already have access and pay the same price.

[Read the installation guide](reference.md#installation) for economy setup and
first-start details.

## Choose what your players can receive

- [Create a package and set its price](reference.md#paid-setup).
- [Choose who can request packages](reference.md#commands-and-permissions).
- [Edit package contents and understand `packages.yml`](reference.md#package-files).
- [Adjust effects, cooldowns, and crate limits](reference.md#configuration).
- [Translate messages and change chat colors](reference.md#localization).

## Fix a problem or upgrade your server

- [Check permissions, payment failures, and other common problems](reference.md#troubleshooting).
- [Back up your files and upgrade safely](reference.md#backups-make-regeneration-and-rollback-predictable).
- [See what changed in 4.1](../CHANGELOG.md).
- [Report a bug](https://github.com/LukeMccon/Airdrop/issues/new?labels=bug).

## Connect another plugin to Airdrop

- [Use the Java API](reference.md#developer-integration).
- [Update an existing integration for 4.1](migration-4.1.md).
- [Check the API version policy](development/api-versioning.md).

[Browse the full reference](reference.md) for all commands, settings, and API details.
