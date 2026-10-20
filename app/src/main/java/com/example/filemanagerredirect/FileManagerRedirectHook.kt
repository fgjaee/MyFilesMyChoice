package com.example.filemanagerredirect

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build

/** Resolves the preferred installed manager for GET_CONTENT requests. */
object FileManagerRedirectHook {
    fun resolveTargetPackage(ctx: Context): String? {
        val prefs = ctx.getSharedPreferences(Prefs.PREFS_NAME, Context.MODE_PRIVATE)
        if (!prefs.getBoolean(Prefs.KEY_ENABLED, true)) return null
        val selected = Prefs.normalizeSelected(
            prefs.getString(Prefs.KEY_PRIMARY_MANAGER, Prefs.PKG_AUTO))
        if (selected != Prefs.PKG_AUTO) return selected

        val priority = listOf(
            "com.mixplorer", "com.mixplorer.silver", "bin.mt.plus", "bin.mt.plus.canary",
            "pl.solidexplorer2", "nextapp.fx", "com.lonelycatgames.Xplore",
            "com.ghisler.android.TotalCommander", "me.zhanghai.android.files"
        )
        val known = ctx.resources.getStringArray(R.array.known_file_manager_packages)
        return (priority + known).distinct()
            .firstOrNull { supportsGetContent(ctx.packageManager, it) }
    }

    private fun supportsGetContent(pm: PackageManager, pkg: String): Boolean {
        val req = Intent(Intent.ACTION_GET_CONTENT).apply {
            type = "*/*"
            addCategory(Intent.CATEGORY_OPENABLE)
            setPackage(pkg)
        }
        return try {
            if (Build.VERSION.SDK_INT >= 33) {
                pm.resolveActivity(req, PackageManager.ResolveInfoFlags.of(0)) != null
            } else {
                @Suppress("DEPRECATION")
                pm.resolveActivity(req, 0) != null
            }
        } catch (_: Exception) { false }
    }
}
