# Changelog

All notable changes to CyoriaSMP-Team's FastLoginPlus fork are documented here.

- [English](CHANGELOG.md) | [简体中文](CHANGELOG_zh.md)
- **Release:** [v1.0.0](https://github.com/CyoriaSMP-Team/Cyoria-FastLoginPlus/releases/tag/v1.0.0)
- **Documentation:** [English documentation](docs/en/README.md) | [中文文档](docs/zh/README.md)

---

## [1.0.0] - Stable

> First stable release of the CyoriaSMP-Team FastLoginPlus fork. This release introduces a redesigned authentication decision flow, signed proxy identity facts, safer account handling, and the initial Fabric 26.2 integration.

### Added

#### Fabric 26.2 and EasyAuth

- **Fabric 26.2 backend:** Added a Fabric server backend that integrates with a Velocity proxy and EasyAuth 3.4.x.
- **Server-side integration:** Players do not need to install an FLP client mod.
- **Fabric commands:** Added FLP list-management operations using vanilla game-master permissions.

#### Bedrock and Floodgate

- **AuthMe 6 Bedrock pre-login:** Supported AuthMe 6 configurations can complete Bedrock login earlier in the login lifecycle, reducing cases where players become stuck in authentication dialogs.
- **Signed Bedrock identity facts:** Bedrock pre-login facts are signed by the proxy and verified by the backend.
- **Spigot backend support:** Spigot proxy backends can handle Bedrock pre-login when the authentication plugin exposes the required hooks.

#### Commands and administration

- **`/flp delete <player>`:** Added a command to remove a non-premium FLP list record without deleting the player's account from the authentication plugin.
- **Final command feedback:** Proxy-relayed list changes now report the final operation result to the administrator.

#### Configuration and localization

- **Versioned language files:** Added the `_messages-version` key so FLP can add missing message keys, refresh outdated defaults, and preserve custom values.
- **Strict proxy policy option:** Added `require-declared-proxy-policy` to optionally reject legacy force messages from proxies that do not declare policy facts.

### Changed

#### Proxy and backend identity flow

- **Proxy is the policy authority:** Backends now determine the login method from the signed identity and policy facts declared by the proxy. Backend-local `autoLogin`, `autoRegister`, and Bedrock policy values no longer override proxy declarations in a proxy deployment.
- **Fail-closed identity handling:** Missing, conflicting, expired, or unreadable identity data keeps authentication locked instead of falling back to an implicit allow.
- **Legacy forwarding verification:** Legacy proxy declarations must carry a valid signature before they can be accepted for premium admission.
- **Unverified connections are handled separately:** A connection that has not completed verification is no longer treated as equivalent to a verified premium connection.
- **Login waiting feedback:** Players receive a waiting hint while proxy identity facts are being exchanged.
- **Single automatic-login authority:** When FLP manages login decisions, the authentication plugin's own proxy auto-login feature remains disabled to prevent duplicate login handling.
- **Final handshake result:** The proxy applies the final handshake result. Administrative commands now report the completed result rather than merely confirming that a request was submitted.

#### Accounts and UUID handling

- **Verification and consent are separate:** A name declaration or failed attempt alone no longer grants premium access. Same-name offline fallback is considered only when the verification service explicitly reports a verification failure.
- **Verified players can register with auto-login disabled:** When `autoLogin: false`, a verified premium player can still create a password-based account.
- **Preserve offline UUIDs:** With `premiumUuid: false`, the player keeps the name-derived offline UUID while FLP continues using the verified Mojang UUID to establish account ownership.
- **Preserve corrupt UUID records:** Corrupt premium UUID records are reported and retained instead of being discarded or silently overwritten.
- **Explicit release for passwordless accounts:** An account without a password is opened for registration only after a second confirmation. Ownership mismatches are rejected.
- **Protect confirmed bindings:** An impostor connection cannot demote an account that has already confirmed its premium binding.

#### Configuration behavior

- **Platform-specific configuration templates:** Bukkit/Folia and BungeeCord/Velocity templates now describe which side reads each setting. A backend-local value is no longer presented as effective when the proxy owns that policy.
- **Anti-bot limits always reject:** Reaching an anti-bot limit now directly rejects the connection. The `anti-bot.action` setting and the `Ignore` action have been removed.

### Fixed

- **Paper configure phase:** Standalone Paper servers retain the configure-phase login path and no longer lose premium pre-login handling.
- **Encrypted session failures:** An encrypted-session verification failure is now reported as a failure instead of being mistaken for a missing premium identity.
- **AuthMe pre-login cleanup:** FLP verifies pre-login registration cleanup before treating the account as ready.
- **Debug logging:** Diagnostic messages now use FLP's own debug setting and no longer require changes to the logging framework.

### Upgrade notes

- **Upgrade the proxy and backends together.** The new identity facts and legacy declaration signature verification require compatible versions on both sides. Do not run an old proxy with a new backend unless you understand and accept the resulting rejection behavior.
- **Remove the retired anti-bot option.** Delete `anti-bot.action` from existing configuration files.
- **Keep custom language files.** Startup migration adds missing message keys. Review startup warnings for any extra or obsolete keys.
- **Strict proxy policy is opt-in.** `require-declared-proxy-policy` defaults to `false`. Enable it only after all proxies have been upgraded and declare policy facts.
- **Review UUID behavior before deployment.** Back up player and authentication data before changing `premiumUuid` or account-binding behavior.

For installation steps and platform-specific configuration, see the [FastLoginPlus documentation](docs/en/README.md).

---

