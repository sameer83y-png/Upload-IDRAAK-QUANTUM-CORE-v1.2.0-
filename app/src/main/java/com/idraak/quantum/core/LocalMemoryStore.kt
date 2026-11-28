package com.idraak.quantum.core

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

data class MemoryRecord(
    val id: Long,
    val category: String,
    val content: String,
    val source: String,
    val createdAt: Long,
    val updatedAt: Long
)

data class MemoryCorrection(
    val id: Long,
    val previousValue: String,
    val correctedValue: String,
    val reason: String,
    val createdAt: Long
)

/**
 * Explicit, local-only memory storage.
 * Nothing is stored unless a caller invokes remember() after an explicit user request.
 * Credentials and secrets should never be passed to this store.
 */
class LocalMemoryStore(context: Context) :
    SQLiteOpenHelper(context.applicationContext, DATABASE_NAME, null, DATABASE_VERSION) {

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            """CREATE TABLE memories (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                category TEXT NOT NULL,
                content TEXT NOT NULL,
                source TEXT NOT NULL,
                created_at INTEGER NOT NULL,
                updated_at INTEGER NOT NULL
            )""".trimIndent()
        )
        db.execSQL(
            """CREATE TABLE corrections (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                previous_value TEXT NOT NULL,
                corrected_value TEXT NOT NULL,
                reason TEXT NOT NULL,
                created_at INTEGER NOT NULL
            )""".trimIndent()
        )
        db.execSQL("CREATE INDEX memories_content_idx ON memories(content)")
        db.execSQL("CREATE INDEX memories_category_idx ON memories(category)")
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        // Future schema migrations will be added here without deleting user memories.
    }

    fun remember(content: String, category: String = "general", source: String = "user") : Long {
        val clean = content.trim()
        require(clean.isNotEmpty()) { "Memory cannot be empty." }
        require(clean.length <= MAX_CONTENT_LENGTH) { "Memory is too long." }
        val now = System.currentTimeMillis()
        val values = ContentValues().apply {
            put("category", category.trim().lowercase().ifBlank { "general" }.take(40))
            put("content", clean)
            put("source", source.take(40))
            put("created_at", now)
            put("updated_at", now)
        }
        return writableDatabase.insertOrThrow("memories", null, values)
    }

    fun search(query: String, limit: Int = 50): List<MemoryRecord> {
        val clean = query.trim()
        if (clean.isEmpty()) return all(limit)
        val escaped = clean.replace("%", "\\%").replace("_", "\\_")
        return readMemories(
            "content LIKE ? ESCAPE '\\'",
            arrayOf("%$escaped%"),
            limit.coerceIn(1, 100)
        )
    }

    fun all(limit: Int = 50): List<MemoryRecord> =
        readMemories(null, null, limit.coerceIn(1, 100))

    fun byCategory(category: String, limit: Int = 50): List<MemoryRecord> {
        val clean = category.trim().lowercase().take(40)
        if (clean.isBlank()) return emptyList()
        return readMemories(
            "category = ?",
            arrayOf(clean),
            limit.coerceIn(1, 100)
        )
    }

    fun forgetMatching(query: String): Int {
        val clean = query.trim()
        if (clean.isEmpty()) return 0
        return writableDatabase.delete("memories", "content = ?", arrayOf(clean))
    }

    fun correct(previousValue: String, correctedValue: String, reason: String = "User correction"): Boolean {
        val previous = previousValue.trim()
        val corrected = correctedValue.trim()
        require(previous.isNotEmpty() && corrected.isNotEmpty()) { "Both values are required." }
        require(corrected.length <= MAX_CONTENT_LENGTH) { "Corrected memory is too long." }

        val db = writableDatabase
        val now = System.currentTimeMillis()
        db.beginTransaction()
        try {
            val correction = ContentValues().apply {
                put("previous_value", previous)
                put("corrected_value", corrected)
                put("reason", reason.take(200))
                put("created_at", now)
            }
            db.insertOrThrow("corrections", null, correction)

            val update = ContentValues().apply {
                put("content", corrected)
                put("updated_at", now)
            }
            val changed = db.update("memories", update, "content = ?", arrayOf(previous)) > 0
            db.setTransactionSuccessful()
            return changed
        } finally {
            db.endTransaction()
        }
    }

    fun correctionHistory(limit: Int = 50): List<MemoryCorrection> {
        val items = mutableListOf<MemoryCorrection>()
        readableDatabase.rawQuery(
            "SELECT id, previous_value, corrected_value, reason, created_at FROM corrections ORDER BY id DESC LIMIT ?",
            arrayOf(limit.coerceIn(1, 100).toString())
        ).use { cursor ->
            while (cursor.moveToNext()) {
                items += MemoryCorrection(
                    id = cursor.getLong(0),
                    previousValue = cursor.getString(1),
                    correctedValue = cursor.getString(2),
                    reason = cursor.getString(3),
                    createdAt = cursor.getLong(4)
                )
            }
        }
        return items
    }

    private fun readMemories(
        selection: String?,
        selectionArgs: Array<String>?,
        limit: Int
    ): List<MemoryRecord> {
        val items = mutableListOf<MemoryRecord>()
        readableDatabase.query(
            "memories",
            arrayOf("id", "category", "content", "source", "created_at", "updated_at"),
            selection,
            selectionArgs,
            null,
            null,
            "updated_at DESC",
            limit.toString()
        ).use { cursor ->
            while (cursor.moveToNext()) {
                items += MemoryRecord(
                    id = cursor.getLong(0),
                    category = cursor.getString(1),
                    content = cursor.getString(2),
                    source = cursor.getString(3),
                    createdAt = cursor.getLong(4),
                    updatedAt = cursor.getLong(5)
                )
            }
        }
        return items
    }

    companion object {
        private const val DATABASE_NAME = "idraak_local_memory.db"
        private const val DATABASE_VERSION = 1
        private const val MAX_CONTENT_LENGTH = 2000
    }
}
