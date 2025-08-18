package com.example.filemanagerredirect

import android.content.Context
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.content.pm.VersionedPackage
import android.os.Build
import android.util.Log

/**
 * Central helper for deciding which file manager package to use.
 * - Reads user prefs (enabled flag, primary choice).
 * - If set to "auto", picks the best installed manager from our known list.
 */
object FileManagerRedirectHook {

    private const val TAG = "FMRedirectHook"
    private const val PKG_AUTO = "auto"
    private const val FALLBACK_SYSTEM = "com.android.documentsui"

    /**
     * Returns the package name of the chosen file manager, or null/empty if redirect is disabled.
     */
    fun resolveTargetPackage(ctx: Context): String? {
        val sp = try {
            val de = ctx.createDeviceProtectedStorageContext()
            de.getSharedPreferences(Prefs.PREFS_NAME, Context.MODE_PRIVATE)
        } catch (_: Throwable) {
            ctx.getSharedPreferences(Prefs.PREFS_NAME, Context.MODE_PRIVATE)
        }

        val enabled = sp.getBoolean(Prefs.KEY_ENABLED, true)
        if (!enabled) {
            Log.d(TAG, "Redirect is disabled in prefs")
            return null
        }

        val selected = sp.getString(Prefs.KEY_PRIMARY_MANAGER, PKG_AUTO) ?: PKG_AUTO
        if (selected != PKG_AUTO) {
            Log.d(TAG, "Using user-selected manager: $selected")
            return selected
        }

        // Auto mode: load the known manager list from resources and filter to those installed.
        val known = try {
            ctx.resources.getStringArray(R.array.known_file_manager_packages).toList()
        } catch (_: Throwable) {
            emptyList()
        }

        val installed = filterInstalled(ctx.packageManager, known)
        val best = chooseBest(installed) ?: FALLBACK_SYSTEM
        Log.d(TAG, "Auto-selected manager: $best (installed=${installed.size})")
        return best
    }

    /**
     * Returns installed packages from the given list.
     */
    private fun filterInstalled(pm: PackageManager, candidates: List<String>): List<String> {
        if (candidates.isEmpty()) return emptyList()
        return candidates.filter { pkg ->
            pm.getPackageInfoCompat(pkg) != null
        }
    }

    /**
     * Pick a "best" package from installed ones using a priority list, else first available.
     */
    private fun chooseBest(installed: List<String>): String? {
        if (installed.isEmpty()) return null

        val priority = listOf(
            // Popular power users / root-capable first
            "com.mixplorer",
            "com.mixplorer.silver",
            "bin.mt.plus",
            "bin.mt.plus.canary",
            "pl.solidexplorer2",
            "nextapp.fx",
            "com.speedsoftware.rootexplorer",
            "com.lonelycatgames.Xplore",
            // Stock / Google file apps next
            "com.google.android.apps.nbu.files",
            "com.android.documentsui",
        )

        priority.firstOrNull { it in installed }?.let { return it }
        return installed.firstOrNull()
    }
}

/* ------------------------------ Compat helpers ------------------------------ */

/**
 * Safe wrapper for getPackageInfo across API levels.
 * Returns null if the package isn't found or any error occurs.
 */
private fun PackageManager.getPackageInfoCompat(
    packageName: String,
    flags: Int = 0
): PackageInfo? {
    return try {
        if (Build.VERSION.SDK_INT >= 33) {
            // Tiramisu+ API
            getPackageInfo(
                VersionedPackage(packageName, 0),
                PackageManager.PackageInfoFlags.of(flags.toLong())
            )
        } else {
            @Suppress("DEPRECATION")
            getPackageInfo(packageName, flags)
        }
    } catch (_: Throwable) {
        null
    }
}