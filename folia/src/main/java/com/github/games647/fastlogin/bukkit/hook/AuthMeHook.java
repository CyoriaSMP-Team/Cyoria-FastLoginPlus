/*
 * SPDX-License-Identifier: MIT
 *
 * The MIT License (MIT)
 *
 * Copyright (c) 2015-2026 games647, Hayston and contributors
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */
package com.github.games647.fastlogin.bukkit.hook;

import com.github.games647.fastlogin.bukkit.BukkitLoginSession;
import com.github.games647.fastlogin.bukkit.FastLoginBukkit;
import com.github.games647.fastlogin.core.hooks.AuthPlugin;
import fr.xephi.authme.api.v3.AuthMeApi;
import fr.xephi.authme.events.RestoreSessionEvent;
import fr.xephi.authme.events.LoginEvent;
import fr.xephi.authme.events.RegisterEvent;
import com.github.games647.fastlogin.core.storage.StoredProfile;
import org.bukkit.Bukkit;
import fr.xephi.authme.process.Management;
import fr.xephi.authme.process.register.executors.ApiPasswordRegisterParams;
import fr.xephi.authme.process.register.executors.RegistrationMethod;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;

import java.lang.reflect.Field;

/**
 * GitHub: <a href="https://github.com/Xephi/AuthMeReloaded/">...</a>
 * <p>
 * Project page:
 * <p>
 * <a href="https://dev.bukkit.org/bukkit-plugins/authme-reloaded/">Bukkit</a>
 * <p>
 * <a href="https://www.spigotmc.org/resources/authme-reloaded.6269/">Spigot</a>
 */
public class AuthMeHook implements AuthPlugin<Player>, Listener {

    private final FastLoginBukkit plugin;

    private final AuthMeApi authmeAPI;
    private Management authmeManagement;

    public AuthMeHook(FastLoginBukkit plugin) {
        this.plugin = plugin;
        this.authmeAPI = AuthMeApi.getInstance();

        if (plugin.getCore().getConfig().getBoolean("respectIpLimit")) {
            try {
                Field managementField = this.authmeAPI.getClass().getDeclaredField("management");
                managementField.setAccessible(true);
                this.authmeManagement = (Management) managementField.get(this.authmeAPI);
            } catch (NoSuchFieldException | IllegalAccessException exception) {
                this.authmeManagement = null;
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onSessionRestore(RestoreSessionEvent restoreSessionEvent) {
        Player player = restoreSessionEvent.getPlayer();

        BukkitLoginSession session = plugin.getSession(player.spigot().getRawAddress());
        if (session != null && session.isVerifiedPremium()) {
            if (plugin.getCore().getConfig().getBoolean("requireJavaPasswordEnrollment")
                    && session.getProfile() != null
                    && !session.getProfile().isOnlinemodePreferred()) {
                // New Java binding: force the manual password/registration step.
                // An old IP-based AuthMe session must not silently enroll it.
                restoreSessionEvent.setCancelled(true);
                return;
            }
            // AuthMe 6.0 with enablePremium: let AuthMe handle premium bypass itself
            // Only cancel RestoreSession if AuthMe is NOT handling premium
            com.github.games647.fastlogin.bukkit.compat.AuthMePremiumIntegrator integrator =
                plugin.getAuthMePremiumIntegrator();
            if (integrator != null && integrator.isAuthMePremiumEnabled()) {
                // AuthMe 6.0 premium is active — don't interfere with its flow
                return;
            }
            restoreSessionEvent.setCancelled(true);
        }
    }

    @Override
    public boolean notifyGeneratedPassword() {
        // 0.7.0/F25: AuthMe 6.0's takeover pre-creates the record itself with an empty password
        // hash (AuthMePremiumIntegrator), so the password FastLogin passes to forceRegister()
        // never reaches the database - announcing it would promise a password that cannot be
        // used. Verified on a real Spigot backend: the row ends up with password=''. AuthMe 5.x
        // registers through AuthMe's own API and does store it, so it keeps the default.
        com.github.games647.fastlogin.bukkit.compat.AuthMePremiumIntegrator integrator =
            plugin.getAuthMePremiumIntegrator();
        return integrator == null || !integrator.isAuthMePremiumEnabled();
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPasswordLogin(LoginEvent event) {
        promoteJavaEnrollment(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPasswordRegister(RegisterEvent event) {
        promoteJavaEnrollment(event.getPlayer());
    }

    private void promoteJavaEnrollment(Player player) {
        if (!plugin.getCore().getConfig().getBoolean("requireJavaPasswordEnrollment")) return;
        BukkitLoginSession session = plugin.getSession(player.spigot().getRawAddress());
        if (session == null || !session.isVerifiedPremium()
                || session.getUuid() == null
                || !session.getUsername().equalsIgnoreCase(player.getName())) return;
        // Manual AuthMe event is the only path that binds a new Java identity.
        // Load fresh by username so a previous async save cannot overwrite it.
        final String name = player.getName();
        final java.util.UUID verifiedUuid = session.getUuid();
        Bukkit.getAsyncScheduler().runDelayed(plugin, task -> {
            com.github.games647.fastlogin.bukkit.compat.AuthMePremiumIntegrator integrator =
                plugin.getAuthMePremiumIntegrator();
            if (integrator == null || !integrator.hasPasswordEnrollment(name)) return;
            plugin.getCore().getStorage().withNameLock(name, () -> {
                StoredProfile profile = plugin.getCore().getStorage().loadProfile(name);
                if (profile == null || (profile.getId() != null && !verifiedUuid.equals(profile.getId()))) return;
                profile.setId(verifiedUuid);
                profile.setOnlinemodePreferred(true);
                plugin.getCore().getStorage().save(profile);
            });
        }, 400L, java.util.concurrent.TimeUnit.MILLISECONDS);
    }

    @Override
    public boolean forceLogin(Player player) {
        if (authmeAPI.isAuthenticated(player)) {
            if (plugin.getCore().isDebug()) {
                plugin.getLog().warn(ALREADY_AUTHENTICATED, player);
            }
            return false;
        }

        //skips registration and login
        authmeAPI.forceLogin(player);
        return true;
    }

    @Override
    public boolean isRegistered(String playerName) {
        return authmeAPI.isRegistered(playerName);
    }

    @Override
    //this automatically login the player too
    public boolean forceRegister(Player player, String password) {
        //if we have the management - we can trigger register with IP limit checks
        if (authmeManagement != null) {
            authmeManagement.performRegister(RegistrationMethod.PASSWORD_REGISTRATION,
                    ApiPasswordRegisterParams.of(player, password, true));
        } else {
            authmeAPI.forceRegister(player, password);
        }

        return true;
    }
}
