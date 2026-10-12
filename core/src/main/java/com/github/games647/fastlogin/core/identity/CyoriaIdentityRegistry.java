/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 CyoriaSMP-Team and contributors
 */
package com.github.games647.fastlogin.core.identity;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * Phase-1, fail-closed namespace registry, separate from FastLogin/AuthMe tables.
 * This class NEVER validates Mojang authentication, passwords, or migrates plugin
 * data itself. Callers may stage a claim ONLY after Mojang Session verification
 * and AuthMe password establishment. No live login path calls it yet.
 *
 * This API is synchronized per registry/connection; production must use one
 * writer and durable SQLite WAL plus external backup/transaction orchestration.
 */
public final class CyoriaIdentityRegistry {

    public enum ClaimState { RESERVED_FOR_PREMIUM, WAITING_FOR_LOCAL_RENAME, ACTIVE }

    private final Connection connection;

    public CyoriaIdentityRegistry(Connection connection) throws SQLException {
        this.connection = Objects.requireNonNull(connection, "connection");
        try (Statement statement = connection.createStatement()) {
            statement.execute("CREATE TABLE IF NOT EXISTS ci_locals ("
                    + "local_uuid TEXT PRIMARY KEY, name_key TEXT NOT NULL UNIQUE, "
                    + "name TEXT NOT NULL)");
            statement.execute("CREATE TABLE IF NOT EXISTS ci_claims ("
                    + "name_key TEXT PRIMARY KEY, name TEXT NOT NULL, "
                    + "premium_uuid TEXT NOT NULL, state TEXT NOT NULL)");
            statement.execute("CREATE TABLE IF NOT EXISTS ci_rename_plans ("
                    + "local_uuid TEXT PRIMARY KEY, old_key TEXT NOT NULL UNIQUE, "
                    + "new_key TEXT NOT NULL UNIQUE, new_name TEXT NOT NULL)");
        }
    }

    private interface Transaction<T> {
        T call() throws SQLException;
    }

    private synchronized <T> T transaction(Transaction<T> transaction) throws SQLException {
        if (!connection.getAutoCommit()) {
            throw new IllegalStateException("Identity registry requires an exclusive auto-commit connection");
        }
        connection.setAutoCommit(false);
        try {
            T result = transaction.call();
            connection.commit();
            return result;
        } catch (SQLException | RuntimeException exception) {
            connection.rollback();
            throw exception;
        } finally {
            connection.setAutoCommit(true);
        }
    }

    /**
     * Remember the initial Local identity; never reassign an existing name/UUID.
     * @param name original login name
     * @param localUuid immutable Local player identity
     * @return true if the name is safely reserved for the same Local UUID
     */
    public boolean reserveLocal(String name, UUID localUuid) throws SQLException {
        final String display = IdentityNames.validate(name);
        final String key = IdentityNames.key(name);
        Objects.requireNonNull(localUuid, "localUuid");
        return transaction(() -> {
            if (claimExists(key) || renameTargetExists(key)) {
                return false;
            }
            try (PreparedStatement statement = connection.prepareStatement(
                    "SELECT local_uuid, name_key FROM ci_locals WHERE name_key=? OR local_uuid=?")) {
                statement.setString(1, key);
                statement.setString(2, localUuid.toString());
                try (ResultSet result = statement.executeQuery()) {
                    if (result.next()) {
                        return localUuid.toString().equals(result.getString(1))
                                && key.equals(result.getString(2));
                    }
                }
            }
            try (PreparedStatement statement = connection.prepareStatement(
                    "INSERT INTO ci_locals(local_uuid,name_key,name) VALUES(?,?,?)")) {
                statement.setString(1, localUuid.toString());
                statement.setString(2, key);
                statement.setString(3, display);
                statement.executeUpdate();
            }
            return true;
        });
    }

