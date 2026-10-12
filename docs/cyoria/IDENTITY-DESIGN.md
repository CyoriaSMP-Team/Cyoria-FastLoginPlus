# Cyoria Identity — Premium Name Claim (work in progress)

**Status: Phase 1 core engine only, no gameplay integration, NOT for production.**

Cyoria Identity is an optional, fail-closed enhancement to FastLoginPlus. The upstream FastLoginPlus remains fully compatible while this feature is developed in an isolated branch. Do not replace the CyoriaSMP production plugin with this experimental build until Phase 2+ integration, backups, auth and end-to-end tests are complete.

## User-facing rules

1. Local player may register a username first, with their AuthMe password and a stable Local identity UUID. Merely finding a matching Mojang **name** does not grant anyone control of Local's identity.
2. Premium player may claim that username **only after a real Mojang `hasJoined` session verification succeeds and the claimant establishes a separate, usable AuthMe password for Local access**. The public name-to-UUID API is not sufficient proof.
3. If a Local account already owns the username, a pending Premium claim reserves the name but **must not overwrite Local's UUID, password, inventory, rank, currency, statistics or vault**. Name ownership is not the same as data ownership.
4. Previous Local player authenticates using their existing password and chooses a free valid nickname, usually `Local_<name>` (or `L_<truncated>` if longer than 16 chars). They are prompted to reconnect with their chosen launcher nickname. Data migrates to the new login name and **retains its original stable identity UUID**; where the backend generates UUID from login name, it MUST be explicitly redirected before inventory loads. An offline UUID is not automatically stable under a nickname change.
5. Only after the external migration executor has snapshotted, moved and checked **all** affected data stores can the pending Premium claim become ACTIVE. Incomplete/failed migration blocks activation and allows restore from backups.
6. Premium owner enters with Mojang authentication (Paid) or may enter using an Offline launcher with the **Premium owner's new password**, through a separate pre-join identity authentication layer that chooses the Premium UUID **before** loading player data. Sending a Premium UUID from a Local launcher is NEVER proof of ownership.
7. Unknown users cannot reuse names reserved by Premium. Local former password remains on the renamed Local identity; Premium password is not copied from the prior Local account.

## States and implementation boundaries

| State | Definition | Safe behavior |
|---|---|---|
| UNCLAIMED | No verified Premium claim | Local may reserve name; must authenticate through AuthMe |
| WAITING_FOR_LOCAL_RENAME | Verified claim with earlier Local identity | No UUID/data takeover; rename-only flow after Local password proof |
| ACTIVE | Verified owner and data migration complete, or previously unoccupied name | Premium requires Mojang proof, or offline-route password proof before Premium UUID assignment |

All names are case-insensitive, ASCII `[A-Za-z0-9_]{3,16}`. DB uniqueness prevents two accounts from owning the same canonical Local nickname. Claim ownership is keyed to immutable Mojang UUID and cannot be overridden by another UUID.

### Implemented in Phase 1

- `IdentityNames`: canonical username validation and `Local_` suggestion respecting Minecraft's 16-character maximum.
- `CyoriaIdentityRegistry`: standalone SQLite (via JDBC) tables `ci_locals`, `ci_claims`, `ci_rename_plans`, with serial transactions, collision protection, verified/pw-ready preconditions, claim reservation and rename plan staging.
- `IdentityMigrationPlan`: record of required stores that must be backed up and verified before a claim can be activated. Missing receipt elements abort.
- JUnit tests: claims denied without proof/password, case insensitive usernames, UUID separation, collision checks, migration rollback on failure, incomplete receipts, idempotent claims.

**Crucial limitations:** `stageVerifiedPremiumClaim(..., mojangSessionVerified, passwordEstablished)` is an **internal staging interface** whose two flags must ultimately come only from verified server-side session and AuthMe operations. Do not call it from Bukkit commands or raw player input. `completeLocalRename` consumes a migration receipt asserted by its caller; Phase 1 does not create or verify the underlying file/DB backups, nor migrate records. The new registry has intentionally not been wired into Live Login, and there is no new user command. None of the staged claim tables touch the existing FastLogin or AuthMe databases.

### Required remaining phases — acceptance gates before deployment

**Phase 2: real authentication adapters.** From `VerifyResponseTask`'s `hasJoined` response, bind verified Mojang name+UUID to a one-shot internal claim capability and verify a dedicated AuthMe password-hash write. Never use the pre-existing AuthMe 6 premium placeholder with empty password as successful password establishment. Add local pre-login password verification and a temporary isolated rename lobby/dialog; deny gameplay until authenticated. Ensure proxy and Floodgate variants fail closed.

**Phase 3: name + UUID and data migration.** Back up consistent snapshots and migrate AuthMe, FastLoginPlus, LuckPerms H2, native world playerdata/stats/advancements, PlayerVaults, Essentials, economy and all installed plugin data. Some data stores are UUID-keyed; some use name/UUID together. Preserve Local UUID by injecting the mapped UUID before data load, not merely renaming files. Enforce only-one-session on both identities; make migrations idempotent, resumable and reversible with a rollback journal. Detect concurrent join/session changes and abort safely. Until this exists, no Local rename can be marked complete on a production server.

**Phase 4: real client tests.** Test Premium login, original Local registration, Local name collision, successful claim, force-rename, same-name Offline login with new owner password, wrong password, offline launcher spoofing Premium UUID, Mojang outages, failed migration, restart/resume, duplicate simultaneous logins, Bedrock, Proxy, IP variation. No security-sensitive shortcuts (e.g. `secondAttemptCracked`, disable session checks) can be used.

### Real server examples (read-only observation at prototype start)

- `NamKrub` existing FastLoginPlus Premium UUID `c83c3db1-3ffe-4105-a768-b1315664d332`; older Local UUID `f57ea40d-ba3a-34a4-839b-a0659169cd58` has separate playerdata. **Do not merge or change these automatically.**
- `Namnarak` has a registered Mojang name but no FastLoginPlus local/premium entry; a name existence query is not itself an authenticated claim.

**Operational rule:** maintain current production `ProtocolLib2PacketEvents-1.0.11`, FastLoginPlus 0.7.0 and AuthMe until after a separate staging environment passes all above tests. SSH/Tailscale are unrelated to this development and must be left untouched.
