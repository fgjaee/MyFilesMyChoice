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
        private const val MODULE_PKG = "com.example.filemanagerredirect" // must match your applicationId
    }

    override fun handleLoadPackage(lpparam: XC_LoadPackage.LoadPackageParam) {
        // Only hook the system framework process
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

        var hooked = 0
        for (m in atmsClass.declaredMethods) {
            if (m.name != "startActivityAsUser") continue
            try {
                XposedBridge.hookMethod(m, object : XC_MethodHook() {
                    override fun beforeHookedMethod(param: MethodHookParam) {
                        // We expect to be in system_server
                        if (Process.myUid() != Process.SYSTEM_UID) return

                        val intent = param.args.firstOrNull { it is Intent } as? Intent ?: return
                        val action = intent.action ?: return

                        if (!isPickerAction(action)) return
                        if (isChooser(intent)) return // avoid loops
                        if (intent.component != null) return // respect explicit component

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

                        if (intent.`package` == target) return

                        intent.`package` = target
                        Log.d(TAG, "Redirected $action to $target (callerUid=${Binder.getCallingUid()})")
                    }
                })
                hooked++
            } catch (t: Throwable) {
                Log.w(TAG, "Hook failed for overload: $m", t)
            }
        }

        if (hooked == 0) {
            Log.e(TAG, "No startActivityAsUser overloads were hooked – ROM may differ")
        } else {
            Log.i(TAG, "Hooked $hooked startActivityAsUser overload(s)")
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
        return comp.className == "com.android.internal.app.ChooserActivity"
    }

    /**
     * Build a module Context inside system_server and move it to device-protected storage.
     */
    private fun obtainModuleContext(): Context? {
        return try {
            val activityThreadClass = XposedHelpers.findClass("android.app.ActivityThread", null)
            val at = XposedHelpers.callStaticMethod(activityThreadClass, "currentActivityThread")
            val sysCtx = XposedHelpers.callMethod(at, "getSystemContext") as Context

            // 1) Create package context for our module (ignore security so resources/prefs are reachable)
            val base = sysCtx.createPackageContext(
                MODULE_PKG,
                Context.CONTEXT_IGNORE_SECURITY
            )

            // 2) Switch to device-protected storage (available API 24+; your minSdk is 24)
            // If anything goes wrong, fall back to base.
            try {
                base.createDeviceProtectedStorageContext()
            } catch (_: Throwable) {
                base
            }
        } catch (t: Throwable) {
            Log.e(TAG, "obtainModuleContext failed", t)
            null
        }
    }
}