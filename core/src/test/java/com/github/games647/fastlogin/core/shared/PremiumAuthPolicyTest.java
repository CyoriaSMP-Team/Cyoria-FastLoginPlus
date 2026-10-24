package com.github.games647.fastlogin.core.shared;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static com.github.games647.fastlogin.core.shared.PremiumAuthPolicy.Action.*;

class PremiumAuthPolicyTest {
    @Test void explicitFirstRegistrationAlwaysWinsOverAutoLogin() {
        for (boolean unknown : new boolean[]{true, false}) {
            for (boolean needsRegistration : new boolean[]{true, false}) {
                assertEquals(MANUAL, PremiumAuthPolicy.choose(true, false, unknown,
                        needsRegistration, false));
            }
        }
    }
    @Test void registeredVerifiedPremiumMayAutoLoginWithoutAutoRegister() {
        assertEquals(LOGIN, PremiumAuthPolicy.choose(true, false, true, false, true));
        assertEquals(LOGIN, PremiumAuthPolicy.choose(true, false, false, true, true));
    }
    @Test void autoLoginDisabledForcesManualEvenWhenRegistered() {
        assertEquals(MANUAL, PremiumAuthPolicy.choose(false, true, true, false, true));
        assertEquals(REGISTER, PremiumAuthPolicy.choose(false, true, true, true, false));
    }
    @Test void registrationIsAllowedOnlyWhenExplicitlyEnabled() {
        assertEquals(REGISTER, PremiumAuthPolicy.choose(true, true, false, true, false));
        assertEquals(REGISTER, PremiumAuthPolicy.choose(true, true, true, false, false));
        assertEquals(MANUAL, PremiumAuthPolicy.choose(true, true, false, false, false));
    }
}
