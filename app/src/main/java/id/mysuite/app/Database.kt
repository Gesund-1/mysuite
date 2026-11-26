package id.mysuite.app

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

class Database(ctx: Context) : SQLiteOpenHelper(ctx, "MySuite.db", null, 1) {
    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL("CREATE TABLE plugins(id TEXT PRIMARY KEY, name TEXT NOT NULL, version TEXT NOT NULL, enabled INTEGER NOT NULL DEFAULT 1, installed_at INTEGER NOT NULL)")
        db.execSQL("CREATE TABLE plugin_data(plugin_id TEXT NOT NULL, data_key TEXT NOT NULL, json_value TEXT NOT NULL, updated_at INTEGER NOT NULL, PRIMARY KEY(plugin_id,data_key))")
        db.execSQL("CREATE TABLE music_tracks(id TEXT PRIMARY KEY, title TEXT NOT NULL, artist TEXT DEFAULT '', uri TEXT NOT NULL, playlist_id TEXT DEFAULT 'all', added_at INTEGER NOT NULL)")
        db.execSQL("CREATE TABLE music_playlists(id TEXT PRIMARY KEY, name TEXT NOT NULL, created_at INTEGER NOT NULL)")
        db.execSQL("CREATE TABLE vault_notes(id TEXT PRIMARY KEY, title TEXT NOT NULL, body TEXT NOT NULL, updated_at INTEGER NOT NULL)")
        db.execSQL("CREATE TABLE vault_bookmarks(id TEXT PRIMARY KEY, title TEXT NOT NULL, url TEXT NOT NULL, created_at INTEGER NOT NULL)")
        db.execSQL("CREATE TABLE vault_articles(id TEXT PRIMARY KEY, title TEXT NOT NULL, url TEXT NOT NULL, content TEXT DEFAULT '', created_at INTEGER NOT NULL)")
        db.execSQL("CREATE TABLE finance_transactions(id TEXT PRIMARY KEY, type TEXT NOT NULL, title TEXT NOT NULL, amount REAL NOT NULL, date TEXT NOT NULL, note TEXT DEFAULT '')")
        db.execSQL("CREATE TABLE finance_budgets(id TEXT PRIMARY KEY, period TEXT NOT NULL, amount REAL NOT NULL, start_date TEXT NOT NULL)")
        db.execSQL("CREATE INDEX idx_finance_date ON finance_transactions(date)")
        db.execSQL("CREATE INDEX idx_music_playlist ON music_tracks(playlist_id)")
    }
    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) { }
    @Synchronized fun put(plugin: String, key: String, value: String) {
        val cv=ContentValues().apply { put("plugin_id",plugin); put("data_key",key); put("json_value",value); put("updated_at",System.currentTimeMillis()) }
        writableDatabase.insertWithOnConflict("plugin_data",null,cv,SQLiteDatabase.CONFLICT_REPLACE)
    }
    @Synchronized fun get(plugin: String, key: String): String? = readableDatabase.rawQuery("SELECT json_value FROM plugin_data WHERE plugin_id=? AND data_key=?", arrayOf(plugin,key)).use { if(it.moveToFirst()) it.getString(0) else null }
}
