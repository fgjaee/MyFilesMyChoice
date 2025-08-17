package com.example.filemanagerredirect

import android.content.Context
import android.content.pm.PackageManager
import androidx.core.content.pm.PackageInfoCompat

object FileManagerCatalog {

    /**
     * Returns installed file managers from our giant arrays.xml list.
     * Falls back to returning the full list if none are detected.
     */
    fun installedOrAll(context: Context): List<String> {
        val pm = context.packageManager
        val all = context.resources.getStringArray(R.array.known_file_manager_packages).toList()

        val installed = all.filter { pkg ->
            try {
                val pi = if (android.os.Build.VERSION.SDK_INT >= 33) {
                    pm.getPackageInfo(pkg, PackageManager.PackageInfoFlags.of(0))
                } else {
                    @Suppress("DEPRECATION") pm.getPackageInfo(pkg, 0)
                }
                PackageInfoCompat.getLongVersionCode(pi) >= 0L
            } catch (_: Exception) {
                false
            }
        }

        return installed.ifEmpty { all }
    }
}