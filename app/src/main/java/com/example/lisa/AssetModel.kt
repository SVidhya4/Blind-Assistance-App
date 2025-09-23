package com.example.lisa

import android.content.Context
import java.io.File
import java.io.FileOutputStream

object AssetModelUtil {
    fun copyAssetModel(context: Context, assetModelName: String): File {
        val outDir = File(context.filesDir, assetModelName)
        if (outDir.exists()) return outDir

        outDir.mkdirs()
        val assetManager = context.assets
        val files = assetManager.list(assetModelName) ?: return outDir

        for (file in files) {
            val inStream = assetManager.open("$assetModelName/$file")
            val outFile = File(outDir, file)
            val outStream = FileOutputStream(outFile)
            inStream.copyTo(outStream)
            inStream.close()
            outStream.close()
        }

        return outDir
    }
}
