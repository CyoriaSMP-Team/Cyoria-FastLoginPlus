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
package com.github.games647.fastlogin.bukkit.listener.protocolsupport;

import com.github.games647.craftapi.UUIDAdapter;
import com.github.games647.fastlogin.bukkit.BukkitLoginSession;
import com.github.games647.fastlogin.bukkit.FastLoginBukkit;
import com.github.games647.fastlogin.bukkit.event.BukkitFastLoginPreLoginEvent;
import com.github.games647.fastlogin.core.antibot.AntiBotService;
import com.github.games647.fastlogin.core.antibot.AntiBotService.Action;

import com.github.games647.fastlogin.bukkit.event.BukkitFastLoginAntiBotEvent;
import com.github.games647.fastlogin.core.shared.JoinManagement;
import com.github.games647.fastlogin.core.shared.event.FastLoginPreLoginEvent;
import com.github.games647.fastlogin.core.storage.StoredProfile;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.plugin.PluginManager;

import java.lang.reflect.Method;
import java.net.InetSocketAddress;
import java.util.Optional;
import java.util.UUID;

/**
 * ProtocolSupport hook without a compile-time ProtocolSupport dependency.
 * Event classes are resolved reflectively at registration so this fork builds
 * without the unmaintained JitPack artifact. Missing or drifted API skips
 * the hook with a warning instead of breaking startup.
 */
