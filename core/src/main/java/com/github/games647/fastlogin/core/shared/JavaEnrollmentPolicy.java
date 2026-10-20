package com.github.games647.fastlogin.core.shared;

/** Fail-closed authorization gate for Java premium login after password enrollment. */
public final class JavaEnrollmentPolicy {
    private JavaEnrollmentPolicy() { }

    /** Mojang session verification occurs separately before this gate is evaluated. */
    public static boolean canAutoLogin(boolean previouslyBound, boolean needsRegistration,
                                       boolean authRegistered, boolean hasPasswordHash) {
        return previouslyBound && !needsRegistration && authRegistered && hasPasswordHash;
    }
}
