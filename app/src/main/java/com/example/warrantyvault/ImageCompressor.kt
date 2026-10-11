package com.example.warrantyvault

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import java.io.File
import java.io.FileOutputStream

object ImageCompressor {
    fun compressImage(imageFile: File, maxWidth: Int = 1024, maxHeight: Int = 1024, quality: Int = 80): File {
        val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(imageFile.absolutePath, options)

        var actualWidth = options.outWidth
        var actualHeight = options.outHeight

        var scaleFactor = 1.0f
        val maxDim = maxWidth.toFloat()

        if (actualWidth > maxDim || actualHeight > maxDim) {
            val widthRatio = actualWidth / maxDim
            val heightRatio = actualHeight / maxDim
            scaleFactor = Math.max(widthRatio, heightRatio)
        }

        options.inJustDecodeBounds = false
        options.inSampleSize = scaleFactor.toInt().coerceAtLeast(1)

        val scaledBitmap = BitmapFactory.decodeFile(imageFile.absolutePath, options)
        val compressedFile = File(imageFile.parent, "compressed_${System.currentTimeMillis()}.jpg")
        val out = FileOutputStream(compressedFile)
        scaledBitmap.compress(Bitmap.CompressFormat.JPEG, quality, out)
        out.flush()
        out.close()

        return compressedFile
    }
}