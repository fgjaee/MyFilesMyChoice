package com.example.filemanagerredirect

import android.content.Intent
import android.os.Bundle
import android.os.IBinder
import de.robv.android.xposed.IXposedHookLoadPackage
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XSharedPreferences
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.XposedHelpers
import de.robv.android.xposed.callbacks.XC_LoadPackage

/**
 * LSPosed/Xposed entry point. Only hooks the system framework ("android") process.
 * Reads user prefs via XSharedPreferences and forces file-picker intents to a target package.
 */
class XposedEntry : IXposedHookLoadPackage {

    override fun handleLoadPackage(lpparam: XC_LoadPackage.LoadPackageParam) {
        if (lpparam.packageName != "android") return

        try {
            // Try the common ActivityTaskManagerService.startActivityAsUser signature.
            hookStartActivityAsUser(lpparam)
            XposedBridge.log("FilePickerReDirekt: hook installed in system_server")
        } catch (t: Throwable) {
            XposedBridge.log("FilePickerReDirekt: hook failed: ${t.message}")
        }
    }

    private fun hookStartActivityAsUser(lpparam: XC_LoadPackage.LoadPackageParam) {
        XposedHelpers.findAndHookMethod(
            "com.android.server.wm.ActivityTaskManagerService",
            lpparam.classLoader,
            "startActivityAsUser",
            Class.forName("android.app.IApplicationThread"),
            String::class.java,
            Intent::class.java,
            String::class.java,
            IBinder::class.java,
            String::class.java,
            Int::class.javaPrimitiveType,    // requestCode
            Int::class.javaPrimitiveType,    // startFlags
            Class.forName("android.app.ProfilerInfo"),
            Bundle::class.java,
            Int::class.javaPrimitiveType,    // userId
            object : XC_MethodHook() {
                override fun beforeHookedMethod(param: MethodHookParam) {
                    val intent = param.args[2] as? Intent ?: return
                    val action = intent.action ?: return

                    val isPicker = action == Intent.ACTION_GET_CONTENT ||
                            action == Intent.ACTION_OPEN_DOCUMENT ||
                            action == Intent.ACTION_OPEN_DOCUMENT_TREE ||
                            action == Intent.ACTION_CREATE_DOCUMENT ||
                            action == Intent.ACTION_PICK
                    if (!isPicker) return

                    // Don’t redirect if already targeted or chooser
                    if (intent.component?.className == "com.android.internal.app.ChooserActivity") return

                    val target = readTargetPackageFromPrefs() ?: return
                    if (intent.`package` == target) return

                    // Set the explicit package; if not installed, system will ignore and continue resolution
                    intent.setPackage(target)
                    XposedBridge.log("FilePickerReDirekt: redirected $action to $target")
                }
            }
        )
    }

    /**
     * Read user settings from the app's SharedPreferences using XSharedPreferences.
     * Requires the prefs XML to be readable by system_server (see step 3).
     */
    private fun readTargetPackageFromPrefs(): String? {
        val sp = XSharedPreferences("com.example.filemanagerredirect", Prefs.PREFS_NAME)
        // On some ROMs you need this; harmless otherwise:
        sp.makeWorldReadable()

        val enabled = sp.getBoolean(Prefs.KEY_ENABLED, true)
        if (!enabled) return null

        val selected = sp.getString(Prefs.KEY_PRIMARY_MANAGER, "auto") ?: "auto"

        // If the app wrote a resolved package for AUTO, prefer it.
        val resolved = sp.getString(Prefs.KEY_RESOLVED_TARGET, null)

        return when {
            selected == "auto" && !resolved.isNullOrBlank() -> resolved
            selected == "auto" -> DEFAULT_CANDIDATES.firstOrNull() ?: "com.android.documentsui"
            else -> selected
        }
    }

    companion object {
        // Conservative ordered defaults (if the app hasn’t pre-resolved AUTO).
        private val DEFAULT_CANDIDATES = listOf(
            "com.mixplorer",
            "com.mixplorer.silver",
            "bin.mt.plus",
            "pl.solidexplorer2",
            "nextapp.fx",
            "com.speedsoftware.rootexplorer",
            "com.lonelycatgames.Xplore",
            "com.google.android.apps.nbu.files",
            "com.android.documentsui"
        )
    }
}