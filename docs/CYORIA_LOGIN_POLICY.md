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
5. Bad Mojang challenge -> no auto-login, no offline fallback that steals identity.
6. Never merge playerdata/inventory from two UUIDs without account-owner proof.

The focused `PremiumAuthPolicyTest` covers registration and login flag separation.
Live end-to-end tests and full Gradle integration remain required before release.