    /**
     * A Premium claim CANNOT be created merely by looking up a Mojang username.
     * Verified proof and a usable AuthMe password are required; the integration
     * must independently establish both, NOT trust a client-supplied boolean.
     * @param name desired Premium login name
     * @param premiumUuid the Mojang-assigned, server-verified UUID
     * @param mojangSessionVerified true only after Mojang hasJoined succeeds
     * @param passwordEstablished true only after a usable AuthMe password was committed
     * @return claim state, or waiting for previous Local to rename
     */
    public ClaimState stageVerifiedPremiumClaim(String name, UUID premiumUuid,
                                                 boolean mojangSessionVerified,
                                                 boolean passwordEstablished) throws SQLException {
        final String display = IdentityNames.validate(name);
        final String key = IdentityNames.key(name);
        Objects.requireNonNull(premiumUuid, "premiumUuid");
        if (!mojangSessionVerified || !passwordEstablished) {
            throw new SecurityException("A verified Mojang session and stored password are mandatory");
        }
        return transaction(() -> {
            try (PreparedStatement statement = connection.prepareStatement(
                    "SELECT premium_uuid,state FROM ci_claims WHERE name_key=?")) {
                statement.setString(1, key);
                try (ResultSet result = statement.executeQuery()) {
                    if (result.next()) {
                        if (!premiumUuid.toString().equals(result.getString(1))) {
                            throw new SecurityException("Name is already claimed by another Premium UUID");
                        }
                        return ClaimState.valueOf(result.getString(2));
                    }
                }
            }
            if (renameTargetExists(key)) {
                throw new IllegalStateException("Name is reserved by a pending Local migration");
            }
            boolean existingLocal = localForName(key) != null;
            ClaimState state = existingLocal ? ClaimState.WAITING_FOR_LOCAL_RENAME : ClaimState.ACTIVE;
            try (PreparedStatement statement = connection.prepareStatement(
                    "INSERT INTO ci_claims(name_key,name,premium_uuid,state) VALUES(?,?,?,?)")) {
                statement.setString(1, key);
                statement.setString(2, display);
                statement.setString(3, premiumUuid.toString());
                statement.setString(4, state.name());
                statement.executeUpdate();
            }
            return state;
        });
    }

    /**
     * Stage rename only. The old Local UUID and password remain theirs.
     * @param localUuid original Local UUID
     * @param oldName currently owned login name
     * @param newName target login name
     * @return immutable migration plan; does not move player data
     */
    public IdentityMigrationPlan stageLocalRename(UUID localUuid, String oldName,
                                                   String newName) throws SQLException {
        Objects.requireNonNull(localUuid, "localUuid");
        final String oldKey = IdentityNames.key(oldName);
        final String newKey = IdentityNames.key(newName);
        final String validatedName = IdentityNames.validate(newName);
        if (oldKey.equals(newKey)) {
            throw new IllegalArgumentException("Rename must change canonical name");
        }
        return transaction(() -> {
            String currentLocal = localForName(oldKey);
            if (!localUuid.toString().equals(currentLocal)) {
                throw new SecurityException("Local UUID does not own the old name");
            }
            if (claimState(oldKey) != ClaimState.WAITING_FOR_LOCAL_RENAME) {
                throw new IllegalStateException("No verified pending Premium claim");
            }
            if (renameForLocal(localUuid.toString())) {
                throw new IllegalStateException("A migration plan is already pending for this Local account");
            }
            if (claimExists(newKey) || localForName(newKey) != null || renameTargetExists(newKey)) {
                throw new IllegalArgumentException("New local name is already reserved");
            }
            try (PreparedStatement stmt = connection.prepareStatement(
                    "INSERT INTO ci_rename_plans(local_uuid,old_key,new_key,new_name) VALUES(?,?,?,?)")) {
                stmt.setString(1, localUuid.toString());
                stmt.setString(2, oldKey);
                stmt.setString(3, newKey);
                stmt.setString(4, validatedName);
                stmt.executeUpdate();
            }
            return new IdentityMigrationPlan(localUuid, oldName, validatedName);
        });
    }

