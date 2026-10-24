package com.github.games647.fastlogin.bukkit.command;

import com.github.games647.fastlogin.core.shared.FloodgateState;
import com.github.games647.fastlogin.core.storage.SQLStorage;
import com.github.games647.fastlogin.core.storage.StoredProfile;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class CrackedCommandPersistenceTest {
    @Test
    void failedDatabaseWriteDoesNotDiscardVerifiedIdentity() {
        UUID uuid = UUID.randomUUID();
        StoredProfile profile = new StoredProfile(uuid, "PremiumPlayer", true, FloodgateState.FALSE, null);
        SQLStorage storage = mock(SQLStorage.class);
        when(storage.saveQuietly(profile)).thenReturn(false);

        assertFalse(CrackedCommand.persistCrackedProfile(profile, storage));
        assertTrue(profile.isOnlinemodePreferred());
        assertEquals(uuid, profile.getId());
        verify(storage).saveQuietly(profile);
    }

    @Test
    void successfulDatabaseWriteClearsPremiumPreferenceAndUuid() {
        StoredProfile profile = new StoredProfile(UUID.randomUUID(), "PremiumPlayer", true,
                FloodgateState.FALSE, null);
        SQLStorage storage = mock(SQLStorage.class);
        when(storage.saveQuietly(profile)).thenReturn(true);

        assertTrue(CrackedCommand.persistCrackedProfile(profile, storage));
        assertFalse(profile.isOnlinemodePreferred());
        assertNull(profile.getId());
        verify(storage).saveQuietly(profile);
    }
}
