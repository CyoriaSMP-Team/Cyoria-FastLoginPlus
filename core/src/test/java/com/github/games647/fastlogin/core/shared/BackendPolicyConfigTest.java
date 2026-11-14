/*
 * SPDX-License-Identifier: MIT
 *
 * Copyright (c) 2026 Hayston1001
 */
package com.github.games647.fastlogin.core.shared;

import org.junit.jupiter.api.Test;
import org.yaml.snakeyaml.Yaml;

import java.io.InputStream;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class BackendPolicyConfigTest {
    @Test
    void bothTemplatesParseWithUnchangedPolicyDefaults() throws Exception {
        for (String template : new String[]{"/config.yml", "/config-proxy.yml"}) {
            try (InputStream input = getClass().getResourceAsStream(template)) {
                Map<String, Object> settings = new Yaml().load(input);
                assertEquals("en", settings.get("language"));
                // Cyoria safe defaults: manual first registration everywhere
                // (autoRegister/auto-register-unknown off); auto-login stays on
                // for returning registered players, including Floodgate ones.
                for (String key : new String[]{"autoLogin", "premiumUuid", "nameChangeCheck",
                        "kick-toggle", "premium-warning", "autoLoginFloodgate"}) {
                    assertEquals(Boolean.TRUE, settings.get(key), template + ": " + key);
                }
                for (String key : new String[]{"autoRegister", "secondAttemptCracked", "offline-whitelist",
                        "auto-register-unknown", "autoRegisterFloodgate", "allowFloodgateNameConflict"}) {
                    assertEquals(Boolean.FALSE, settings.get(key), template + ": " + key);
                }
                Map<?, ?> antiBot = (Map<?, ?>) settings.get("anti-bot");
                assertEquals(Boolean.TRUE, antiBot.get("enabled"));
                assertFalse(antiBot.containsKey("action"));
                assertEquals(9, antiBot.size());
                assertEquals(600, antiBot.get("connections"));
                assertEquals(10, antiBot.get("expire"));
                assertEquals(20, antiBot.get("per-ip-connections"));
                assertEquals(5, antiBot.get("per-ip-expire"));
                assertEquals(10, antiBot.get("burst-limit"));
                assertEquals(10, antiBot.get("burst-window"));
                assertEquals(5, antiBot.get("ban-duration"));
                assertTrue(antiBot.containsKey("trusted-ips"));
                assertNull(antiBot.get("trusted-ips"));
            }
        }
    }

    @Test
    void removedStrictPolicyGateIsNoLongerInEitherTemplate() throws Exception {
        try (InputStream backend = getClass().getResourceAsStream("/config.yml");
             InputStream proxy = getClass().getResourceAsStream("/config-proxy.yml")) {
            Map<String, Object> backendSettings = new Yaml().load(backend);
            Map<String, Object> proxySettings = new Yaml().load(proxy);
            // undeclared policy is always rejected now, so the switch is gone
            assertFalse(backendSettings.containsKey("require-declared-proxy-policy"));
            assertFalse(proxySettings.containsKey("require-declared-proxy-policy"));
        }
    }
}
