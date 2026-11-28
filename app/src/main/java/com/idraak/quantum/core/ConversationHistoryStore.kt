package com.idraak.quantum.core

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

data class ConversationEntry(
    val id: Long,
    val role: String,
    val message: String,
    val createdAt: Long
)

/** Local conversation history. Entries remain on this device only. */
class ConversationHistoryStore(context: Context) :
    SQLiteOpenHelper(context.applicationContext, "idraak_conversation_history.db", null, 1) {

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            """CREATE TABLE conversation_entries (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                role TEXT NOT NULL,
                message TEXT NOT NULL,
                created_at INTEGER NOT NULL
            )""".trimIndent()
        )
        db.execSQL("CREATE INDEX conversation_created_idx ON conversation_entries(created_at)")
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit

    fun add(role: String, message: String) {
        val clean = message.trim()
        if (clean.isEmpty()) return
        val values = ContentValues().apply {
            put("role", role.take(20))
            put("message", clean.take(8000))
            put("created_at", System.currentTimeMillis())
        }
        writableDatabase.insert("conversation_entries", null, values)
    }

    fun recent(limit: Int = 100): List<ConversationEntry> {
        val entries = mutableListOf<ConversationEntry>()
        readableDatabase.query(
            "conversation_entries",
            arrayOf("id", "role", "message", "created_at"),
            null, null, null, null, "id DESC", limit.coerceIn(1, 300).toString()
        ).use { cursor ->
            while (cursor.moveToNext()) {
                entries += ConversationEntry(
                    cursor.getLong(0), cursor.getString(1),
                    cursor.getString(2), cursor.getLong(3)
                )
            }
        }
        return entries
    }
}