public class ProtocolSupportListener extends JoinManagement<Player, CommandSender, ProtocolLoginSource>
        implements Listener {

    private final FastLoginBukkit plugin;
    private final AntiBotService antiBotService;

    public ProtocolSupportListener(FastLoginBukkit plugin, AntiBotService antiBotService) {
        super(plugin.getCore(), plugin.getCore().getAuthPluginHook(), plugin.getBedrockService());

        this.plugin = plugin;
        this.antiBotService = antiBotService;
    }

    public static void register(FastLoginBukkit plugin, AntiBotService antiBotService) {
        Class<?> loginStartClass;
        Class<?> connectionCloseClass;
        Class<?> profileCompleteClass;
        try {
            loginStartClass = Class.forName("protocolsupport.api.events.PlayerLoginStartEvent");
            connectionCloseClass = Class.forName("protocolsupport.api.events.ConnectionCloseEvent");
            profileCompleteClass = Class.forName("protocolsupport.api.events.PlayerProfileCompleteEvent");
            // Fail fast on API drift while the cause is still obvious.
            loginStartClass.getMethod("isLoginDenied");
            loginStartClass.getMethod("getConnection");
            loginStartClass.getMethod("denyLogin", String.class);
            loginStartClass.getMethod("setOnlineMode", boolean.class);
            connectionCloseClass.getMethod("getConnection");
            profileCompleteClass.getMethod("getConnection");
            profileCompleteClass.getMethod("getForcedName");
            profileCompleteClass.getMethod("setForcedUUID", UUID.class);
        } catch (ClassNotFoundException | NoSuchMethodException ex) {
            plugin.getLog().warn("ProtocolSupport hook skipped (API not present): " + ex.getMessage());
            return;
        }
        ProtocolSupportListener listener = new ProtocolSupportListener(plugin, antiBotService);
        PluginManager pluginManager = plugin.getServer().getPluginManager();
        pluginManager.registerEvent(asEventClass(loginStartClass), listener, EventPriority.NORMAL,
                (l, event) -> listener.onLoginStart(event), plugin);
        pluginManager.registerEvent(asEventClass(connectionCloseClass), listener, EventPriority.NORMAL,
                (l, event) -> listener.onConnectionClosed(event), plugin);
        pluginManager.registerEvent(asEventClass(profileCompleteClass), listener, EventPriority.NORMAL,
                (l, event) -> listener.onPropertiesResolve(event), plugin);
    }

    @SuppressWarnings("unchecked")
    private static Class<? extends Event> asEventClass(Class<?> eventClass) {
        return (Class<? extends Event>) eventClass;
    }

    private static Object invoke(Object target, String method, Object... args) {
        try {
            Class<?>[] params = new Class<?>[args.length];
            for (int i = 0; i < args.length; i++) {
                params[i] = args[i].getClass();
            }
            // boolean.class never appears here (all reflective args are objects);
            // primitives are resolved by exact name lookup below.
            Method found = findMethod(target.getClass(), method, args);
            return found.invoke(target, args);
        } catch (ReflectiveOperationException ex) {
            throw new IllegalStateException("ProtocolSupport call failed: " + method, ex);
        }
    }

    private static Method findMethod(Class<?> type, String name, Object[] args) throws NoSuchMethodException {
        outer:
        for (Method candidate : type.getMethods()) {
            if (!candidate.getName().equals(name) || candidate.getParameterCount() != args.length) {
                continue;
            }
            Class<?>[] params = candidate.getParameterTypes();
            for (int i = 0; i < args.length; i++) {
                if (args[i] == null || !params[i].isInstance(args[i])) {
                    continue outer;
                }
            }
            return candidate;
        }
        throw new NoSuchMethodException(type.getName() + '#' + name);
    }

    private static Object connectionOf(Object event) {
        return invoke(event, "getConnection");
    }

    private static String profileName(Object connection) {
        return (String) invoke(invoke(connection, "getProfile"), "getName");
    }

    private static InetSocketAddress rawAddress(Object connection) {
        return (InetSocketAddress) invoke(connection, "getRawAddress");
    }

    public void onLoginStart(Object loginStartEvent) {
        if ((boolean) invoke(loginStartEvent, "isLoginDenied")
                || plugin.getCore().getAuthPluginHook() == null) {
            return;
        }

        Object connection = connectionOf(loginStartEvent);
        String username = profileName(connection);
        InetSocketAddress address = rawAddress(connection);
        plugin.getLog().info("Incoming login request for {} from {}", username, address);

        Action action = antiBotService.onIncomingConnection(address, username);
        if (action != Action.Continue) {
            BukkitFastLoginAntiBotEvent antiBotEvent =
                    new BukkitFastLoginAntiBotEvent(address, username, action);
            plugin.getServer().getPluginManager().callEvent(antiBotEvent);
            if (antiBotEvent.isCancelled()) {
                action = Action.Continue;
            }
        }

        switch (action) {
            case Block:
                String message = plugin.getCore().getMessage("kick-antibot");
                invoke(loginStartEvent, "denyLogin", message);
                break;
            case Continue:
            default:
                //remove old data every time on a new login in order to keep the session only for one person
                plugin.removeSession(address);

                try {
                    ProtocolLoginSource source = new ProtocolLoginSource(loginStartEvent);
                    super.onLogin(username, source);
                } catch (ReflectiveOperationException ex) {
                    plugin.getLog().warn("ProtocolSupport login source unavailable: " + ex.getMessage());
                }
                break;
        }
    }

    public void onConnectionClosed(Object closeEvent) {
        InetSocketAddress address = rawAddress(connectionOf(closeEvent));
        plugin.removeSession(address);
    }

    public void onPropertiesResolve(Object profileCompleteEvent) {
        Object connection = connectionOf(profileCompleteEvent);
        InetSocketAddress address = rawAddress(connection);

        BukkitLoginSession session = plugin.getSession(address);

        Object profile = invoke(connection, "getProfile");
        if (session != null && (boolean) invoke(profile, "isOnlineMode")) {
            session.setVerifiedPremium(true);
            // Same reason as the proxy paths: carry the verified Mojang UUID into the session.
            // ProtocolSupport applies the offline rewrite below to this very profile object once
            // the event returns, so afterwards neither resolvePremiumUuid source is v4 and AuthMe
            // would never get a premium_uuid written.
            session.setUuid((UUID) invoke(profile, "getUUID"));

            if (!plugin.getConfig().getBoolean("premiumUuid")) {
                String username = Optional.ofNullable((String) invoke(profileCompleteEvent, "getForcedName"))
                        .orElse((String) invoke(profile, "getName"));
                invoke(profileCompleteEvent, "setForcedUUID", UUIDAdapter.generateOfflineId(username));
            }
        }
    }

    @Override
    public FastLoginPreLoginEvent callFastLoginPreLoginEvent(String username, ProtocolLoginSource source,
                                                             StoredProfile profile) {
        BukkitFastLoginPreLoginEvent event = new BukkitFastLoginPreLoginEvent(username, source, profile);
        plugin.getServer().getPluginManager().callEvent(event);
        return event;
    }

    @Override
    public void requestPremiumLogin(ProtocolLoginSource source, StoredProfile profile, String username,
                                    boolean registered) {
        source.enableOnlinemode();

        BukkitLoginSession playerSession = new BukkitLoginSession(username, registered, profile);
        plugin.putSession(source.getAddress(), playerSession);
    }

    @Override
    public void startCrackedSession(ProtocolLoginSource source, StoredProfile profile, String username) {
        BukkitLoginSession loginSession = new BukkitLoginSession(username, profile);
        plugin.putSession(source.getAddress(), loginSession);
    }
}
