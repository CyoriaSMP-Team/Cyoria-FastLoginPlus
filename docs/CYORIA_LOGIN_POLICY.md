# CyoriaSMP manual enrollment and premium auto-login

The CyoriaSMP identity policy requires every Java or Bedrock player to register
an account explicitly on first join. Passing a Mojang session challenge proves
ownership of a Java account, **not** ownership of an existing Cyoria identity.

## Safe first-registration defaults

```yaml
autoRegister: false
autoLogin: false
auto-register-unknown: false
premiumUuid: true
```

The first-registration requirement is implemented in FastLoginPlus; when
CyoriaAuth is ready to approve account linking, enabling `autoLogin: true`
allows **verified Premium sessions with an already registered AuthMe identity**
to auto-login, while still requiring `/register` for missing AuthMe records.
Cracked clients continue to use `/login`.

Do **not** enable `autoLogin` as an arbitrary way to create or link an identity.
The rule does not yet consult a fully integrated CyoriaAuth account-linking API;
AuthMe registration is the interim gate. Enforce the separate verified-link gate
before advertising full CyoriaAuth single sign-on. Verified Minecraft UUID alone
must never merge or overwrite old offline UUID playerdata.

When `autoRegister: false`, the FLP core never calls `forceRegister` and AuthMe
Premium integration must not create or bypass unregistered users through the
premium pre-login/confirmation hooks. When `autoRegister: true` is explicitly
chosen, that is an opt-in to automatic registration and can operate independently
of auto-login.

## Verification

1. Unregistered Java Premium -> AuthMe `/register`, never `forceRegister`.
2. Registered, linked/opted-in Java Premium -> Mojang verified -> auto-login only
   if `autoLogin: true` (identity-link gate is pending implementation).
3. Java Cracked -> normal password login, no Mojang-based auto-login.
4. Bedrock -> independent Floodgate settings; never assume Java Premium status.
5. Invalid Mojang session while the session service is reachable -> no fallback; a Mojang name-lookup HTTP 429 or confirmed session-service outage may use AuthMe password login only for an existing password-backed account.
6. During the outage fallback, unknown accounts and AuthMe premium-owned accounts are rejected; no auto-register or passwordless login is allowed.
7. Never merge playerdata/inventory from two UUIDs without account-owner proof.

The upstream `LoginMethodDecider` decision table (with `LoginMethodDeciderTest`
and proxy `PolicyIntentTest` coverage) now enforces the same rule from the
other direction: with `autoRegisterUnknown: false` a verified premium identity
without a local AuthMe account resolves to `GUIDE_REGISTER`, never to
`AUTO_REGISTER_LOGIN`. Account state is read from the auth plugin itself
(`AuthPlugin.inspectAccount`), so a session flag alone can never mint access.
Live end-to-end tests and full Gradle integration remain required before release.


## Mojang authentication outage fallback (Velocity)

The proxy option `auth-outage-password-fallback: true` enables a bounded,
password-only recovery path when a Mojang name-to-UUID lookup is explicitly
rate-limited with HTTP 429, or when a failed premium handshake is followed by
a failed reachability probe to Mojang's session service. For the handshake path,
a reachable session service means the failure is treated as an invalid/failed
session and the outage path stays closed.

The proxy sends a dedicated `AUTH_OUTAGE` declaration to the Bukkit backend.
The backend reads the account state from AuthMe and allows only
`PASSWORD_ACCOUNT`; the player must complete AuthMe's normal `/login` flow.
Unknown accounts, premium-owned accounts without a usable password, and account
database read failures do not receive passwordless access. This is not a
registration fallback and never changes the player's UUID or player data.

Both Velocity FastLoginPlus and the Bukkit/Folia backend must run a build that
understands the `AUTH_OUTAGE` declaration. Older backends do not understand
that wire type and must be upgraded before enabling this option.
