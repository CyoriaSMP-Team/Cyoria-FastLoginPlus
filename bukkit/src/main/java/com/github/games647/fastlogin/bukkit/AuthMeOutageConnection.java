/*
 * SPDX-License-Identifier: MIT
 *
 * Copyright (c) 2026 games647 and contributors
 * Copyright (c) 2026 Hayston1001
 */
package com.github.games647.fastlogin.bukkit;

import org.bukkit.entity.Player;

/**
 * Identifies a proxy-validated Mojang outage declaration before AuthMe restores an IP session.
 */
public final class AuthMeOutageConnection {

    private AuthMeOutageConnection() {
    }

    /**
     * Whether this connection must be kept behind AuthMe's password prompt.
     *
     * @param plugin FastLoginPlus backend plugin
     * @param player current backend player
     * @return true only for a fresh, validated outage declaration
     */
    public static boolean isAuthenticationOutage(FastLoginBukkit plugin, Player player) {
        BukkitLoginSession session = plugin.getSession(player.spigot().getRawAddress());
        if (session != null && session.getIdentityDecision()
                == com.github.games647.fastlogin.protocol.IdentityClaim.Decision.AUTH_OUTAGE_PASSWORD) {
            return true;
        }

        try {
            SpigotProfileReader.Snapshot snapshot = SpigotProfileReader.read(player);
            com.github.games647.fastlogin.protocol.IdentityClaim claim = plugin.validateIdentityDeclaration(
                    snapshot.claims, snapshot.signatures, player.getName(), player.getUniqueId(), false);
            return claim != null && claim.getDecision()
                    == com.github.games647.fastlogin.protocol.IdentityClaim.Decision.AUTH_OUTAGE_PASSWORD;
        } catch (Exception untrustedProfile) {
            return false;
        }
    }
}
