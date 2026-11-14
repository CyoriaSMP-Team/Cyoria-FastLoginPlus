/*
 * SPDX-License-Identifier: MIT
 *
 * Copyright (c) 2026 games647 and contributors
 * Copyright (c) 2026 Hayston1001
 */
package com.github.games647.fastlogin.bukkit.command;

import com.github.games647.fastlogin.bukkit.FastLoginBukkit;
import com.github.games647.fastlogin.bukkit.compat.AuthMePremiumIntegrator;
import com.github.games647.fastlogin.bukkit.event.BukkitFastLoginPremiumToggleEvent;
import com.github.games647.fastlogin.core.storage.StoredProfile;
import com.github.games647.fastlogin.core.storage.SQLStorage;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import static com.github.games647.fastlogin.core.shared.event.FastLoginPremiumToggleEvent.PremiumToggleReason;

public class CrackedCommand extends ToggleCommand {

    private static final String PERM_PREFIX = "fastloginplus.bukkit.command.";

    public CrackedCommand(FastLoginBukkit plugin) {
        super(plugin);
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label,
                             String[] args) {
        if (args.length == 0) {
            onCrackedSelf(sender);
        } else {
            onCrackedOther(sender, command, args);
        }

        return true;
    }

    private void onCrackedSelf(CommandSender sender) {
        if (isConsole(sender)) {
            return;
        }

        if (!sender.hasPermission(PERM_PREFIX + "cracked")) {
            plugin.getCore().sendLocaleMessage("no-permission", sender);
            return;
        }

        Player player = (Player) sender;
        String playerName = sender.getName();

        // Forward to proxy if enabled; proxy handles profile persistence,
        // messages, kick — the backend has no DB in proxy mode.
        if (forwardCrackedCommand(sender, playerName)) {
            return;
        }

        // Local path (no proxy): load profile from local DB
        // database calls must not run on the main thread
        plugin.getScheduler().runAsync(() -> {
            // serialize concurrent load-modify-save windows for the
            // same player (two fast commands would otherwise double-save)
            plugin.getCore().getStorage().withNameLock(playerName, () -> {
                StoredProfile profile = plugin.getCore().getStorage().loadProfile(playerName);
                if (profile == null) {
                    // null only on SQL exception (lock timeout, DB down) — the database failed
                    plugin.getScheduler().getSyncExecutor().execute(() ->
                            plugin.getCore().sendLocaleMessage("database-error", sender));
                    return;
                }
                //already fully cracked: no consent, no premium UUID and no
                //same-name cracked observation left to clear
                if (!profile.isOnlinemodePreferred() && profile.getId() == null
                        && !profile.isSameNameCracked()) {
                    plugin.getScheduler().getSyncExecutor().execute(() ->
                            plugin.getCore().sendLocaleMessage("not-premium", sender));
                    return;
                }

                if (!persistCrackedProfile(profile, plugin.getCore().getStorage())) {
                    plugin.getScheduler().getSyncExecutor().execute(() ->
                            plugin.getCore().sendLocaleMessage("database-error", sender));
                    return;
                }
                clearAuthMePremium(playerName);

                // Local path (no proxy): event + kick
                plugin.getServer().getPluginManager().callEvent(
                        new BukkitFastLoginPremiumToggleEvent(sender, profile, PremiumToggleReason.COMMAND_OTHER)
                );

                plugin.getScheduler().getSyncExecutor().execute(() -> {
                    if (plugin.getCore().getConfig().getBoolean("kick-toggle")) {
                        player.kickPlayer(plugin.getCore().getMessage("remove-premium"));
                    } else {
                        // this is the *cracked* self path — the message
                        // was misleadingly the add-premium key
                        // single feedback message — the pre-save duplicate
                        // was removed
                        plugin.getCore().sendLocaleMessage("remove-premium", sender);
                    }
                });
            });
        });
    }

    private void onCrackedOther(CommandSender sender, Command command, String[] args) {
        if (!hasOtherPermission(sender, PERM_PREFIX + "cracked")) {
            return;
        }

        String playerName = args[0];

        // Forward to proxy if enabled; proxy handles profile persistence,
        // messages, kick — the backend has no DB in proxy mode.
        if (forwardCrackedCommand(sender, playerName)) {
            return;
        }

        // Local path (no proxy): load profile from local DB
        // database calls must not run on the main thread
        plugin.getScheduler().runAsync(() -> {
            // see the self path above
            plugin.getCore().getStorage().withNameLock(playerName, () -> {
                StoredProfile profile = plugin.getCore().getStorage().loadProfile(playerName);
                if (profile == null) {
                    // null only on SQL exception (lock timeout, DB down) — the database failed
                    plugin.getScheduler().getSyncExecutor().execute(() ->
                            plugin.getCore().sendLocaleMessage("database-error", sender));
                    return;
                }

                //already fully cracked: no consent, no premium UUID and no
                //same-name cracked observation left to clear
                if (profile.isExistingPlayer() && !profile.isOnlinemodePreferred()
                        && profile.getId() == null && !profile.isSameNameCracked()) {
                    plugin.getScheduler().getSyncExecutor().execute(() ->
                            plugin.getCore().sendLocaleMessage("not-premium-other", sender));
                    return;
                }

                if (!persistCrackedProfile(profile, plugin.getCore().getStorage())) {
                    plugin.getScheduler().getSyncExecutor().execute(() ->
                            plugin.getCore().sendLocaleMessage("database-error", sender));
                    return;
                }
                clearAuthMePremium(playerName);

                plugin.getScheduler().getSyncExecutor().execute(() ->
                        plugin.getCore().sendLocaleMessage("remove-premium-other", sender));

                // Local path (no proxy): event
                plugin.getServer().getPluginManager().callEvent(
                        new BukkitFastLoginPremiumToggleEvent(sender, profile, PremiumToggleReason.COMMAND_OTHER));
            });
        });
    }

    // Persist-first ordering: the FLP profile row must be durably cracked
    // before the destructive AuthMe premium wipe runs. Reversing the order
    // strands players when the database write fails after AuthMe was cleared.
    static boolean persistCrackedProfile(StoredProfile profile, SQLStorage storage) {
        boolean wasOnlineModePreferred = profile.isOnlinemodePreferred();
        java.util.UUID wasId = profile.getId();
        boolean wasSameNameCracked = profile.isSameNameCracked();
        profile.setOnlinemodePreferred(false);
        profile.setId(null);
        profile.setSameNameCracked(false);
        if (storage.saveQuietly(profile)) {
            return true;
        }
        // Roll the in-memory row back: a failed write must never discard the
        // verified identity the player already holds.
        profile.setOnlinemodePreferred(wasOnlineModePreferred);
        profile.setId(wasId);
        profile.setSameNameCracked(wasSameNameCracked);
        return false;
    }

    private void clearAuthMePremium(String playerName) {
        // AuthMe lives on the backend; a proxy cannot reach it, so this runs
        // on the backend in both forwarded and local paths.
        AuthMePremiumIntegrator integrator = plugin.getAuthMePremiumIntegrator();
        if (integrator != null) {
            integrator.clearPlayerPremium(playerName);
        } else {
            plugin.getLog().warn("CrackedCommand: authMePremiumIntegrator is null, "
                + "skipping AuthMe cleanup for {}", playerName);
        }
    }

    private boolean forwardCrackedCommand(CommandSender sender, String target) {
        return forwardBungeeCommand(sender, target, false);
    }
}