    /**
     * Commit ONLY after a separate executor migrated AuthMe and every data store.
     * @param plan original pending migration plan
     * @param completed independently verified migration-store receipts
     */
    public void completeLocalRename(IdentityMigrationPlan plan,
                                    Set<IdentityMigrationPlan.Store> completed) throws SQLException {
        Objects.requireNonNull(plan, "plan");
        plan.verifyCompleted(completed);
        transaction(() -> {
            String oldKey = IdentityNames.key(plan.getPreviousName());
            String newKey = IdentityNames.key(plan.getNewName());
            UUID localUuid = plan.getLocalUuid();
            try (PreparedStatement stmt = connection.prepareStatement(
                    "SELECT old_key,new_key FROM ci_rename_plans WHERE local_uuid=?")) {
                stmt.setString(1, localUuid.toString());
                try (ResultSet result = stmt.executeQuery()) {
                    if (!result.next() || !oldKey.equals(result.getString(1))
                            || !newKey.equals(result.getString(2))) {
                        throw new SecurityException("Mismatched or absent migration plan");
                    }
                }
            }
            if (claimState(oldKey) != ClaimState.WAITING_FOR_LOCAL_RENAME
                    || !localUuid.toString().equals(localForName(oldKey))) {
                throw new IllegalStateException("Claim/local ownership changed during migration");
            }
            try (PreparedStatement stmt = connection.prepareStatement(
                    "UPDATE ci_locals SET name_key=?,name=? WHERE local_uuid=?")) {
                stmt.setString(1, newKey);
                stmt.setString(2, plan.getNewName());
                stmt.setString(3, localUuid.toString());
                if (stmt.executeUpdate() != 1) {
                    throw new SQLException("Local account not migrated");
                }
            }
            try (PreparedStatement stmt = connection.prepareStatement(
                    "UPDATE ci_claims SET state='ACTIVE' WHERE name_key=? AND state='WAITING_FOR_LOCAL_RENAME'")) {
                stmt.setString(1, oldKey);
                if (stmt.executeUpdate() != 1) {
                    throw new SQLException("Premium claim not activated");
                }
            }
            try (PreparedStatement stmt = connection.prepareStatement(
                    "DELETE FROM ci_rename_plans WHERE local_uuid=?")) {
                stmt.setString(1, localUuid.toString());
                stmt.executeUpdate();
            }
            return null;
        });
    }

    /**
     * Reconstruct a pending rename plan after restart without changing state.
     * @param localUuid the Local identity that owns the plan
     * @return pending plan, or null if none exists
     */
    public synchronized IdentityMigrationPlan pendingPlan(UUID localUuid) throws SQLException {
        Objects.requireNonNull(localUuid, "localUuid");
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT old_key,new_name FROM ci_rename_plans WHERE local_uuid=?")) {
            statement.setString(1, localUuid.toString());
            try (ResultSet r = statement.executeQuery()) {
                if (r.next()) {
                    return new IdentityMigrationPlan(localUuid, r.getString(1), r.getString(2));
                }
                return null;
            }
        }
    }

    public synchronized ClaimState claimState(String name) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT state FROM ci_claims WHERE name_key=?")) {
            statement.setString(1, IdentityNames.key(name));
            try (ResultSet r = statement.executeQuery()) {
                return r.next() ? ClaimState.valueOf(r.getString(1)) : null;
            }
        }
    }

    private boolean claimExists(String key) throws SQLException {
        return claimState(key) != null;
    }

    private String localForName(String key) throws SQLException {
        try (PreparedStatement s = connection.prepareStatement("SELECT local_uuid FROM ci_locals WHERE name_key=?")) {
            s.setString(1, key);
            try (ResultSet r = s.executeQuery()) {
                return r.next() ? r.getString(1) : null;
            }
        }
    }

    private boolean renameForLocal(String localId) throws SQLException {
        try (PreparedStatement s = connection.prepareStatement(
                "SELECT 1 FROM ci_rename_plans WHERE local_uuid=?")) {
            s.setString(1, localId);
            try (ResultSet r = s.executeQuery()) {
                return r.next();
            }
        }
    }

    private boolean renameTargetExists(String key) throws SQLException {
        try (PreparedStatement s = connection.prepareStatement("SELECT 1 FROM ci_rename_plans WHERE new_key=?")) {
            s.setString(1, key);
            try (ResultSet r = s.executeQuery()) {
                return r.next();
            }
        }
    }
}
