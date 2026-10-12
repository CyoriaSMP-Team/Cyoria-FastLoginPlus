/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 CyoriaSMP-Team and contributors
 */
package com.github.games647.fastlogin.core.identity;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.DriverManager;
import java.util.EnumSet;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class CyoriaIdentityRegistryTest {
    private Connection connection;
    private CyoriaIdentityRegistry registry;
    private final UUID local = UUID.fromString("f57ea40d-ba3a-34a4-839b-a0659169cd58");
    private final UUID premium = UUID.fromString("c83c3db1-3ffe-4105-a768-b1315664d332");

    @BeforeEach
    void setUp() throws Exception {
        connection = DriverManager.getConnection("jdbc:sqlite::memory:");
        registry = new CyoriaIdentityRegistry(connection);
    }

    @AfterEach
    void tearDown() throws Exception {
        connection.close();
    }

    @Test
    void localGetsUnclaimedNameWithoutOverwritingDifferentUuid() throws Exception {
        assertTrue(registry.reserveLocal("NamKrub", local));
        assertTrue(registry.reserveLocal("namkrub", local));
        assertFalse(registry.reserveLocal("namkrub", UUID.randomUUID()));
        assertFalse(registry.reserveLocal("SomeoneElse", local));
    }

    @Test
    void publicMojangNameLookupIsNotProofOfOwnership() throws Exception {
        assertTrue(registry.reserveLocal("NamKrub", local));
        assertThrows(SecurityException.class,
                () -> registry.stageVerifiedPremiumClaim("NamKrub", premium, false, true));
        assertThrows(SecurityException.class,
                () -> registry.stageVerifiedPremiumClaim("NamKrub", premium, true, false));
        assertNull(registry.claimState("NamKrub"));
    }

    @Test
    void verifiedClaimNeverReassignsExistingLocalAssets() throws Exception {
        assertTrue(registry.reserveLocal("NamKrub", local));
        assertEquals(CyoriaIdentityRegistry.ClaimState.WAITING_FOR_LOCAL_RENAME,
                registry.stageVerifiedPremiumClaim("NAMKRUB", premium, true, true));
        assertFalse(registry.reserveLocal("namkrub", UUID.randomUUID()));
        assertThrows(SecurityException.class,
                () -> registry.stageVerifiedPremiumClaim("NamKrub", UUID.randomUUID(), true, true));
        assertEquals(CyoriaIdentityRegistry.ClaimState.WAITING_FOR_LOCAL_RENAME,
                registry.claimState("namkrub"));
    }

    @Test
    void migratingNeedsCompleteVerifiedStoreSet() throws Exception {
        registry.reserveLocal("NamKrub", local);
        registry.stageVerifiedPremiumClaim("NamKrub", premium, true, true);
        IdentityMigrationPlan plan = registry.stageLocalRename(local, "NamKrub", "Local_NamKrub");
        assertEquals(local, plan.getLocalUuid());
        assertEquals("Local_NamKrub", plan.getNewName());
        assertThrows(IllegalStateException.class,
                () -> registry.completeLocalRename(plan,
                        EnumSet.of(IdentityMigrationPlan.Store.AUTHME,
                                IdentityMigrationPlan.Store.WORLD_PLAYERDATA)));
        assertEquals(CyoriaIdentityRegistry.ClaimState.WAITING_FOR_LOCAL_RENAME,
                registry.claimState("NamKrub"));
        registry.completeLocalRename(plan, EnumSet.allOf(IdentityMigrationPlan.Store.class));
        assertEquals(CyoriaIdentityRegistry.ClaimState.ACTIVE,
                registry.claimState("NamKrub"));
        assertTrue(registry.reserveLocal("local_namkrub", local));
        assertFalse(registry.reserveLocal("NamKrub", local));
    }

    @Test
    void cannotRenameToAnotherLocalOrPremiumOrPendingName() throws Exception {
        registry.reserveLocal("NamKrub", local);
        registry.reserveLocal("Occupied", UUID.randomUUID());
        UUID another = UUID.randomUUID();
        registry.reserveLocal("Alice", another);
        registry.stageVerifiedPremiumClaim("NamKrub", premium, true, true);
        registry.stageVerifiedPremiumClaim("PaidOnly", UUID.randomUUID(), true, true);
        assertThrows(IllegalArgumentException.class,
                () -> registry.stageLocalRename(local, "NamKrub", "Occupied"));
        assertThrows(IllegalArgumentException.class,
                () -> registry.stageLocalRename(local, "NamKrub", "paidonly"));
        assertThrows(SecurityException.class,
                () -> registry.stageLocalRename(another, "NamKrub", "Another"));
        registry.stageLocalRename(local, "NamKrub", "Local_NamKrub");
        assertThrows(IllegalStateException.class,
                () -> registry.stageLocalRename(local, "NamKrub", "NewLocalName"));
    }

    @Test
    void badMigrationPlanCannotActivateClaim() throws Exception {
        registry.reserveLocal("NamKrub", local);
        registry.stageVerifiedPremiumClaim("NamKrub", premium, true, true);
        IdentityMigrationPlan plan = registry.stageLocalRename(local, "NamKrub", "Local_NamKrub");
        assertThrows(SecurityException.class,
                () -> registry.completeLocalRename(
                        new IdentityMigrationPlan(local, "NamKrub", "OtherName"),
                        EnumSet.allOf(IdentityMigrationPlan.Store.class)));
        assertEquals(CyoriaIdentityRegistry.ClaimState.WAITING_FOR_LOCAL_RENAME,
                registry.claimState("NamKrub"));
    }

    @Test
    void claimingUnusedNameIsActiveButNoLocalCanStealIt() throws Exception {
        assertEquals(CyoriaIdentityRegistry.ClaimState.ACTIVE,
                registry.stageVerifiedPremiumClaim("NoLocal", premium, true, true));
        assertFalse(registry.reserveLocal("nolocal", UUID.randomUUID()));
        assertEquals(CyoriaIdentityRegistry.ClaimState.ACTIVE,
                registry.stageVerifiedPremiumClaim("NOLOCAL", premium, true, true));
    }

    @Test
    void usernamesFollowJavaSixteenCharacterLimit() {
        assertEquals("Local_NamKrub", IdentityNames.suggestedLocalName("NamKrub"));
        assertEquals(16, IdentityNames.suggestedLocalName("SixteenCharName").length());
        assertThrows(IllegalArgumentException.class,
                () -> IdentityNames.validate("LongerThanSixteenCharacters"));
        assertThrows(IllegalArgumentException.class,
                () -> IdentityNames.validate("bad name"));
        assertEquals("namkrub", IdentityNames.key("NamKrub"));
    }
    @Test
    void pendingRenameTargetCannotBeSquattedByAnotherLocalOrPremium() throws Exception {
        registry.reserveLocal("NamKrub", local);
        registry.stageVerifiedPremiumClaim("NamKrub", premium, true, true);
        registry.stageLocalRename(local, "NamKrub", "Local_NamKrub");
        assertFalse(registry.reserveLocal("local_namkrub", UUID.randomUUID()));
        assertThrows(IllegalStateException.class,
                () -> registry.stageVerifiedPremiumClaim("Local_NamKrub", UUID.randomUUID(), true, true));
        assertNotNull(registry.pendingPlan(local));
    }

    @Test
    void stagedClaimsAndRenamePlansSurviveSqliteRestart() throws Exception {
        java.nio.file.Path db = java.nio.file.Files.createTempFile("cyoria-identity-test", ".sqlite");
        try {
            try (Connection first = DriverManager.getConnection("jdbc:sqlite:" + db.toString())) {
                CyoriaIdentityRegistry onFirstBoot = new CyoriaIdentityRegistry(first);
                assertTrue(onFirstBoot.reserveLocal("NamKrub", local));
                onFirstBoot.stageVerifiedPremiumClaim("NamKrub", premium, true, true);
                onFirstBoot.stageLocalRename(local, "NamKrub", "Local_NamKrub");
            }
            try (Connection second = DriverManager.getConnection("jdbc:sqlite:" + db.toString())) {
                CyoriaIdentityRegistry onRestart = new CyoriaIdentityRegistry(second);
                assertEquals(CyoriaIdentityRegistry.ClaimState.WAITING_FOR_LOCAL_RENAME,
                        onRestart.claimState("NamKrub"));
                IdentityMigrationPlan pending = onRestart.pendingPlan(local);
                assertNotNull(pending);
                assertEquals("Local_NamKrub", pending.getNewName());
                assertEquals(local, pending.getLocalUuid());
            }
        } finally {
            java.nio.file.Files.deleteIfExists(db);
        }
    }

}
