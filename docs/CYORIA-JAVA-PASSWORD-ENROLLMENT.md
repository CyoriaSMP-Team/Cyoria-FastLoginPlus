# CyoriaSMP verified Java enrollment / Floodgate auto-approval

Branch baseline: FastLoginPlus Folia `0.7.0-2c5808e`, the build installed on the CyoriaSMP Folia server. This changes the login flow, not the Minecraft player UUID or password hash algorithm. It is an opt-in fork behavior.

## Policies

| Client and state | Action |
| --- | --- |
| Java, genuine Mojang account, no AuthMe record | Verify the Mojang session first; the player registers a **chosen password** with AuthMe on first visit; no generated or empty password, no automatic grant before registration. |
| Java, genuine Mojang account, preexisting AuthMe record | Verify the Mojang session, then require AuthMe login once unless the FastLogin profile is already bound and the account has a real password hash. Existing passwords are preserved. |
| Java, genuine Mojang account, verified + bound + AuthMe hash | Auto-login on subsequent visits. |
| Java, Mojang verification fails | Do not grant premium; use the existing cracked fallback pathway requiring the normal AuthMe password. A reconnect may be needed after a rejected Mojang handshake. |
| Bedrock player | Only a player actually verified by Floodgate may auto-register/auto-login. Keep Java-name conflict prevention enabled. |

`requireJavaPasswordEnrollment=true` gates the manual Java flow. Password enrollment is promoted only on AuthMe LoginEvent/RegisterEvent associated with a Mojang-verified session; the promotion records the Mojang UUID and the FastLogin premium preference. The premium preference by itself never satisfies enrollment. The old automatic Java forceRegister path (generated random password) is bypassed; the AuthMe premium compatibility bridge must not pre-create an empty password record for new Java accounts. Clearing a stale premium flag preserves any stored password hash.

## Site-specific configuration

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

Notes: `autoRegister` here **enables premium session discovery**; the Cyoria policy still forbids automatic Java AuthMe registration. `secondAttemptCracked` allows the ordinary password path when a Mojang handshake fails: it is not a premium bypass. Floodgate must be installed and must verify the connection; a Bedrock-like username alone is insufficient.

## Build and deployment

The production candidate is a Java 21 class overlay of the deployed Folia JAR. Six modified compiled classes plus the new JavaEnrollmentPolicy are overlaid, retaining the original dependency bundle. The six Java policy cases pass. The original Maven dependency tree could not be resolved in this environment; full Maven integration tests and a live client acceptance test **have not been completed**. Staging does not automatically activate the candidate.

On Home Server:

- Stage: `/srv/cytech/ops/fastlogin-enrollment-20261021/staged/`
- Apply **only when Pano's Minecraft process is stopped**: `/srv/cytech/ops/fastlogin-enrollment-20261021/apply-when-stopped.sh`
- Restore **only when stopped**: `/srv/cytech/ops/fastlogin-enrollment-20261021/rollback-when-stopped.sh`
- Apply backs up the plugin JAR, config and SQLite snapshots before atomic file replacement. Never overwrite live SQLite databases during a normal rollback; player registrations during the new version could otherwise be lost.

Verify after Pano start: Java first-time password registration with web login, Java returning auto-login, Java Mojang rejection and manual password fallback, Bedrock fresh Floodgate auto-approval, Bedrock linked, and collision prevention. Do not declare the feature released until live client tests pass.
