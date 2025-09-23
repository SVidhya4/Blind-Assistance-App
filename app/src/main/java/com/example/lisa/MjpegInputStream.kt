package com.example.lisa

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import java.io.BufferedInputStream
import java.io.ByteArrayOutputStream

class MjpegInputStream(private val inputStream: BufferedInputStream) {

    fun readMjpegFrame(): Bitmap? {
        return try {
            val baos = ByteArrayOutputStream()
            var c: Int
            var contentStarted = false

            while (true) {
                c = inputStream.read()
                if (c == -1) break
                baos.write(c)

                // Look for JPEG start (0xFFD8)
                if (!contentStarted && baos.size() >= 2) {
                    val data = baos.toByteArray()
                    if (data[data.size - 2] == 0xFF.toByte() && data[data.size - 1] == 0xD8.toByte()) {
                        baos.reset()
                        baos.write(0xFF)
                        baos.write(0xD8)
                        contentStarted = true
                    }
                }

                // Look for JPEG end (0xFFD9)
                if (contentStarted && baos.size() >= 2) {
                    val data = baos.toByteArray()
                    if (data[data.size - 2] == 0xFF.toByte() && data[data.size - 1] == 0xD9.toByte()) {
                        val jpegData = baos.toByteArray()
                        return BitmapFactory.decodeByteArray(jpegData, 0, jpegData.size)
                    }
                }
            }
            null
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
}
