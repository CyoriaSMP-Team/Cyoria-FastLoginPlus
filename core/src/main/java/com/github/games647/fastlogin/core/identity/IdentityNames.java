/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 CyoriaSMP-Team and contributors
 */
package com.github.games647.fastlogin.core.identity;

import java.util.Locale;
import java.util.Objects;
import java.util.regex.Pattern;

/** Canonical, case-insensitive Java Edition login names (max 16 characters). */
public final class IdentityNames {

    private static final Pattern VALID = Pattern.compile("[A-Za-z0-9_]{3,16}");

    private IdentityNames() {
    }

    public static String validate(String name) {
        Objects.requireNonNull(name, "name");
        if (!VALID.matcher(name).matches()) {
            throw new IllegalArgumentException("Invalid Minecraft Java username");
        }
        return name;
    }

    public static String key(String name) {
        return validate(name).toLowerCase(Locale.ROOT);
    }

    /**
     * Generates a suggestion; caller must still check database availability.
     * @param previousName original Local username
     * @return a valid possible Local name no longer than sixteen characters
     */
    public static String suggestedLocalName(String previousName) {
        validate(previousName);
        if (previousName.length() <= 10) {
            return "Local_" + previousName;
        }
        // "L_" + 14 chars fits the 16-character protocol limit.
        return "L_" + previousName.substring(0, 14);
    }
}
