package com.example.warrantyvault

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

class MaintenanceActivity : AppCompatActivity() {

    private lateinit var dbHelper: DatabaseHelper
    private var receiptId: Long = -1
    private var itemName: String = "Item"

    private lateinit var etTaskTitle: EditText
    private lateinit var etIntervalDays: EditText
    private lateinit var etManualNotes: EditText
    private lateinit var llTasksList: LinearLayout

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_maintenance)

        dbHelper = DatabaseHelper(this)

        receiptId = intent.getLongExtra("RECEIPT_ID", -1)
        itemName = intent.getStringExtra("ITEM_NAME") ?: "Item"

        findViewById<TextView>(R.id.tvHeaderTitle).text = "🛠️ Care Hub: $itemName"

        etTaskTitle = findViewById(R.id.etTaskTitle)
        etIntervalDays = findViewById(R.id.etIntervalDays)
        etManualNotes = findViewById(R.id.etManualNotes)
        llTasksList = findViewById(R.id.llTasksList)

        findViewById<Button>(R.id.btnSaveTask).setOnClickListener { saveTask() }

        loadTasks()
    }

    private fun saveTask() {
        val title = etTaskTitle.text.toString().trim()
        val intervalStr = etIntervalDays.text.toString().trim()
        val notes = etManualNotes.text.toString().trim()

        if (title.isEmpty() || intervalStr.isEmpty()) {
            Toast.makeText(this, "Please enter a task title and interval", Toast.LENGTH_SHORT).show()
            return
        }

        val intervalDays = intervalStr.toIntOrNull() ?: 30
        val nextDueDate = calculateNextDueDate(intervalDays)

        val taskId = dbHelper.insertMaintenanceTask(receiptId, title, intervalDays, nextDueDate, notes)

        if (taskId != -1L) {
            scheduleMaintenanceAlarm(taskId, title, nextDueDate)
            Toast.makeText(this, "Task Scheduled!", Toast.LENGTH_SHORT).show()
            etTaskTitle.text.clear()
            etIntervalDays.text.clear()
            etManualNotes.text.clear()
            loadTasks()
        }
    }

    private fun loadTasks() {
        llTasksList.removeAllViews()
        val cursor = dbHelper.getTasksForReceipt(receiptId)

        while (cursor.moveToNext()) {
            val title = cursor.getString(cursor.getColumnIndexOrThrow("task_title"))
            val interval = cursor.getInt(cursor.getColumnIndexOrThrow("interval_days"))
            val dueDate = cursor.getString(cursor.getColumnIndexOrThrow("next_due_date"))
            val notes = cursor.getString(cursor.getColumnIndexOrThrow("manual_notes"))

            val taskCard = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(24, 24, 24, 24)
                setBackgroundColor(Color.parseColor("#F8FAFC"))
                val params = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                )
                params.setMargins(0, 0, 0, 16)
                layoutParams = params
            }

            val titleTv = TextView(this).apply {
                text = "📌 $title (Every $interval Days)"
                textSize = 15f
                setTextColor(Color.parseColor("#1E293B"))
            }

            val dueTv = TextView(this).apply {
                text = "Next Due: $dueDate"
                textSize = 13f
                setTextColor(Color.parseColor("#D97706"))
            }

            taskCard.addView(titleTv)
            taskCard.addView(dueTv)

            if (notes.isNotEmpty()) {
                val notesTv = TextView(this).apply {
                    text = "Notes: $notes"
                    textSize = 12f
                    setTextColor(Color.parseColor("#64748B"))
                }
                taskCard.addView(notesTv)
            }

            llTasksList.addView(taskCard)
        }
        cursor.close()
    }

    private fun calculateNextDueDate(days: Int): String {
        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        val cal = Calendar.getInstance().apply {
            add(Calendar.DAY_OF_YEAR, days)
        }
        return sdf.format(cal.time)
    }

    private fun scheduleMaintenanceAlarm(taskId: Long, taskTitle: String, dueDateStr: String) {
        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        val dueDate = sdf.parse(dueDateStr) ?: return
        val alarmManager = getSystemService(Context.ALARM_SERVICE) as AlarmManager

        val intent = Intent(this, AlarmReceiver::class.java).apply {
            putExtra("is_maintenance", true)
            putExtra("item_name", itemName)
            putExtra("task_title", taskTitle)
        }

        val pendingIntent = PendingIntent.getBroadcast(
            this,
            (taskId + 5000).toInt(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        alarmManager.set(AlarmManager.RTC_WAKEUP, dueDate.time, pendingIntent)
    }
}