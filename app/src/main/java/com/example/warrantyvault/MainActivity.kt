package com.example.warrantyvault

import android.Manifest
import android.app.AlarmManager
import android.app.AlertDialog
import android.app.DatePickerDialog
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.view.LayoutInflater
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.content.ContextCompat
import com.google.android.material.switchmaterial.SwitchMaterial
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.Text
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

class MainActivity : AppCompatActivity() {

    private lateinit var dbHelper: DatabaseHelper
    private var selectedPurchaseDate: String = ""
    private var capturedImageFile: File? = null

    private lateinit var ivReceiptPreview: ImageView
    private lateinit var btnSelectDate: Button
    private lateinit var llReceiptContainer: LinearLayout

    private lateinit var etItemName: EditText
    private lateinit var etStoreName: EditText
    private lateinit var etPrice: EditText

    // Live countdown timer handler to refresh cards every minute
    private val countdownHandler = Handler(Looper.getMainLooper())
    private val countdownRunnable = object : Runnable {
        override fun run() {
            loadReceipts()
            countdownHandler.postDelayed(this, 60000)
        }
    }

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
                ivReceiptPreview.visibility = View.VISIBLE

                scanReceiptText(bitmap)
            }
        }
    }

    private val cameraPermissionLauncher = registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
        if (isGranted) launchCamera() else Toast.makeText(this, "Camera permission denied.", Toast.LENGTH_SHORT).show()
    }

    private val notificationPermissionLauncher = registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
        if (isGranted) Toast.makeText(this, "Notifications enabled!", Toast.LENGTH_SHORT).show()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        // Load theme preference before layout is inflated
        val sharedPref = getSharedPreferences("ThemePrefs", Context.MODE_PRIVATE)
        val isDarkMode = sharedPref.getBoolean("isDarkMode", false)
        if (isDarkMode) {
            AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES)
        } else {
            AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)
        }

        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        dbHelper = DatabaseHelper(this)

        val switchDarkMode = findViewById<SwitchMaterial>(R.id.switchDarkMode)
        switchDarkMode.isChecked = isDarkMode
        switchDarkMode.setOnCheckedChangeListener { _, isChecked ->
            sharedPref.edit().putBoolean("isDarkMode", isChecked).apply()
            if (isChecked) {
                AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES)
            } else {
                AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)
            }
        }

        etItemName = findViewById(R.id.etItemName)
        etStoreName = findViewById(R.id.etStoreName)
        val etCategory = findViewById<EditText>(R.id.etCategory)
        etPrice = findViewById(R.id.etPrice)
        val etDurationDays = findViewById<EditText>(R.id.etDurationDays)
        btnSelectDate = findViewById(R.id.btnSelectDate)
        val btnCapturePhoto = findViewById<Button>(R.id.btnCapturePhoto)
        val btnSaveReceipt = findViewById<Button>(R.id.btnSaveReceipt)
        val btnTestNotification = findViewById<Button>(R.id.btnTestNotification)
        ivReceiptPreview = findViewById(R.id.ivReceiptPreview)
        llReceiptContainer = findViewById(R.id.llReceiptContainer)

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

        btnCapturePhoto.setOnClickListener { checkAndLaunchCamera() }

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
                scheduleTieredWarrantyAlarms(name, calculatedExpiration, rowId.toInt())
                Toast.makeText(this, "Receipt Saved & Alarms Scheduled!", Toast.LENGTH_SHORT).show()
                clearForm(etItemName, etStoreName, etCategory, etPrice, etDurationDays)
                loadReceipts()
            }
        }

        loadReceipts()
    }

    override fun onResume() {
        super.onResume()
        countdownHandler.post(countdownRunnable) // Start live countdown ticking
    }

    override fun onPause() {
        super.onPause()
        countdownHandler.removeCallbacks(countdownRunnable) // Pause live countdown when app is backgrounded
    }

    private fun scanReceiptText(bitmap: Bitmap) {
        val image = InputImage.fromBitmap(bitmap, 0)
        val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)

        recognizer.process(image)
            .addOnSuccessListener { visionText: Text ->
                val textLines = visionText.text.split("\n").map { it.trim() }.filter { it.isNotEmpty() }
                if (textLines.isEmpty()) return@addOnSuccessListener

                val ignoreKeywords = listOf("receipt", "welcome", "tax", "invoice", "tel", "phone", "date", "cashier", "thank")
                val storeCandidate = textLines.take(6).firstOrNull { line ->
                    ignoreKeywords.none { line.lowercase().contains(it) } && line.length > 2 && !line.matches(Regex(""".*\d{3}-?\d{3}.*"""))
                }
                if (etStoreName.text.isEmpty() && storeCandidate != null) etStoreName.setText(storeCandidate)

                val itemCandidate = textLines.drop(1).take(5).firstOrNull { line ->
                    ignoreKeywords.none { line.lowercase().contains(it) } && line != storeCandidate
                }
                if (etItemName.text.isEmpty() && itemCandidate != null) etItemName.setText(itemCandidate)

                val totalLine = textLines.firstOrNull { it.uppercase().contains("TOTAL") || it.uppercase().contains("AMOUNT") }
                var extractedPrice: Double? = null
                if (totalLine != null) {
                    val priceMatch = Regex("""\d+[.,]\d{2}""").find(totalLine)
                    extractedPrice = priceMatch?.value?.replace(",", ".")?.toDoubleOrNull()
                }
                if (extractedPrice == null) {
                    extractedPrice = Regex("""\b\d+[.,]\d{2}\b""")
                        .findAll(visionText.text)
                        .mapNotNull { it.value.replace(",", ".").toDoubleOrNull() }
                        .filter { it < 5000.0 }
                        .maxOrNull()
                }
                if (extractedPrice != null && etPrice.text.isEmpty()) {
                    etPrice.setText(String.format(Locale.US, "%.2f", extractedPrice))
                }

                val dateMatch = Regex("""\b(\d{1,2}[/-]\d{1,2}[/-]\d{2,4}|\d{4}[/-]\d{1,2}[/-]\d{1,2})\b""").find(visionText.text)
                if (dateMatch != null && selectedPurchaseDate.isEmpty()) {
                    selectedPurchaseDate = dateMatch.value.replace('/', '-')
                    btnSelectDate.text = "Purchased: $selectedPurchaseDate"
                }
            }
    }

    private fun loadReceipts() {
        llReceiptContainer.removeAllViews()
        val cursor = dbHelper.getAllItems()

        if (cursor.count == 0) {
            val emptyTv = TextView(this).apply {
                text = "No saved receipts in your vault yet."
                setTextColor(Color.parseColor("#64748B"))
                textSize = 14f
                setPadding(16, 16, 16, 16)
            }
            llReceiptContainer.addView(emptyTv)
            cursor.close()
            return
        }

        val inflater = LayoutInflater.from(this)

        while (cursor.moveToNext()) {
            val name = cursor.getString(cursor.getColumnIndexOrThrow("item_name"))
            val store = cursor.getString(cursor.getColumnIndexOrThrow("store_name"))
            val price = cursor.getDouble(cursor.getColumnIndexOrThrow("price"))
            val expiry = cursor.getString(cursor.getColumnIndexOrThrow("calculated_expiration"))
            val status = cursor.getString(cursor.getColumnIndexOrThrow("status_flag"))
            val imagePath = cursor.getString(cursor.getColumnIndexOrThrow("image_path"))
            val category = cursor.getString(cursor.getColumnIndexOrThrow("category"))

            val cardView = inflater.inflate(R.layout.item_receipt_card, llReceiptContainer, false)

            val ivThumbnail = cardView.findViewById<ImageView>(R.id.ivCardThumbnail)
            val tvItemName = cardView.findViewById<TextView>(R.id.tvCardItemName)
            val tvSubDetails = cardView.findViewById<TextView>(R.id.tvCardSubDetails)
            val tvExpiry = cardView.findViewById<TextView>(R.id.tvCardExpiry)
            val tvPrice = cardView.findViewById<TextView>(R.id.tvCardPrice)
            val tvStatusPill = cardView.findViewById<TextView>(R.id.tvCardStatusPill)

            tvItemName.text = name
            tvSubDetails.text = if (category.isNotEmpty()) "$store • $category" else store
            tvExpiry.text = getRemainingTimeFormatted(expiry) // Live countdown formatting
            tvPrice.text = String.format(Locale.US, "$%.2f", price)

            val imageFile = File(imagePath)
            if (imagePath != "no_image" && imageFile.exists()) {
                ivThumbnail.setImageURI(android.net.Uri.fromFile(imageFile))
                ivThumbnail.clearColorFilter()
            } else {
                ivThumbnail.setImageResource(android.R.drawable.ic_menu_gallery)
                ivThumbnail.setColorFilter(Color.parseColor("#94A3B8"))
            }

            val isExpired = status == "Expired"
            val pillBg = GradientDrawable().apply {
                cornerRadius = 30f
                setColor(Color.parseColor(if (isExpired) "#FDE8E8" else "#DEF7EC"))
            }
            tvStatusPill.background = pillBg
            tvStatusPill.text = if (isExpired) "EXPIRED" else "ACTIVE"
            tvStatusPill.setTextColor(Color.parseColor(if (isExpired) "#9B1C1C" else "#03543F"))

            cardView.setOnClickListener {
                showReceiptDetailDialog(name, store, category, price, expiry, status, imagePath)
            }

            llReceiptContainer.addView(cardView)
        }
        cursor.close()
    }

    private fun showReceiptDetailDialog(
        name: String, store: String, category: String, price: Double,
        expiry: String, status: String, imagePath: String
    ) {
        val builder = AlertDialog.Builder(this)
        builder.setTitle("Receipt: $name")

        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(32, 32, 32, 32)
        }

        val detailsTv = TextView(this).apply {
            text = "Store: $store\nCategory: $category\nPrice: $$price\nExpires: $expiry\nStatus: $status"
            textSize = 15f
            setPadding(0, 0, 0, 24)
        }
        layout.addView(detailsTv)

        val imageFile = File(imagePath)
        if (imagePath != "no_image" && imageFile.exists()) {
            val imageView = ImageView(this).apply {
                setImageURI(android.net.Uri.fromFile(imageFile))
                adjustViewBounds = true
                maxHeight = 900
            }
            layout.addView(imageView)
        }

        builder.setView(layout)
        builder.setPositiveButton("Close") { dialog, _ -> dialog.dismiss() }
        builder.show()
    }

    private fun scheduleTestNotification(itemName: String) {
        val alarmManager = getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = Intent(this, AlarmReceiver::class.java).apply { putExtra("item_name", itemName) }
        val pendingIntent = PendingIntent.getBroadcast(this, 1001, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        alarmManager.set(AlarmManager.RTC_WAKEUP, System.currentTimeMillis() + 5000, pendingIntent)
        Toast.makeText(this, "Notification in 5 seconds!", Toast.LENGTH_SHORT).show()
    }

    private fun scheduleTieredWarrantyAlarms(itemName: String, expirationDateStr: String, baseReqCode: Int) {
        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        val expDate = sdf.parse(expirationDateStr) ?: return
        val alarmManager = getSystemService(Context.ALARM_SERVICE) as AlarmManager

        // Thresholds: 30 days before, 15 days before, 1 day before
        val thresholds = listOf(
            30 to (30L * 24 * 60 * 60 * 1000),
            15 to (15L * 24 * 60 * 60 * 1000),
            1 to (1L * 24 * 60 * 60 * 1000)
        )

        thresholds.forEachIndexed { index, (daysLeft, offsetMillis) ->
            val triggerTime = expDate.time - offsetMillis

            if (triggerTime > System.currentTimeMillis()) {
                val intent = Intent(this, AlarmReceiver::class.java).apply {
                    putExtra("item_name", itemName)
                    putExtra("days_left", daysLeft)
                }

                val requestCode = (baseReqCode * 100) + index
                val pendingIntent = PendingIntent.getBroadcast(
                    this,
                    requestCode,
                    intent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )

                alarmManager.set(AlarmManager.RTC_WAKEUP, triggerTime, pendingIntent)
            }
        }
    }

    private fun getRemainingTimeFormatted(expirationDateStr: String): String {
        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        val expDate = sdf.parse(expirationDateStr) ?: return "Invalid Date"

        val diffMillis = expDate.time - System.currentTimeMillis()
        if (diffMillis <= 0) return "⚠️ Expired"

        val days = diffMillis / (1000 * 60 * 60 * 24)
        val hours = (diffMillis / (1000 * 60 * 60)) % 24
        val minutes = (diffMillis / (1000 * 60)) % 60

        return "⏳ ${days}d ${hours}h ${minutes}m remaining"
    }

    private fun checkAndLaunchCamera() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
            launchCamera()
        } else {
            cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    private fun launchCamera() {
        cameraLauncher.launch(Intent(MediaStore.ACTION_IMAGE_CAPTURE))
    }

    private fun calculateExpirationDate(purchaseDateStr: String, durationDays: Int): String {
        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        val date = sdf.parse(purchaseDateStr) ?: Date()
        val calendar = Calendar.getInstance().apply {
            time = date
            add(Calendar.DAY_OF_YEAR, durationDays)
        }
        return sdf.format(calendar.time)
    }

    private fun evaluateStatus(expirationDateStr: String): String {
        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        val expDate = sdf.parse(expirationDateStr) ?: Date()
        return if (expDate.before(Date())) "Expired" else "Active"
    }

    private fun clearForm(vararg inputs: EditText) {
        inputs.forEach { it.text.clear() }
        btnSelectDate.text = "Select Purchase Date"
        selectedPurchaseDate = ""
        capturedImageFile = null
        ivReceiptPreview.setImageDrawable(null)
        ivReceiptPreview.visibility = View.GONE
    }
}