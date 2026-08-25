package com.example.warrantyvault

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

class DatabaseHelper(context: Context) : SQLiteOpenHelper(context, DATABASE_NAME, null, DATABASE_VERSION) {

    override fun onCreate(db: SQLiteDatabase) {
        // Schema specified in Phase 2 documentation
        val createItemsTable = """
            CREATE TABLE items (
                item_id INTEGER PRIMARY KEY AUTOINCREMENT, 
                item_name TEXT NOT NULL, 
                store_name TEXT NOT NULL, 
                category TEXT NOT NULL, 
                price REAL NOT NULL, 
                purchase_date TEXT NOT NULL, 
                duration_days INTEGER NOT NULL, 
                calculated_expiration TEXT NOT NULL, 
                image_path TEXT NOT NULL, 
                status_flag TEXT NOT NULL, 
                notification_ids TEXT
            );
        """.trimIndent()
        db.execSQL(createItemsTable)
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        db.execSQL("DROP TABLE IF EXISTS items")
        onCreate(db)
    }

    // Insert a new warranty record
    fun insertItem(
        itemName: String,
        storeName: String,
        category: String,
        price: Double,
        purchaseDate: String,
        durationDays: Int,
        calculatedExpiration: String,
        imagePath: String,
        statusFlag: String,
        notificationIds: String
    ): Long {
        val db = this.writableDatabase
        val values = ContentValues().apply {
            put("item_name", itemName)
            put("store_name", storeName)
            put("category", category)
            put("price", price)
            put("purchase_date", purchaseDate)
            put("duration_days", durationDays)
            put("calculated_expiration", calculatedExpiration)
            put("image_path", imagePath)
            put("status_flag", statusFlag)
            put("notification_ids", notificationIds)
        }
        val result = db.insert("items", null, values)
        db.close()
        return result
    }

    // Retrieve all records for dashboard rendering
    fun getAllItems(): Cursor {
        val db = this.readableDatabase
        return db.rawQuery("SELECT * FROM items ORDER BY item_id DESC", null)
    }

    companion object {
        private const val DATABASE_NAME = "warranty_vault.db"
        private const val DATABASE_VERSION = 1
    }
}