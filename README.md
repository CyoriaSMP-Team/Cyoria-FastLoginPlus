# FastLoginPlus

[中文→](README_zh.md)

> **Auto-detect and login premium Minecraft players on offline-mode servers** — no password needed, no client mods required. Actively maintained fork of [FastLogin](https://github.com/TuxCoding/FastLogin).

Many Minecraft servers run in "offline mode" (no Mojang authentication) to allow cracked clients, but this forces all players — including those with paid accounts — to type a password every time they join. FastLoginPlus checks each player against Mojang's API on login: if they own the game, they skip the auth plugin entirely and get their real UUID and skin automatically.

> Works with [ProtocolLib](https://github.com/dmulloy2/ProtocolLib). [ForDetails→](PROTOCOLLIB-ASYNC-DESIGN.md)

> **CyoriaSMP edition — verified-once authentication (Folia 1.21.11).** This branch includes an opt-in Java password enrollment policy. Genuine Java players choose their own AuthMe password on the first verified login, then can auto-login on future verified connections. Floodgate-verified Bedrock players can be auto-approved. The generic FastLoginPlus behavior described elsewhere in this README is unchanged when this policy is disabled.
>
> **Implementation details, deployment and rollback:** [Cyoria Java Enrollment & Floodgate Auto-Approval](docs/CYORIA-JAVA-PASSWORD-ENROLLMENT.md). The candidate has passed compilation and targeted policy tests and has started on the CyoriaSMP production server; full Java/Bedrock client acceptance testing is still pending.

## Features

### Core (from FastLogin)

* Auto-detect premium accounts via Mojang API — skip auth plugin login
* Premium UUID and skin forwarding
* Auto-register new premium players
* BungeeCord/Velocity proxy support
* Bedrock player support via Geyser/Floodgate

### Improvements (new in FastLoginPlus)

* **AuthMeReloaded 6.0 compatibility** — auto-detects AuthMeReloaded version without user config
* **CyoriaSMP verified-once Java enrollment (opt-in)** — require a real player-chosen AuthMe password exactly once, then allow auto-login only for previously enrolled Mojang-verified Java accounts; no empty/generated password enrollment
* **Offline Whitelist** — block unknown cracked players, allow premium via Mojang API. Replaces upstream `switchMode` which kicked new premium players
* **Multi-layer anti-bot** — per-IP rate limiting, burst detection, temporary IP ban, trusted IP whitelist, and `FastLoginAntiBotEvent` for plugin integration
* **[Folia](https://papermc.io/downloads/folia) support** — dedicated module with Folia-compatible scheduler
* **Auto update check** — checks GitHub Releases on startup and periodically; notifies OPs in-game when a new version is available
* **Multi-language** — built-in English and Chinese, custom language files supported
* **Per-platform config templates** — Bukkit/Folia and BungeeCord/Velocity each generate their own config file: proxies get a trimmed template without backend-only keys, and backend comments mark the keys that lose effect behind a proxy
* **SQLite on proxy platforms** — BungeeCord and Velocity now bundle SQLite JDBC driver; upstream only supports MySQL/MariaDB on proxies
* **Session retry** — Mojang verification retries on network errors instead of failing immediately
* **[SkinsRestorer](https://modrinth.com/plugin/skinsrestorer) compatibility** — no longer overrides skins set via SkinsRestorer
* **Log readability** — human-readable login flow messages instead of raw packet dumps

## Quick Start

**Spigot/Paper**: drop `FastLoginPlusBukkit.jar`, an auth plugin and [ProtocolLib 5.3+](https://github.com/dmulloy2/ProtocolLib) in `plugins/` → set `online-mode=false`

**Folia**: drop `FastLoginPlusFolia.jar`, an auth plugin and [ProtocolLib 5.3+](https://github.com/dmulloy2/ProtocolLib) in `plugins/` → set `online-mode=false`

### CyoriaSMP: Java Register Once / Bedrock Auto Approve

This is **CyoriaSMP-specific behavior**, not the default of the upstream FastLoginPlus fork. It was implemented for the Folia + AuthMe 6.0.1 + Geyser/Floodgate stack. It is enabled only when `requireJavaPasswordEnrollment: true`; other deployments keep the existing behavior by default.

| Connection | First login | Next connection |
| --- | --- | --- |
| **Java, valid Mojang session, new account** | Complete `/register <password> <password>` using a password the player selects; do **not** create a random/empty-password AuthMe account | Auto-login once enrolled and the Mojang session is verified again |
| **Java, valid Mojang session, existing AuthMe account** | Enter the existing AuthMe password once to bind the verified Java identity | Auto-login once linked and verified |
| **Java, Mojang authentication not verified** | Fall back to offline/regular AuthMe password authentication; the premium bypass must not be granted (a reconnect may be required) | Still requires the password unless premium verification and enrollment both succeed |
| **Bedrock, authenticated via Floodgate** | Auto-registration and approval, using Floodgate's verified identity | Auto-login; do not infer Bedrock authentication from username alone |

**Example `plugins/FastLoginPlus/config.yml` (standalone Folia / CyoriaSMP):**

```yaml
autoRegister: true
autoLogin: true
requireJavaPasswordEnrollment: true
auto-register-unknown: false
secondAttemptCracked: true
autoLoginFloodgate: true
autoRegisterFloodgate: true
allowFloodgateNameConflict: false
```

`autoRegister: true` permits the plugin to detect new genuine Java players and start Mojang verification; **it does not bypass the one-time password requirement** when `requireJavaPasswordEnrollment` is enabled. Java auto-login additionally requires a previously bound FastLogin profile and a real stored AuthMe password hash. Failed verification never proves identity. Bedrock auto-approval is limited to connections that Floodgate actually authenticates; Java/Bedrock name-conflict protections stay enabled.

**Important:** The new policy is opt-in; it is not a universal configuration for other FastLoginPlus installations. Use the matching Folia build and AuthMe integration, back up AuthMe/FastLogin databases and the plugin before deploying, and test all four connection paths on your own instance. Never replace the JAR while the Minecraft process is running. See the [detailed flow, tests and rollback procedure](docs/CYORIA-JAVA-PASSWORD-ENROLLMENT.md).

### Proxy Configuration

When running behind a proxy (BungeeCord or Velocity), the proxy must be configured to forward player information to backend servers. FLP relies on this to deliver login commands via plugin messages.

<details>
<summary>Proxy ID setup (click to expand)</summary>

The backend only accepts login commands from trusted proxies. Each proxy has a unique UUID that must be added to the backend's whitelist:

- **Velocity** — FLP auto-generates a UUID to `plugins/fastloginplus/proxyId.txt` on first start. Copy the UUID from that file.
- **BungeeCord** — uses BungeeCord's own instance UUID, i.e. the `stats` value in BungeeCord's `config.yml` (BungeeCord writes it there on first start and reuses it; deleting that line makes it generate a new one, which invalidates every backend whitelist).

Paste the UUID into `plugins/fastloginplus/allowed-proxies.txt` on every backend server, one UUID per line. Restart the backends after adding the UUID.

</details>

#### Velocity

| Setting | Value | Why |
|---------|-------|-----|
| `velocity.toml` → `player-info-forwarding-mode` | `modern` | **Required**. Without this, Velocity does not forward UUIDs, skins, or IPs — FLP's plugin messages will never reach the backend. |
| `velocity.toml` → `online-mode` | `false` | FLP handles authentication per-connection via `forceOnlineMode()`; the proxy should not authenticate by default. |
| Backend `server.properties` → `online-mode` | `false` | The proxy handles authentication; the backend must not repeat it. |
| Backend `paper-global.yml` → `proxies.velocity.online-mode` | `false` | Must match `velocity.toml`'s `online-mode`. |

#### BungeeCord

| Setting | Value | Why |
|---------|-------|-----|
| `config.yml` → `ip_forward` | `true` | **Required**. Without this, BungeeCord does not forward UUIDs, skins, or IPs — FLP's plugin messages will never reach the backend. |
| `config.yml` → `online_mode` | `false` | FLP enables per-connection via `connection.setOnlineMode(true)`; the proxy should not authenticate by default. |
| Backend `server.properties` → `online-mode` | `false` | The proxy handles authentication; the backend must not repeat it. |

### Database Storage

In **standalone mode** (no proxy), the database (`FastLogin.db` by default) is stored on each backend server under `plugins/fastloginplus/`.

In **proxy mode** (BungeeCord/Velocity), the database is stored **only on the proxy**. Backend servers do not create a database — they simply execute the login/register commands sent by the proxy via plugin messages. `/flp premium` and `/flp cracked` commands on the backend forward to the proxy, and the proxy handles all profile reads and writes.

### Configuration Templates

FLP ships **two config templates**; each platform generates its `config.yml` from the one that matches its role:

| Template | Used by | Contents |
|----------|---------|----------|
| `config.yml` (backend) | Bukkit, Folia | All keys. Comments mark which keys have no effect (or only partial effect) when the server runs **behind a proxy** — e.g. `database`, `anti-bot`, Floodgate keys are ignored on a proxy backend because the proxy owns those functions. |
| `config-proxy.yml` (proxy) | BungeeCord, Velocity | Proxy-relevant keys only. Backend-only keys (`verifyClientKeys`, `respectIpLimit`) are omitted, and comments describe the proxy's role (decision maker: Mojang API queries, database, force-login forwarding). |

The file on disk is always named `config.yml`. Copying a config file between a proxy and a backend is safe: each platform regenerates the file structure from its own template on startup while preserving your values, and keys missing from the new template are simply dropped (they had no effect there anyway).

## Environment

| Platform | Java | Notes |
|----------|------|-------|
| Spigot / Paper | 8+ | Requires [ProtocolLib 5.3+](https://github.com/dmulloy2/ProtocolLib) |
| Folia | 21+ | Requires [ProtocolLib 5.3+](https://github.com/dmulloy2/ProtocolLib) |
| BungeeCord / Waterfall | 17+ | — |
| Velocity | 17+ | — |

An auth plugin is required on the backend (e.g. AuthMe, LoginSecurity, CrazyLogin). [SeeFullList→](https://github.com/TuxCoding/FastLogin#supported-auth-plugins)

## [AuthMeReloaded](https://modrinth.com/plugin/authmereloaded) Support (5.x / 6.0)

FastLoginPlus supports both AuthMeReloaded 5.x and 6.0. AuthMeReloaded 6.0 adds the **preJoin dialogue (Paper) and the enablePremium configuration**. FLP automatically enables `enablePremium: true` and unregisters AuthMe's built-in premium verification listener.

While FLP handles premium authentication, AuthMe's native `/premium` and `/freemium` commands are blocked. Players will be prompted to use `/flp premium` / `/flp cracked` instead.

If AuthMe's proxy plugin (`AuthMeBungee` / `AuthMeVelocity`) is installed, FLP also locks `premium.keepOfflineUuidCompatibility` to `false` (AuthMe's default value). In other words, whether the backend receives a premium UUID or an offline UUID is solely determined by FLP's own `premiumUuid` configuration.

## [Geyser](https://geysermc.org/)/[Floodgate](https://geysermc.org/floodgate/) Support

FastLoginPlus works with Geyser to allow Bedrock players to join your offline-mode Java server.

- **Geyser only** — Bedrock players join without Xbox authentication. FLP treats them as regular Java players; premium auto-login works if the username matches a paid Java account.
- **Geyser + Floodgate** (recommended) — Bedrock players authenticate via Xbox Live, and their usernames are prefixed (e.g. `Steve` → `.Steve`). This prevents FLP from mistaking a Bedrock player for a premium Java account and avoids username conflicts between platforms.

> **Recommendation:** Install Floodgate alongside Geyser for better security and identity separation. FLP does not require Floodgate to function, but it is strongly recommended when both Java and Bedrock players share the same server.

> **Version requirements:** Geyser requires **Java 21+** to run. Geyser-Spigot requires a Paper/Spigot server on **1.20.5 or above**. Servers below 1.20.5 can still use Geyser by installing [ViaVersion](https://viaversion.com/) on the backend and running Geyser on a proxy (Velocity/BungeeCord), or by using Geyser-Standalone with ViaVersion. ViaVersion allows the server to accept newer Java clients, which Geyser uses as the translation target. See [Geyser supported versions](https://geysermc.org/wiki/geyser/supported-versions/) for details.

## Commands & Permissions

| Command | Description | Permission | Default |
|---------|-------------|------------|---------|
| `/flp premium [player]` | Mark as premium | `fastloginplus.bukkit.command.premium` | true |
| `/flp cracked [player]` | Mark as cracked | `fastloginplus.bukkit.command.cracked` | op |
| `/flp delete <player>` | Delete player record | `fastloginplus.bukkit.command.delete` | op |

Add `.other` suffix for targeting other players (default: op).

- A bare `/flp` prints the version and usage, and requires server operator permission.
- On Folia the permission prefix is `fastloginplus.folia.command.*` instead of `fastloginplus.bukkit.command.*`.

**Account safety when switching modes:** The original FastLoginPlus implementation can remove AuthMe records when `/flp cracked` is used. With the **Cyoria verified-once enrollment policy enabled**, the AuthMe password and account are preserved: only a stale premium authentication marker is cleared. This avoids destroying an existing player-chosen password. Treat other auth-plugin integrations separately; the Cyoria enrollment behavior is designed around AuthMe 6.0 and is not guaranteed for unrelated auth plugins.

## PlaceholderAPI

| Placeholder | Values | Description |
|---|---|---|
| `%fastloginplus_status%` | `Premium`, `Cracked`, `Unknown` | Authentication status |
| `%fastloginplus_is_premium%` | `true`, `false` | Whether the player passed premium verification |
| `%fastloginplus_floodgate%` | `Java`, `Bedrock`, `Linked`, `Unknown` | Connection platform (Java vs Bedrock via Geyser/Floodgate) |

## License

[MIT](LICENSE) · Originally by [games647](https://github.com/TuxCoding/FastLogin) · Maintained by [Hayston](https://github.com/Hayston1001)
