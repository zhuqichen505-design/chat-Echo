package com.aiassistant.domain.model

import androidx.room.Entity
import androidx.room.Index

/** Portable identity is independent of a device's SQLite row ID. */
@Entity(tableName = "backup_identities", primaryKeys = ["entityTable", "entityId"], indices = [Index(value = ["uuid"], unique = true)])
data class BackupIdentity(val entityTable: String, val entityId: Long, val uuid: String)
