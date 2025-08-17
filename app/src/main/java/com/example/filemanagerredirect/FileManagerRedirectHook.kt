package com.example.filemanagerredirect

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import com.example.filemanagerredirect.pm.getPackageInfoCompat

/**
 * Chooses which app should receive a file-picker intent.
 * No LSPosed classes are referenced; works in a plain app build.
 */
object FileManagerRedirectHook {

    /**
     * Returns a copy of [original] targeted at the best file manager package,
     * or null if no redirect should occur (caller can show chooser/system picker).
     */
    fun pickBestHandler(context: Context, original: Intent): Intent? {
        val prefs = try {
            // handle direct-boot/device-protected context if available
            val de = context.createDeviceProtectedStorageContext()
            de.getSharedPreferences(Prefs.PREFS_NAME, Context.MODE_PRIVATE)
        } catch (_: Throwable) {
            context.getSharedPreferences(Prefs.PREFS_NAME, Context.MODE_PRIVATE)
        }

        // Master toggle
        val enabled = prefs.getBoolean(Prefs.KEY_ENABLED, true)
        if (!enabled) return null

        val selected = prefs.getString(Prefs.KEY_PACKAGE, Prefs.PKG_AUTO) ?: Prefs.PKG_AUTO
        val stealth = prefs.getBoolean(Prefs.KEY_STEALTH, false)
        val fallbackSaf = prefs.getBoolean(Prefs.KEY_FALLBACK_SAF, true)

        val pm: PackageManager = context.packageManager

        // Known packages come from resources so we don't need a hardcoded PACKAGES constant.
        val known = try {
            context.resources.getStringArray(R.array.known_file_manager_packages).toList()
        } catch (_: Throwable) {
            emptyList()
        }

        // Build the candidate list:
        // 1) If a specific package is selected, use that.
        // 2) Otherwise, use the first installed package from our known list.
        val candidatePackages: List<String> = if (selected.isNotEmpty()) {
            listOf(selected)
        } else {
            known.filter { pkg -> pm.getPackageInfoCompat(pkg) != null }
        }

        val targetPkg = candidatePackages.firstOrNull()
            ?: return if (fallbackSaf) null else null // nothing installed, let system handle it

        // Create a direct intent to the chosen package.
        return Intent(original).apply {
            `package` = targetPkg
            selector = null
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }.also {
            // If you later want a NON-stealth option, your caller can wrap this in a chooser.
            // Here we just return the direct intent; "stealth" only influences the caller behavior.
            // (No additional code needed here for stealth=true.)
        }
    }
}