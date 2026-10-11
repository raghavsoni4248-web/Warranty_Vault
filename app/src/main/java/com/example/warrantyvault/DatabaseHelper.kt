package com.example.warrantyvault

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

class DatabaseHelper(context: Context) : SQLiteOpenHelper(context, DATABASE_NAME, null, DATABASE_VERSION) {

    companion object {
        private const val DATABASE_NAME = "warranty_vault.db"
        private const val DATABASE_VERSION = 2

        const val TABLE_NAME = "receipts"
        const val COLUMN_ID = "id"
        const val COLUMN_ITEM_NAME = "item_name"
        const val COLUMN_STORE_NAME = "store_name"
        const val COLUMN_CATEGORY = "category"
        const val COLUMN_PRICE = "price"
        const val COLUMN_PURCHASE_DATE = "purchase_date"
        const val COLUMN_DURATION_DAYS = "duration_days"
        const val COLUMN_CALCULATED_EXPIRATION = "calculated_expiration"
        const val COLUMN_IMAGE_PATH = "image_path"
        const val COLUMN_STATUS_FLAG = "status_flag"
        const val COLUMN_NOTIFICATION_IDS = "notification_ids"
    }

    override fun onCreate(db: SQLiteDatabase) {
        val createTableQuery = ("CREATE TABLE $TABLE_NAME ("
                + "$COLUMN_ID INTEGER PRIMARY KEY AUTOINCREMENT, "
                + "$COLUMN_ITEM_NAME TEXT, "
                + "$COLUMN_STORE_NAME TEXT, "
                + "$COLUMN_CATEGORY TEXT, "
                + "$COLUMN_PRICE REAL, "
                + "$COLUMN_PURCHASE_DATE TEXT, "
                + "$COLUMN_DURATION_DAYS INTEGER, "
                + "$COLUMN_CALCULATED_EXPIRATION TEXT, "
                + "$COLUMN_IMAGE_PATH TEXT, "
                + "$COLUMN_STATUS_FLAG TEXT, "
                + "$COLUMN_NOTIFICATION_IDS TEXT)")
        db.execSQL(createTableQuery)
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        db.execSQL("DROP TABLE IF EXISTS $TABLE_NAME")
        onCreate(db)
    }

    fun insertItem(
        itemName: String, storeName: String, category: String, price: Double,
        purchaseDate: String, durationDays: Int, calculatedExpiration: String,
        imagePath: String, statusFlag: String, notificationIds: String
    ): Long {
        val db = this.writableDatabase
        val values = ContentValues().apply {
            put(COLUMN_ITEM_NAME, itemName)
            put(COLUMN_STORE_NAME, storeName)
            put(COLUMN_CATEGORY, category)
            put(COLUMN_PRICE, price)
            put(COLUMN_PURCHASE_DATE, purchaseDate)
            put(COLUMN_DURATION_DAYS, durationDays)
            put(COLUMN_CALCULATED_EXPIRATION, calculatedExpiration)
            put(COLUMN_IMAGE_PATH, imagePath)
            put(COLUMN_STATUS_FLAG, statusFlag)
            put(COLUMN_NOTIFICATION_IDS, notificationIds)
        }
        return db.insert(TABLE_NAME, null, values)
    }

    fun getAllItems(): Cursor {
        val db = this.readableDatabase
        return db.rawQuery("SELECT * FROM $TABLE_NAME ORDER BY $COLUMN_ID DESC", null)
    }
}