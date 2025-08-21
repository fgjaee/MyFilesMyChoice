package com.example.filemanagerredirect

import android.content.Context
import android.content.Intent
import android.os.Binder
import android.os.Process
import android.util.Log
import de.robv.android.xposed.IXposedHookLoadPackage
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.XposedHelpers
import de.robv.android.xposed.callbacks.XC_LoadPackage
import java.lang.reflect.Method

/**
 * System-framework-only hook.
 * Hooks ActivityTaskManagerService.startActivityAsUser in system_server ("android" package).
 */
class XposedEntry : IXposedHookLoadPackage {

    companion object {
        private const val TAG = "XposedEntry"
        private const val MODULE_PKG = "com.example.filemanagerredirect"
    }

    override fun handleLoadPackage(lpparam: XC_LoadPackage.LoadPackageParam) {
        // We only act inside the android (system_server) process
        if (lpparam.packageName != "android") return

        try {
            hookATMS(lpparam.classLoader)
            Log.i(TAG, "Hook installed in system_server")
        } catch (t: Throwable) {
            Log.e(TAG, "Failed to hook ATMS", t)
        }
    }

    private fun hookATMS(cl: ClassLoader) {
        val atmsClass = XposedHelpers.findClass(
            "com.android.server.wm.ActivityTaskManagerService",
            cl
        )

        // Signatures vary across Android versions. We hook ALL overloads named startActivityAsUser.
        val methods: Array<Method> = atmsClass.declaredMethods
        var count = 0
        for (m in methods) {
            if (m.name != "startActivityAsUser") continue
            try {
                XposedBridge.hookMethod(m, object : XC_MethodHook() {
                    override fun beforeHookedMethod(param: MethodHookParam) {
                        // Only user/system processes should be launching activities here;
                        // but don't do anything during system boot's very early phase.
                        if (Process.myUid() != Process.SYSTEM_UID) return

                        val intent = param.args.firstOrNull { it is Intent } as? Intent ?: return
                        val action = intent.action ?: return

                        if (!isPickerAction(action)) return
                        if (isChooser(intent)) return // avoid loops
                        // If caller already hard-pins a package/component to a specific app, respect it.
                        if (intent.component != null) return

                        // Read module prefs/resources from module context (not "android" context)
                        val moduleCtx = obtainModuleContext() ?: run {
                            Log.w(TAG, "No module context available; skip redirect")
                            return
                        }

                        val target = try {
                            FileManagerRedirectHook.resolveTargetPackage(moduleCtx)
                        } catch (t: Throwable) {
                            Log.e(TAG, "resolveTargetPackage failed", t)
                            null
                        } ?: return

                        // If already going to target, do nothing
                        if (intent.`package` == target) return

                        // Set explicit package to our chosen manager
                        intent.`package` = target
                        Log.d(TAG, "Redirected $action to $target (callerUid=${Binder.getCallingUid()})")
                    }
                })
                count++
            } catch (t: Throwable) {
                // Keep trying other overloads; many ROMs have multiple
                Log.w(TAG, "Hook failed for overload: $m", t)
            }
        }

        if (count == 0) {
            Log.e(TAG, "No startActivityAsUser overloads were hooked – ROM may differ")
        } else {
            Log.i(TAG, "Hooked $count startActivityAsUser overload(s)")
        }
    }

    private fun isPickerAction(action: String): Boolean {
        return action == Intent.ACTION_GET_CONTENT ||
               action == Intent.ACTION_OPEN_DOCUMENT ||
               action == Intent.ACTION_OPEN_DOCUMENT_TREE ||
               action == Intent.ACTION_CREATE_DOCUMENT ||
               action == Intent.ACTION_PICK
    }

    private fun isChooser(intent: Intent): Boolean {
        val comp = intent.component ?: return false
        // Defensive: skip Android's chooser
        return comp.className == "com.android.internal.app.ChooserActivity"
    }

    /**
     * Create a Context for our module APK inside system_server so we can read:
     *  - SharedPreferences in device-protected storage
     *  - Resources (arrays etc.)
     */
    private fun obtainModuleContext(): Context? {
        return try {
            val activityThreadClass = XposedHelpers.findClass(
                "android.app.ActivityThread",
                null
            )
            val at = XposedHelpers.callStaticMethod(activityThreadClass, "currentActivityThread")
            val sysCtx = XposedHelpers.callMethod(at, "getSystemContext") as Context

            // Use device-protected storage so prefs are available before user unlock
            val flags = Context.CONTEXT_IGNORE_SECURITY or Context.CONTEXT_DEVICE_PROTECTED_STORAGE
            val moduleCtx = sysCtx.createPackageContext(MODULE_PKG, flags)
            moduleCtx
        } catch (t: Throwable) {
            Log.e(TAG, "obtainModuleContext failed", t)
            null
        }
    }
}