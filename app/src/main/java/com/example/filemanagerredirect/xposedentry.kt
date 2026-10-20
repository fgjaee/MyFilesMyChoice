package com.example.filemanagerredirect

import android.app.Activity
import android.content.ComponentName
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import de.robv.android.xposed.IXposedHookLoadPackage
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.XposedHelpers
import de.robv.android.xposed.callbacks.XC_LoadPackage

/**
 * Global DocumentsUI interception (opt in), not per-calling-app interception.
 *
 * Hook only DocumentsUI processes. Keep the original picker Activity alive,
 * start the bridge FOR RESULT, and forward that result to the original caller.
 * An aborted/unsupported bridge returns to the already-open stock picker.
 *
 * SAF's persistable OPEN_DOCUMENT / TREE / CREATE semantics and the separate
 * system Photo Picker remain untouched in this experimental revision.
 */
class XposedEntry : IXposedHookLoadPackage {
    companion object {
        private val pickerPackages = setOf(
            "com.android.documentsui",
            "com.google.android.documentsui"
        )
        private const val REQUEST_BRIDGE = 0x4D46
        private const val STATE = "mfmc.globalPickerState"
        private const val CONFIG_AUTHORITY = "content://com.example.filemanagerredirect.settings"
        private const val BRIDGE_PACKAGE = "com.example.filemanagerredirect"
        private const val BRIDGE_CLASS = "com.example.filemanagerredirect.RedirectActivity"
    }

    override fun handleLoadPackage(lpparam: XC_LoadPackage.LoadPackageParam) {
        if (lpparam.packageName !in pickerPackages) return
        try {
            // Activity.dispatchActivityResult runs before DocumentsUI's own result
            // callback (including the modern ComponentCaller signature).
            val resultHooks = XposedBridge.hookAllMethods(
                Activity::class.java, "dispatchActivityResult",
                object : XC_MethodHook() {
                    override fun beforeHookedMethod(param: MethodHookParam) {
                        val activity = param.thisObject as? Activity ?: return
                        if (activity.packageName !in pickerPackages) return
                        if (param.args.size < 4) return
                        if (param.args[0] != null) return // Fragment/view result, not ours
                        if (param.args[1] != REQUEST_BRIDGE) return
                        if (XposedHelpers.getAdditionalInstanceField(activity, STATE) != "launched") return

                        // Block DocumentsUI from treating our private requestCode as its own.
                        XposedHelpers.setAdditionalInstanceField(activity, STATE, "bypassed")
                        val resultCode = param.args[2] as? Int ?: Activity.RESULT_CANCELED
                        val data = param.args[3] as? Intent
                        if (resultCode == Activity.RESULT_OK && isGrantable(data)) {
                            val result = Intent(data).apply {
                                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                            }
                            activity.setResult(Activity.RESULT_OK, result)
                            activity.finish()
                            XposedBridge.log("MFMC: forwarded file result through DocumentsUI")
                        } else {
                            // User canceled or external picker returned an unusable URI:
                            // leave the original DocumentsUI open as the fallback.
                            XposedBridge.log("MFMC: returning to stock picker")
                        }
                        param.result = null
                    }
                }
            )
            // Never launch a bridge unless we can receive its result.
            if (resultHooks.isEmpty()) {
                XposedBridge.log("MFMC: result dispatch unavailable; hook disabled")
                return
            }
            XposedBridge.hookAllMethods(
                Activity::class.java, "onResume",
                object : XC_MethodHook() {
                    override fun afterHookedMethod(param: MethodHookParam) {
                        val activity = param.thisObject as? Activity ?: return
                        if (activity.packageName !in pickerPackages ||
                            activity.isFinishing ||
                            activity.intent?.action != Intent.ACTION_GET_CONTENT) return
                        if (XposedHelpers.getAdditionalInstanceField(activity, STATE) != null) return
                        if (!globalEnabled(activity)) return

                        XposedHelpers.setAdditionalInstanceField(activity, STATE, "launched")
                        try {
                            val original = activity.intent
                            val bridge = Intent(Intent.ACTION_GET_CONTENT).apply {
                                component = ComponentName(BRIDGE_PACKAGE, BRIDGE_CLASS)
                                type = original.type ?: "*/*"
                                if (original.hasCategory(Intent.CATEGORY_OPENABLE)) {
                                    addCategory(Intent.CATEGORY_OPENABLE)
                                }
                                original.getStringArrayExtra(Intent.EXTRA_MIME_TYPES)?.let {
                                    putExtra(Intent.EXTRA_MIME_TYPES, it)
                                }
                                putExtra(Intent.EXTRA_ALLOW_MULTIPLE,
                                    original.getBooleanExtra(Intent.EXTRA_ALLOW_MULTIPLE, false))
                                putExtra(Intent.EXTRA_LOCAL_ONLY,
                                    original.getBooleanExtra(Intent.EXTRA_LOCAL_ONLY, false))
                            }
                            activity.startActivityForResult(bridge, REQUEST_BRIDGE)
                            XposedBridge.log("MFMC: launched global file picker bridge")
                        } catch (t: Throwable) {
                            XposedHelpers.setAdditionalInstanceField(activity, STATE, "bypassed")
                            XposedBridge.log("MFMC: launch failed; using stock picker: " + t)
                        }
                    }
                }
            )
            XposedBridge.log("MFMC: DocumentsUI global GET_CONTENT hooks installed")
        } catch (t: Throwable) {
            XposedBridge.log("MFMC: hook registration failed: " + t)
        }
    }

    private fun globalEnabled(activity: Activity): Boolean {
        return try {
            val config: Bundle? = activity.contentResolver.call(
                Uri.parse(CONFIG_AUTHORITY), "read", null, null)
            config?.getBoolean("global_enabled", false) == true &&
                config.getBoolean("enabled", true)
        } catch (_: Throwable) {
            // If the module is missing/disabled, fail open to stock DocumentsUI.
            false
        }
    }

    private fun isGrantable(data: Intent?): Boolean {
        if (data == null) return false
        val uris = mutableListOf<Uri>()
        data.data?.let { uris.add(it) }
        data.clipData?.let { clip ->
            for (i in 0 until clip.itemCount) {
                clip.getItemAt(i).uri?.let { uris.add(it) }
            }
        }
        return uris.isNotEmpty() && uris.all { it.scheme == "content" }
    }
}
