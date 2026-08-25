package com.example.warrantyvault

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream

object ImageCompressor {
    // Compresses high-res photo receipts to ~300KB JPEGs to reduce storage overhead
    fun compressImage(imageFile: File): File {
        val bitmap = BitmapFactory.decodeFile(imageFile.absolutePath) ?: return imageFile
        val outputStream = ByteArrayOutputStream()

        // Compress using 70% JPEG quality target
        bitmap.compress(Bitmap.CompressFormat.JPEG, 70, outputStream)

        val fileOutputStream = FileOutputStream(imageFile)
        fileOutputStream.write(outputStream.toByteArray())
        fileOutputStream.flush()
        fileOutputStream.close()

        return imageFile
    }
}