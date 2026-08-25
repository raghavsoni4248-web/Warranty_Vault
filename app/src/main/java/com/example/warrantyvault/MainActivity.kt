package com.example.warrantyvault

import android.Manifest
import android.app.AlarmManager
import android.app.DatePickerDialog
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

class MainActivity : ComponentActivity() {

    private lateinit var dbHelper: DatabaseHelper
    private var selectedPurchaseDate: String = ""
    private var capturedImageFile: File? = null

    private lateinit var ivReceiptPreview: ImageView
    private lateinit var btnSelectDate: Button
    private lateinit var tvReceiptList: TextView

    private val cameraLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == RESULT_OK) {
            val bitmap = result.data?.extras?.get("data") as? Bitmap
            if (bitmap != null) {
                val file = File(filesDir, "receipt_${System.currentTimeMillis()}.jpg")
                val out = FileOutputStream(file)
                bitmap.compress(Bitmap.CompressFormat.JPEG, 100, out)
                out.flush()
                out.close()

                val compressedFile = ImageCompressor.compressImage(file)
                capturedImageFile = compressedFile
                ivReceiptPreview.setImageURI(android.net.Uri.fromFile(compressedFile))
                Toast.makeText(this, "Receipt photo captured & compressed!", Toast.LENGTH_SHORT).show()
            }
        }
    }

    // Dedicated Launcher for Camera Permission
    private val cameraPermissionLauncher = registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
        if (isGranted) {
            launchCamera()
        } else {
            Toast.makeText(this, "Camera permission denied.", Toast.LENGTH_SHORT).show()
        }
    }

    // Dedicated Launcher for Notification Permission (Prevents launching camera on grant)
    private val notificationPermissionLauncher = registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
        if (isGranted) {
            Toast.makeText(this, "Notifications enabled!", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(this, "Notification permission denied.", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        dbHelper = DatabaseHelper(this)

        val etItemName = findViewById<EditText>(R.id.etItemName)
        val etStoreName = findViewById<EditText>(R.id.etStoreName)
        val etCategory = findViewById<EditText>(R.id.etCategory)
        val etPrice = findViewById<EditText>(R.id.etPrice)
        val etDurationDays = findViewById<EditText>(R.id.etDurationDays)
        btnSelectDate = findViewById(R.id.btnSelectDate)
        val btnCapturePhoto = findViewById<Button>(R.id.btnCapturePhoto)
        val btnSaveReceipt = findViewById<Button>(R.id.btnSaveReceipt)
        val btnTestNotification = findViewById<Button>(R.id.btnTestNotification)
        ivReceiptPreview = findViewById(R.id.ivReceiptPreview)
        tvReceiptList = findViewById(R.id.tvReceiptList)

        // Request notification permission safely for Android 13+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }

        btnSelectDate.setOnClickListener {
            val calendar = Calendar.getInstance()
            DatePickerDialog(
                this,
                { _, year, month, dayOfMonth ->
                    selectedPurchaseDate = String.format(Locale.US, "%04d-%02d-%02d", year, month + 1, dayOfMonth)
                    btnSelectDate.text = "Purchased: $selectedPurchaseDate"
                },
                calendar.get(Calendar.YEAR),
                calendar.get(Calendar.MONTH),
                calendar.get(Calendar.DAY_OF_MONTH)
            ).show()
        }

        btnCapturePhoto.setOnClickListener {
            checkAndLaunchCamera()
        }

        // Test push notification scheduled for 5 seconds in the future
        btnTestNotification.setOnClickListener {
            val name = etItemName.text.toString().ifEmpty { "Sample Receipt Item" }
            scheduleTestNotification(name)
        }

        btnSaveReceipt.setOnClickListener {
            val name = etItemName.text.toString().trim()
            val store = etStoreName.text.toString().trim()
            val category = etCategory.text.toString().trim()
            val priceStr = etPrice.text.toString().trim()
            val durationStr = etDurationDays.text.toString().trim()

            if (name.isEmpty() || store.isEmpty() || priceStr.isEmpty() || durationStr.isEmpty() || selectedPurchaseDate.isEmpty()) {
                Toast.makeText(this, "Please fill in all form fields and select a date", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val price = priceStr.toDoubleOrNull() ?: 0.0
            val durationDays = durationStr.toIntOrNull() ?: 0
            val imagePath = capturedImageFile?.absolutePath ?: "no_image"

            val calculatedExpiration = calculateExpirationDate(selectedPurchaseDate, durationDays)
            val statusFlag = evaluateStatus(calculatedExpiration)

            val rowId = dbHelper.insertItem(
                itemName = name,
                storeName = store,
                category = category,
                price = price,
                purchaseDate = selectedPurchaseDate,
                durationDays = durationDays,
                calculatedExpiration = calculatedExpiration,
                imagePath = imagePath,
                statusFlag = statusFlag,
                notificationIds = "alarm_${System.currentTimeMillis()}"
            )

            if (rowId != -1L) {
                scheduleWarrantyAlarm(name, calculatedExpiration, rowId.toInt())
                Toast.makeText(this, "Receipt Saved!", Toast.LENGTH_SHORT).show()
                clearForm(etItemName, etStoreName, etCategory, etPrice, etDurationDays)
                loadReceipts()
            }
        }

        loadReceipts()
    }

    private fun scheduleTestNotification(itemName: String) {
        val alarmManager = getSystemService(Context.ALARM_SERVICE) as AlarmManager

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (!alarmManager.canScheduleExactAlarms()) {
                val intent = Intent(android.provider.Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM)
                startActivity(intent)
                Toast.makeText(this, "Please enable 'Alarms & reminders' permission in Settings.", Toast.LENGTH_LONG).show()
                return
            }
        }

        val intent = Intent(this, AlarmReceiver::class.java).apply {
            putExtra("item_name", itemName)
        }

        val pendingIntent = PendingIntent.getBroadcast(
            this,
            1001,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val triggerTime = System.currentTimeMillis() + 5000

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerTime, pendingIntent)
            } else {
                alarmManager.setExact(AlarmManager.RTC_WAKEUP, triggerTime, pendingIntent)
            }
            Toast.makeText(this, "Notification scheduled in 5 seconds!", Toast.LENGTH_LONG).show()
        } catch (e: SecurityException) {
            alarmManager.set(AlarmManager.RTC_WAKEUP, triggerTime, pendingIntent)
            Toast.makeText(this, "Scheduled test alarm in 5 seconds.", Toast.LENGTH_LONG).show()
        }
    }

    private fun scheduleWarrantyAlarm(itemName: String, expirationDateStr: String, reqCode: Int) {
        val alarmManager = getSystemService(Context.ALARM_SERVICE) as AlarmManager

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (!alarmManager.canScheduleExactAlarms()) {
                val intent = Intent(android.provider.Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM)
                startActivity(intent)
                Toast.makeText(this, "Please grant 'Alarms & reminders' permission in Settings.", Toast.LENGTH_LONG).show()
                return
            }
        }

        val triggerTime = System.currentTimeMillis() + (2 * 60 * 1000) // 2 minutes from now

        val intent = Intent(this, AlarmReceiver::class.java).apply {
            putExtra("item_name", itemName)
        }

        val pendingIntent = PendingIntent.getBroadcast(
            this,
            reqCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerTime, pendingIntent)
            } else {
                alarmManager.setExact(AlarmManager.RTC_WAKEUP, triggerTime, pendingIntent)
            }
            val formattedTime = SimpleDateFormat("hh:mm:ss a", Locale.US).format(Date(triggerTime))
            Toast.makeText(this, "Alarm set to ring in 2 minutes (at $formattedTime)!", Toast.LENGTH_LONG).show()
        } catch (e: SecurityException) {
            alarmManager.set(AlarmManager.RTC_WAKEUP, triggerTime, pendingIntent)
            Toast.makeText(this, "Alarm scheduled in 2 minutes.", Toast.LENGTH_LONG).show()
        }
    }

    private fun checkAndLaunchCamera() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
            launchCamera()
        } else {
            cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    private fun launchCamera() {
        val cameraIntent = Intent(MediaStore.ACTION_IMAGE_CAPTURE)
        cameraLauncher.launch(cameraIntent)
    }

    private fun calculateExpirationDate(purchaseDateStr: String, durationDays: Int): String {
        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        val date = sdf.parse(purchaseDateStr) ?: Date()
        val calendar = Calendar.getInstance()
        calendar.time = date
        calendar.add(Calendar.DAY_OF_YEAR, durationDays)
        return sdf.format(calendar.time)
    }

    private fun evaluateStatus(expirationDateStr: String): String {
        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        val expDate = sdf.parse(expirationDateStr) ?: Date()
        val today = Date()

        return if (expDate.before(today)) {
            "Expired"
        } else {
            "Active"
        }
    }

    private fun clearForm(vararg inputs: EditText) {
        inputs.forEach { it.text.clear() }
        btnSelectDate.text = "Select Purchase Date"
        selectedPurchaseDate = ""
        capturedImageFile = null
        ivReceiptPreview.setImageDrawable(null)
    }

    private fun loadReceipts() {
        val cursor = dbHelper.getAllItems()
        val builder = StringBuilder()

        while (cursor.moveToNext()) {
            val nameIndex = cursor.getColumnIndex("item_name")
            val storeIndex = cursor.getColumnIndex("store_name")
            val priceIndex = cursor.getColumnIndex("price")
            val expiryIndex = cursor.getColumnIndex("calculated_expiration")
            val statusIndex = cursor.getColumnIndex("status_flag")

            if (nameIndex != -1 && storeIndex != -1 && expiryIndex != -1 && statusIndex != -1 && priceIndex != -1) {
                val name = cursor.getString(nameIndex)
                val store = cursor.getString(storeIndex)
                val price = cursor.getDouble(priceIndex)
                val expiry = cursor.getString(expiryIndex)
                val status = cursor.getString(statusIndex)

                builder.append("• $name ($store) - $$price\n  Expires: $expiry [$status]\n\n")
            }
        }
        cursor.close()

        tvReceiptList.text = if (builder.isEmpty()) "No receipts stored yet in SQLite." else builder.toString()
    }
}