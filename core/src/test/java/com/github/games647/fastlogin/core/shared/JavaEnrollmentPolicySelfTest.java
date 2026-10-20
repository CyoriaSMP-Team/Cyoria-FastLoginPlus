package com.github.games647.fastlogin.core.shared;

/** Dependency-free smoke test; run with java -ea. */
public final class JavaEnrollmentPolicySelfTest {
    public static void main(String[] args) {
        check(true,  false, true,  true,  true); // returning Mojang-verified + password
        check(false, false, true,  true,  false); // first visit cannot hijack existing AuthMe account
        check(true,  true,  false, false, false); // first-time must register themselves
        check(true,  false, true,  false, false); // passwordless premium placeholder
        check(true,  false, false, true,  false); // registration missing
        check(false, false, false, true,  false); // unbound identity
        System.out.println("PASS 6 Java enrollment policy cases");
    }

    private static void check(boolean bound, boolean needsRegistration, boolean registered,
                              boolean hash, boolean expected) {
        boolean actual = JavaEnrollmentPolicy.canAutoLogin(bound, needsRegistration, registered, hash);
        if (actual != expected) throw new AssertionError("Wrong authorization decision");
    }
}
