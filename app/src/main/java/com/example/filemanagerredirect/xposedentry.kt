package com.example.filemanagerredirect

import android.app.Activity
import android.content.ComponentName
import android.content.Intent
import android.os.Build
import de.robv.android.xposed.IXposedHookLoadPackage
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.XposedHelpers
import de.robv.android.xposed.callbacks.XC_LoadPackage

class XposedEntry : IXposedHookLoadPackage {

    // Target picker packages only
    private val pickerPkgs = setOf(
        "com.android.documentsui",
        "com.sec.android.app.myfiles",
        "com.mi.android.globalFileexplorer",
        "com.coloros.filemanager",
        "com.huawei.hidisk"
    )

    // MiXplorer component; adjust if your variant differs
    private val mixClassName = "com.mixplorer.activities.BrowseActivity"
    private val mixPkg = "com.mixplorer"

    override fun handleLoadPackage(lpparam: XC_LoadPackage.LoadPackageParam) {
        if (!pickerPkgs.contains(lpparam.packageName)) return

        try {
            // Hook Activity.onCreate for ALL activities in the picker process; we’ll early-exit after first redirect
            XposedHelpers.findAndHookMethod(
                Activity::class.java, "onCreate", android.os.Bundle::class.java,
                object : XC_MethodHook() {
                    override fun beforeHookedMethod(param: MethodHookParam) {
                        val act = param.thisObject as Activity
                        // Don’t loop if we’re already inside MiXplorer or finishing
                        if (act.packageName == mixPkg || act.isFinishing) return

                        // Only react to SAF-style actions
                        val a = act.intent?.action ?: return
                        val isSaf = a == Intent.ACTION_OPEN_DOCUMENT
                                || a == Intent.ACTION_OPEN_DOCUMENT_TREE
                                || a == Intent.ACTION_CREATE_DOCUMENT
                                || a == Intent.ACTION_GET_CONTENT
                        if (!isSaf) return

                        // Build a best-effort MiXplorer intent mirroring the request
                        val mix = Intent(act.intent).apply {
                            component = ComponentName(mixPkg, mixClassName)
                            // Many apps require CATEGORY_OPENABLE; preserve flags & type
                            addCategory(Intent.CATEGORY_OPENABLE)
                            // Ensure we want a result and grant perms
                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or
                                     Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
                        }

                        // If MiXplorer isn’t installed, bail gracefully
                        val pm = act.packageManager
                        val res = pm.resolveActivity(mix, 0)
                        if (res == null) {
                            XposedBridge.log("FilePickerRedirect: MiXplorer not found; skipping redirect")
                            return
                        }

                        // Launch MiXplorer for result and prevent DocumentsUI from rendering
                        try {
                            act.startActivityForResult(mix, 0x4D58) // 'MX' :)
                            act.overridePendingTransition(0, 0)
                            act.finish() // finish picker; caller will get result from MiXplorer
                            param.result = null // skip original onCreate
                        } catch (e: Throwable) {
                            XposedBridge.log("FilePickerRedirect: launch failed: ${e.message}")
                        }
                    }
                }
            )
        } catch (t: Throwable) {
            XposedBridge.log("FilePickerRedirect: hook install error ${t.message}")
        }
    }
}