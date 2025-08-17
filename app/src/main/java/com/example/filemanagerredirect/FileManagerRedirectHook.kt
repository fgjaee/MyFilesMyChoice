package com.example.filemanagerredirect

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import com.example.filemanagerredirect.pm.getPackageInfoCompat

/**
 * Simple “hook” helper the app can call to decide which file manager package
 * should handle a file-picker style intent.
 *
 * This version does NOT depend on any Xposed/LSPosed APIs, so it compiles in a
 * normal app build. If you later wire it into LSPosed, you can reuse the same
 * decision logic.
 */
object FileManagerRedirectHook {

    /**
     * Returns a copy of [original] that targets the best file manager package,
     * or null if redirect should not occur.
     */
    fun pickBestHandler(context: Context, original: Intent): Intent? {
        val prefs = context.getSharedPreferences(Keys.PREFS, Context.MODE_PRIVATE)

        // Master toggle
        val enabled = prefs.getBoolean(Keys.KEY_ENABLED, true)
        if (!enabled) return null

        val selected = prefs.getString(Keys.KEY_PACKAGE, PKG_AUTO) ?: PKG_AUTO
        val stealth = prefs.getBoolean(Keys.KEY_STEALTH, false)
        val fallbackSaf = prefs.getBoolean(Keys.KEY_FALLBACK_SAF, true)

        val pm: PackageManager = context.packageManager

        // If user picked a specific package, prefer it; otherwise scan our PACKAGES.
        val candidatePackages: List<String> =
            if (selected.isNotEmpty()) {
                listOf(selected)
            } else {
                PACKAGES.filter { pkg -> pm.getPackageInfoCompat(pkg, 0) != null }
            }

        // Nothing installed we know about → either fall back (return null) or keep chooser
        val targetPkg = candidatePackages.firstOrNull()
            ?: return if (fallbackSaf) null else null

        // Build the redirected intent; keep categories/extras from original
        val redirected = Intent(original).apply {
            // Direct target
            `package` = targetPkg
            // Remove any selector to avoid being overridden
            selector = null
            // Ensure new task if launching from background contexts
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }

        // If "stealth" is on, we avoid showing a chooser at all by returning a direct intent.
        // If not stealth, the caller (e.g., RedirectActivity) can still wrap this in a chooser.
        return redirected
    }
}