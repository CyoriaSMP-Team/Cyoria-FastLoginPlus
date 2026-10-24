package com.github.games647.fastlogin.core.shared;

/** Separates optional premium auto-login from explicit first-time registration. */
public final class PremiumAuthPolicy {
    private PremiumAuthPolicy() { }

    public enum Action { MANUAL, REGISTER, LOGIN }

    /** Called only for an already Mojang-verified premium session. */
    public static Action choose(boolean autoLogin, boolean autoRegister,
                                boolean autoRegisterUnknown, boolean needsRegistration,
                                boolean registeredInAuthPlugin) {
        if (!registeredInAuthPlugin) {
            // A missing account never qualifies for login. Even if a caller says
            // registration is needed, auto-register-unknown alone cannot override
            // the separate autoRegister consent flag.
            return autoRegister && (needsRegistration || autoRegisterUnknown)
                    ? Action.REGISTER : Action.MANUAL;
        }
        return autoLogin ? Action.LOGIN : Action.MANUAL;
    }
}
