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

import com.github.games647.fastlogin.core.shared.LoginSource;

import java.lang.reflect.Method;
import java.net.InetSocketAddress;

/**
 * ProtocolSupport login source without a compile-time ProtocolSupport
 * dependency. The ProtocolSupport API is resolved reflectively so this fork
 * builds without the unmaintained JitPack artifact; behaviour is identical
 * when ProtocolSupport is installed and the hook is simply skipped when it
 * is absent or its API drifted.
 */
public class ProtocolLoginSource implements LoginSource {

    private final Object loginStartEvent;
    private final Method setOnlineMode;
    private final Method denyLogin;
    private final Method getConnection;

    public ProtocolLoginSource(Object loginStartEvent) throws ReflectiveOperationException {
        this.loginStartEvent = loginStartEvent;
        Class<?> eventClass = loginStartEvent.getClass();
        this.setOnlineMode = eventClass.getMethod("setOnlineMode", boolean.class);
        this.denyLogin = eventClass.getMethod("denyLogin", String.class);
        this.getConnection = eventClass.getMethod("getConnection");
    }

    private static Object invoke(Method method, Object target, Object... args) {
        try {
            return method.invoke(target, args);
        } catch (ReflectiveOperationException ex) {
            throw new IllegalStateException("ProtocolSupport call failed: " + method.getName(), ex);
        }
    }

    private Object connection() {
        return invoke(getConnection, loginStartEvent);
    }

    private static Object profile(Object connection) throws ReflectiveOperationException {
        return connection.getClass().getMethod("getProfile").invoke(connection);
    }

    @Override
    public void enableOnlinemode() {
        invoke(setOnlineMode, loginStartEvent, true);
    }

    @Override
    public void kick(String message) {
        invoke(denyLogin, loginStartEvent, message);
    }

    @Override
    public InetSocketAddress getAddress() {
        try {
            Object connection = connection();
            return (InetSocketAddress) connection.getClass().getMethod("getRawAddress").invoke(connection);
        } catch (ReflectiveOperationException ex) {
            throw new IllegalStateException("ProtocolSupport call failed: getRawAddress", ex);
        }
    }

    public Object getLoginStartEvent() {
        return loginStartEvent;
    }

    @Override
    public String toString() {
        return this.getClass().getSimpleName() + '{'
            + "loginStartEvent=" + loginStartEvent
            + '}';
    }
}
