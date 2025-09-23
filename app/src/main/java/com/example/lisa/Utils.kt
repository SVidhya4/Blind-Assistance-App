package com.example.lisa

import java.io.BufferedReader
import java.io.InputStream
import java.io.InputStreamReader

object Utils {
    fun loadJSONFromAsset(inputStream: InputStream): String {
        val builder = StringBuilder()
        val reader = BufferedReader(InputStreamReader(inputStream, Charsets.UTF_8))
        var line: String? = reader.readLine()
        while (line != null) {
            builder.append(line)
            line = reader.readLine()
        }
        reader.close()
        return builder.toString()
    }
}
