package com.ankiduel.server.db

import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.transactions.TransactionManager
import org.jetbrains.exposed.sql.transactions.transaction
import java.sql.Connection

object UsersTable : Table("users") {
    val id = varchar("id", 64)
    val username = varchar("username", 64).uniqueIndex()
    val passwordHash = varchar("password_hash", 256)
    val createdAt = long("created_at")
    override val primaryKey = PrimaryKey(id)
}

object DuelsTable : Table("duels") {
    val id = varchar("id", 64)
    val inviteCode = varchar("invite_code", 16).uniqueIndex()
    val creatorId = varchar("creator_id", 64).references(UsersTable.id)
    val configJson = text("config_json")
    val status = varchar("status", 32)
    val currentCycleKey = varchar("current_cycle_key", 32)
    val createdAt = long("created_at")
    override val primaryKey = PrimaryKey(id)
}

object DuelParticipantsTable : Table("duel_participants") {
    val duelId = varchar("duel_id", 64).references(DuelsTable.id)
    val userId = varchar("user_id", 64).references(UsersTable.id)
    val status = varchar("status", 32)
    val ankiEffectiveSeconds = long("anki_effective_seconds")
    val socialSeconds = long("social_seconds")
    val cardsReviewed = integer("cards_reviewed")
    val jokerActiveUntilEpochMs = long("joker_active_until")
    val jokersUsedThisWeek = integer("jokers_used_this_week")
    val weekKey = varchar("week_key", 16)
    val lastSyncEpochMs = long("last_sync_at")
    val ankiApiConnected = bool("anki_api_connected")
    val trackedSocialPackagesJson = text("tracked_social_packages_json")

    override val primaryKey = PrimaryKey(duelId, userId)
}

object DatabaseFactory {
    fun init(jdbcUrl: String = "jdbc:sqlite:ankiduel.db"): Database {
        val db = Database.connect(
            url = jdbcUrl,
            driver = "org.sqlite.JDBC"
        )
        TransactionManager.manager.defaultIsolationLevel = Connection.TRANSACTION_SERIALIZABLE
        transaction(db) {
            SchemaUtils.create(UsersTable, DuelsTable, DuelParticipantsTable)
        }
        return db
    }
}
