package com.example.warrantyvault

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

class DatabaseHelper(context: Context) : SQLiteOpenHelper(context, DATABASE_NAME, null, DATABASE_VERSION) {

    companion object {
        private const val DATABASE_NAME = "warranty_vault.db"
        private const val DATABASE_VERSION = 3 // Upgraded version for Maintenance feature

        // Receipts Table
        const val TABLE_RECEIPTS = "receipts"
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

        // Maintenance Table
        const val TABLE_MAINTENANCE = "maintenance_tasks"
        const val COLUMN_TASK_ID = "task_id"
        const val COLUMN_RECEIPT_ID = "receipt_id"
        const val COLUMN_TASK_TITLE = "task_title"
        const val COLUMN_INTERVAL_DAYS = "interval_days"
        const val COLUMN_NEXT_DUE_DATE = "next_due_date"
        const val COLUMN_MANUAL_NOTES = "manual_notes"
    }

    override fun onCreate(db: SQLiteDatabase) {
        val createReceiptsTable = ("CREATE TABLE $TABLE_RECEIPTS ("
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

        val createMaintenanceTable = ("CREATE TABLE $TABLE_MAINTENANCE ("
                + "$COLUMN_TASK_ID INTEGER PRIMARY KEY AUTOINCREMENT, "
                + "$COLUMN_RECEIPT_ID INTEGER, "
                + "$COLUMN_TASK_TITLE TEXT, "
                + "$COLUMN_INTERVAL_DAYS INTEGER, "
                + "$COLUMN_NEXT_DUE_DATE TEXT, "
                + "$COLUMN_MANUAL_NOTES TEXT)")

        db.execSQL(createReceiptsTable)
        db.execSQL(createMaintenanceTable)
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        db.execSQL("DROP TABLE IF EXISTS $TABLE_RECEIPTS")
        db.execSQL("DROP TABLE IF EXISTS $TABLE_MAINTENANCE")
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
        return db.insert(TABLE_RECEIPTS, null, values)
    }

    fun insertMaintenanceTask(receiptId: Long, title: String, intervalDays: Int, nextDueDate: String, notes: String): Long {
        val db = this.writableDatabase
        val values = ContentValues().apply {
            put(COLUMN_RECEIPT_ID, receiptId)
            put(COLUMN_TASK_TITLE, title)
            put(COLUMN_INTERVAL_DAYS, intervalDays)
            put(COLUMN_NEXT_DUE_DATE, nextDueDate)
            put(COLUMN_MANUAL_NOTES, notes)
        }
        return db.insert(TABLE_MAINTENANCE, null, values)
    }

    fun getTasksForReceipt(receiptId: Long): Cursor {
        val db = this.readableDatabase
        return db.rawQuery("SELECT * FROM $TABLE_MAINTENANCE WHERE$COLUMN_RECEIPT_ID = ?", arrayOf(receiptId.toString()))
    }

    fun getAllItems(): Cursor {
        val db = this.readableDatabase
        return db.rawQuery("SELECT * FROM $TABLE_RECEIPTS ORDER BY$COLUMN_ID DESC", null)
    }
}