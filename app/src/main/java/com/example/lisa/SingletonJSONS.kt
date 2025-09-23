// SingletonJSONS.kt
package com.example.lisa

import android.content.Context
import android.util.Log
import org.json.JSONObject
import java.io.InputStream

object SingletonJSONS {
    private const val TAG = "SingletonJSONS"

    // public cached JSON objects
    var w_to_i_jObj: JSONObject? = null
        private set
    var i_to_w_jObj: JSONObject? = null
        private set

    private var initialized = false

    /**
     * Initialize once. Must be called with applicationContext.
     * Safe to call multiple times; subsequent calls are no-ops.
     */
    fun init(context: Context) {
        if (initialized) return
        try {
            w_to_i_jObj = loadJson(context, "w_to_i.json")
            i_to_w_jObj = loadJson(context, "i_to_w.json")
            initialized = true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load JSON assets: ${e.message}")
        }
    }

    private fun loadJson(context: Context, assetName: String): JSONObject? {
        return try {
            val stream: InputStream = context.assets.open(assetName)
            val bytes = stream.readBytes()
            stream.close()
            JSONObject(String(bytes))
        } catch (e: Exception) {
            Log.e(TAG, "loadJson error for $assetName: ${e.message}")
            null
        }
    }
}
