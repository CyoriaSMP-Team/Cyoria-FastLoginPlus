# CyoriaSMP FastLoginPlus

[![Build](https://github.com/CyoriaSMP-Team/Cyoria-FastLoginPlus/actions/workflows/gradle.yml/badge.svg)](https://github.com/CyoriaSMP-Team/Cyoria-FastLoginPlus/actions/workflows/gradle.yml)
[![CodeQL](https://github.com/CyoriaSMP-Team/Cyoria-FastLoginPlus/actions/workflows/codeql-analysis.yml/badge.svg)](https://github.com/CyoriaSMP-Team/Cyoria-FastLoginPlus/actions/workflows/codeql-analysis.yml)
[![License: MIT](https://img.shields.io/badge/License-MIT-blue.svg)](LICENSE)
[![GitHub contributors](https://img.shields.io/github/contributors/CyoriaSMP-Team/Cyoria-FastLoginPlus)](https://github.com/CyoriaSMP-Team/Cyoria-FastLoginPlus/graphs/contributors)
[![GitHub issues](https://img.shields.io/github/issues/CyoriaSMP-Team/Cyoria-FastLoginPlus)](https://github.com/CyoriaSMP-Team/Cyoria-FastLoginPlus/issues)

**A security-conscious Minecraft authentication bridge for premium Java identities, offline-mode servers and supported Bedrock networks.**

CyoriaSMP FastLoginPlus is a maintained fork of [FastLoginPlus by Hayston1001](https://github.com/Hayston1001/FastLoginPlus), itself based on [FastLogin by games647](https://github.com/TuxCoding/FastLogin). It connects verified player identities with your server's authentication plugin, enabling eligible premium players to log in without repeatedly entering a password while preserving password-based login for other players.

> This repository is a community fork maintained by **CyoriaSMP-Team**. It is not an official Mojang/Microsoft product and is not affiliated with the upstream maintainers.

---

## Contents

- [Highlights](#highlights)
- [Supported platforms](#supported-platforms)
- [How authentication works](#how-authentication-works)
- [Security model](#security-model)
- [Requirements](#requirements)
- [Installation overview](#installation-overview)
- [Commands and permissions](#commands-and-permissions)
- [Configuration](#configuration)
- [Build from source](#build-from-source)
- [Documentation and support](#documentation-and-support)
- [Credits and license](#credits-and-license)

## Highlights

- **Verified premium login** — verifies Java account ownership through the appropriate Mojang/session or proxy login flow. A username match alone never grants premium access.
- **Passwordless login integration** — coordinates with supported authentication plugins to register or authenticate eligible verified players.
- **Existing-account protection** — an existing password account is not silently taken over just because its name matches a premium Minecraft profile. Account binding requires the account owner's action.
- **Proxy-aware authentication** — centralizes identity decisions and premium/cracked records on BungeeCord or Velocity, with backend account handling.
- **Folia support** — provides a separate Folia artifact using Folia-compatible scheduling.
- **Bedrock integration** — supports selected Geyser/Floodgate login flows, subject to platform and authentication-plugin limitations.
- **Skin and UUID handling** — forwards verified profile textures and supports premium/offline UUID policies; custom SkinsRestorer skins are respected on supported integrations.
- **Anti-bot controls** — configurable global and per-IP rate limits, burst detection, temporary bans and trusted IP exceptions.
- **Database options** — SQLite on supported platforms and MySQL/MariaDB options depending on the proxy/platform artifact.
- **Operational tools** — localized messages, update checks, diagnostics and account-management commands.

## Supported platforms

| Artifact | Role | Notes |
| --- | --- | --- |
| **Bukkit** | Standalone Spigot/Paper backend | Requires a compatible packet integration and authentication plugin |
| **Folia** | Standalone or proxy-connected Folia backend | Use Folia-compatible dependencies |
| **BungeeCord** | Proxy | Performs network-level identity and policy decisions |
| **Velocity** | Proxy | Recommended for new Paper/Folia networks; modern forwarding is preferred |
| **Fabric 26.2** | Fabric backend behind Velocity | Initial integration using FabricProxy-Lite and EasyAuth 3.4.x |

The project builds five installable artifacts: Bukkit, Folia, BungeeCord, Velocity and Fabric 26.2. Compatibility depends on the exact Minecraft/server, Java, proxy, packet library and authentication-plugin versions. The artifact list is not a promise that every combination has been tested.

### Important compatibility notes

- Fabric support currently targets **Minecraft 26.2** and a Velocity modern-forwarding setup. It is not a standalone authentication mode and does not support BungeeCord forwarding.
- The Fabric integration uses EasyAuth; the documented reference version is 3.4.4. Bedrock support is narrower than the classic Bukkit/proxy integrations.
- Standalone Bukkit/Folia needs a compatible packet library. Proxy-connected backends do not use FLP's ProtocolLib interception for the proxy login flow.
- AuthMe 5.x and 6.x have different integration behavior. Consult the documentation before changing premium settings.
- Use a Java runtime that satisfies the highest requirement among your Minecraft server, proxy and plugins. FLP artifact bytecode floors are not the same as the runtime requirements of the full server stack.

## How authentication works

1. A player connects using a supported Java or Bedrock route.
2. FLP determines which verification flow applies and verifies identity through the relevant server/proxy integration.
3. FLP checks its premium/cracked policy and the local authentication-plugin account state.
4. If identity, policy and account ownership all permit it, the authentication plugin can complete automatic registration or passwordless login.
5. Players who are not eligible continue through the configured password-authentication flow or are rejected by admission policy.

**Admission, identity verification and access to an authentication account are separate decisions.** Passing one does not automatically grant the others.

### Existing password accounts

A premium username does not automatically take ownership of an existing offline/password account. The account owner must authenticate using the existing method and explicitly bind the premium identity through the supported flow. This protects players from account takeover caused by username collisions.

## Security model

- Never trust a username, UUID format, skin texture or unverified plugin message as proof of account ownership.
- In proxy deployments, configure forwarding correctly and restrict backend ports so untrusted clients cannot bypass the proxy.
- Keep Velocity forwarding secrets, database credentials and proxy identifiers private.
- Back up authentication data and player data before changing UUID policy or switching authentication modes.
- An identity/database lookup failure must not be treated as successful authentication.
- Anti-bot rate limits are an additional control, **not** a replacement for firewalling, proxy security or DDoS protection.

Read the [login flow and identity trust notes](docs/en/LOGIN-FLOW.md) before deploying this on a public network.

## Requirements

Requirements vary by artifact and server version. At a minimum:

- A supported platform listed above.
- A compatible authentication plugin (for example, AuthMeReloaded on supported Bukkit/Paper/Folia setups, or EasyAuth for the documented Fabric integration).
- A compatible packet library for standalone Bukkit/Folia deployments.
- Correct proxy forwarding and backend isolation for network deployments.
- Supported Geyser/Floodgate versions when enabling Bedrock login.

For the full compatibility guidance and deployment choices, see [Requirements](docs/en/overview/requirements.md).

## Installation overview

1. Download the artifact that matches your platform from [GitHub Releases](https://github.com/CyoriaSMP-Team/Cyoria-FastLoginPlus/releases).
2. Install it on the correct server or proxy. Do not install the `-plain.jar` artifact.
3. Install and configure the required authentication and platform dependencies.
4. Start once to generate configuration files, then stop the server and review the generated configuration.
5. Configure online/offline mode, forwarding, UUID policy, account-binding policy and database settings for your deployment.
6. Restart and test with separate accounts: a verified premium account, an existing password account and an unregistered account.

### Choose an installation guide

| Deployment | Guide |
| --- | --- |
| Standalone Spigot/Paper | [Bukkit installation](docs/en/installation/standalone.md) |
| Standalone Folia | [Folia installation](docs/en/installation/folia.md) |
| Velocity + Paper/Folia/Spigot | [Velocity installation](docs/en/installation/velocity.md) |
| BungeeCord + Bukkit/Paper/Folia | [BungeeCord installation](docs/en/installation/bungeecord.md) |
| Velocity + Fabric 26.2 + EasyAuth | [Fabric installation](docs/en/installation/fabric.md) |

## Commands and permissions

Commands are exposed under the `/flp` namespace. Exact availability depends on platform and whether the command is run on a backend or proxy.

| Command | Purpose |
| --- | --- |
| `/flp` | Show the command help available on the current platform |
| `/flp premium [player]` | Mark/bind a player as premium, subject to the identity and confirmation flow |
| `/flp cracked [player]` | Switch a player to the password/offline login route |
| `/flp delete [player]` | Delete a player's FLP record; use carefully |

Typical Bukkit/Folia permission nodes use the `fastloginplus.bukkit.command.*` or `fastloginplus.folia.command.*` prefix. Proxy permissions use their platform-specific prefix. See the [commands and permissions reference](docs/en/accounts/commands.md) for the complete list, defaults and platform notes.

> Administrative account commands can affect player access. Back up relevant databases and verify the target before using them.

## Configuration

FLP generates platform-specific configuration files on first startup. Do not copy a Bukkit configuration blindly to a proxy or Fabric installation; options and responsibilities differ by platform.

Key settings include:

| Setting | Purpose |
| --- | --- |
| `autoRegister` | Select eligible new players for the premium verification/registration flow |
| `autoLogin` | Allow eligible verified players to bypass password entry |
| `auto-register-unknown` | Control automatic local account creation for eligible verified Java players |
| `premiumUuid` | Choose premium or offline UUID handling; changing this can affect player data |
| `offline-whitelist` | Restrict offline Java admission to names already known to FLP |
| `forwardSkin` | Forward verified profile textures |
| `anti-bot.*` | Configure global/per-IP limits and temporary bans |

These settings are policy controls, not substitutes for identity verification. Read the relevant platform reference before changing them:

- [Bukkit/Folia configuration](docs/en/configuration/bukkit.md)
- [Velocity configuration](docs/en/configuration/velocity.md)
- [BungeeCord configuration](docs/en/configuration/bungeecord.md)
- [Fabric configuration](docs/en/configuration/fabric.md)

## Build from source

The repository uses Gradle. **JDK 25 is required to run the Gradle build**; classic modules use a Java 21 toolchain and the Fabric 26.2 module also requires Java 25.

```bash
git clone https://github.com/CyoriaSMP-Team/Cyoria-FastLoginPlus.git
cd Cyoria-FastLoginPlus

# Build and run checks
./gradlew build --console=plain

# Build all installable artifacts and stage them in build/release/
./gradlew release --console=plain
```

On Windows, use `gradlew.bat` instead of `./gradlew`.

The release task builds all modules and stages the five installable JARs in `build/release/`. Do not distribute `-plain.jar` files as server plugins.

## Documentation and support

- **Documentation:** [English manual](docs/en/README.md) · [中文文档](docs/zh/README.md)
- **Changelog:** [CHANGELOG.md](CHANGELOG.md) · [中文更新日志](CHANGELOG_zh.md)
- **Report a bug:** [GitHub Issues](https://github.com/CyoriaSMP-Team/Cyoria-FastLoginPlus/issues)
- **Request a feature:** [GitHub Discussions](https://github.com/CyoriaSMP-Team/Cyoria-FastLoginPlus/discussions) (when enabled) or open an issue
- **Source code:** [CyoriaSMP-Team/Cyoria-FastLoginPlus](https://github.com/CyoriaSMP-Team/Cyoria-FastLoginPlus)

When reporting a problem, include the FLP version, artifact/platform, Minecraft/server version, Java version, proxy mode, authentication plugin/version and a sanitized log/config excerpt. Never post passwords, forwarding secrets, tokens or database credentials.

## Credits and license

This project preserves the FastLogin lineage and acknowledges the work of its upstream authors and contributors:

- **FastLoginPlus upstream:** [Hayston1001/FastLoginPlus](https://github.com/Hayston1001/FastLoginPlus)
- **Original FastLogin:** [TuxCoding/FastLogin](https://github.com/TuxCoding/FastLogin)
- **Maintained fork:** [CyoriaSMP-Team/Cyoria-FastLoginPlus](https://github.com/CyoriaSMP-Team/Cyoria-FastLoginPlus)

Licensed under the [MIT License](LICENSE). Original copyright and contributor notices remain in the source files.
