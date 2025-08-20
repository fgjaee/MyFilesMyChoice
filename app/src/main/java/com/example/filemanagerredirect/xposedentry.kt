package com.example.filemanagerredirect

import android.app.AndroidAppHelper
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.util.Log
import de.robv.android.xposed.IXposedHookLoadPackage
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.XposedHelpers
import de.robv.android.xposed.callbacks.XC_LoadPackage

/**
 * LSPosed entrypoint.
 * Hooks PackageManager/Intent resolution to redirect file picker calls.
 */
class XposedEntry : IXposedHookLoadPackage {

    companion object {
        private const val TAG = "XposedEntry"
    }

    override fun handleLoadPackage(lpparam: XC_LoadPackage.LoadPackageParam) {
        // Don’t hook ourselves
        if (lpparam.packageName == "com.example.filemanagerredirect") return

        try {
            val ctx = AndroidAppHelper.currentApplication()?.applicationContext ?: return
            val targetPkg = FileManagerRedirectHook.resolveTargetPackage(ctx) ?: return

            // Hook PackageManager.resolveActivity() to intercept file picker intents
            val pmClass = XposedHelpers.findClass(
                "android.app.ApplicationPackageManager",
                lpparam.classLoader
            )

            XposedHelpers.findAndHookMethod(
                pmClass,
                "resolveActivity",
                Intent::class.java,
                Int::class.javaPrimitiveType,
                object : XC_MethodHook() {
                    override fun beforeHookedMethod(param: MethodHookParam) {
                        val origIntent = param.args[0] as? Intent ?: return
                        if (isFilePickerIntent(origIntent)) {
                            Log.d(TAG, "Redirecting file picker to $targetPkg")
                            origIntent.setPackage(targetPkg)
                            param.args[0] = origIntent
                        }
                    }
                }
            )

            XposedBridge.log("$TAG: Hook initialized for ${lpparam.packageName}, redirect → $targetPkg")
        } catch (t: Throwable) {
            XposedBridge.log("$TAG error: ${t.message}")
        }
    }

    /**
     * Detects whether an Intent looks like a file picker.
     */
    private fun isFilePickerIntent(intent: Intent): Boolean {
        val act = intent.action ?: return false
        val type = intent.type ?: ""

        // Document/file intents
        if (act == Intent.ACTION_GET_CONTENT || act == Intent.ACTION_OPEN_DOCUMENT) {
            return true
        }
        // Mime filters
        if (type.startsWith("image/") || type.startsWith("video/") || type.startsWith("audio/") || type == "*/*") {
            return true
        }
        return false
    }
}