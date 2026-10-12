/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 CyoriaSMP-Team and contributors
 */
package com.github.games647.fastlogin.core.identity;

import java.util.Collections;
import java.util.EnumSet;
import java.util.Set;
import java.util.UUID;

/**
 * No migrations are performed just by constructing this immutable plan.
 * The executor MUST back up and migrate every required plugin store (or stop).
 */
public final class IdentityMigrationPlan {
    public enum Store {
        AUTHME, FASTLOGIN, WORLD_PLAYERDATA, WORLD_STATS, WORLD_ADVANCEMENTS,
        LUCKPERMS, PLAYER_VAULTS, ESSENTIALS, ECONOMY, OTHER_PLUGINS
    }

    private final UUID localUuid;
    private final String previousName;
    private final String newName;
    private final Set<Store> requiredStores;

    IdentityMigrationPlan(UUID localUuid, String previousName, String newName) {
        this.localUuid = localUuid;
        this.previousName = previousName;
        this.newName = newName;
        this.requiredStores = Collections.unmodifiableSet(EnumSet.allOf(Store.class));
    }

    public UUID getLocalUuid() {
        return localUuid;
    }
    public String getPreviousName() {
        return previousName;
    }
    public String getNewName() {
        return newName;
    }
    public Set<Store> getRequiredStores() {
        return requiredStores;
    }

    public void verifyCompleted(Set<Store> completedStores) {
        if (completedStores == null || !completedStores.containsAll(requiredStores)) {
            throw new IllegalStateException("Incomplete account migration. Cannot release premium name");
        }
    }
}
