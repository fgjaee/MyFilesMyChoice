// app/src/main/java/com/example/filemanagerredirect/XposedEntry.kt
package com.example.filemanagerredirect

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Binder
import android.os.Bundle
import android.text.TextUtils
import de.robv.android.xposed.IXposedHookLoadPackage
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.XposedHelpers
import de.robv.android.xposed.callbacks.XC_LoadPackage

/**
 * LSPosed entry: hook system framework ("android") and rewrite
 * file-picking Intents to the chosen file manager.
 */
class XposedEntry : IXposedHookLoadPackage {

    override fun handleLoadPackage(lpparam: XC_LoadPackage.LoadPackageParam) {
        // Only hook the system framework process.
        if (lpparam.packageName != "android") return

        try {
            hookStartActivity(lpparam)
            XposedBridge.log("XposedEntry: hooked ActivityTaskManagerService.startActivityAsUser")
        } catch (t: Throwable) {
            XposedBridge.log("XposedEntry error while hooking: ${t.stackTraceToString()}")
        }
    }

    private fun hookStartActivity(lpparam: XC_LoadPackage.LoadPackageParam) {
        val atmsClassName = "com.android.server.wm.ActivityTaskManagerService"

        // Resolve hidden/public parameter classes reflectively so we can compile on CI.
        val IApplicationThreadClass = XposedHelpers.findClass(
            "android.app.IApplicationThread",
            lpparam.classLoader
        )
        val IBinderClass = XposedHelpers.findClass("android.os.IBinder", lpparam.classLoader)
        val ProfilerInfoClass = XposedHelpers.findClass(
            "android.app.ProfilerInfo",
            lpparam.classLoader
        )

        XposedHelpers.findAndHookMethod(
            atmsClassName,
            lpparam.classLoader,
            "startActivityAsUser",
            IApplicationThreadClass,                // caller
            String::class.java,                     // callingPackage
            Intent::class.java,                     // intent
            String::class.java,                     // resolvedType
            IBinderClass,                           // resultTo
            String::class.java,                     // resultWho
            Int::class.javaPrimitiveType,           // requestCode
            Int::class.javaPrimitiveType,           // startFlags
            ProfilerInfoClass,                      // profilerInfo
            Bundle::class.java,                     // bOptions
            Int::class.javaPrimitiveType,           // userId
            object : XC_MethodHook() {
                override fun beforeHookedMethod(param: MethodHookParam) {
                    try {
                        val intent = param.args[2] as? Intent ?: return
                        val userId = (param.args[10] as? Int) ?: 0

                        // Context from system_server
                        val sysContext = getSystemContext(param.thisObject) ?: return
                        val targetPkg =
                            FileManagerRedirectHook.resolveTargetPackage(sysContext) ?: return
                        if (targetPkg.isBlank()) return

                        // ACTION_CHOOSER (e.g., Gmail)
                        if (Intent.ACTION_CHOOSER == intent.action) {
                            val inner =
                                intent.getParcelableExtra<Intent>(Intent.EXTRA_INTENT)
                            if (inner != null && shouldRedirect(inner, targetPkg)) {
                                redirectIntent(inner, targetPkg)
                                intent.putExtra(Intent.EXTRA_INTENT, inner)
                                XposedBridge.log("XposedEntry: redirected CHOOSER inner -> $targetPkg")
                            }
                            return
                        }

                        // Plain pickers or explicit system picker component
                        if (shouldRedirect(intent, targetPkg)) {
                            redirectIntent(intent, targetPkg)
                            XposedBridge.log(
                                "XposedEntry: redirected ${intent.action} -> $targetPkg (uid=${Binder.getCallingUid()}, userId=$userId)"
                            )
                        }
                    } catch (t: Throwable) {
                        XposedBridge.log("XposedEntry redirect error: ${t.stackTraceToString()}")
                    }
                }
            }
        )
    }

    /** True if this is one of the SAF / picker actions we care about. */
    private fun isPickerAction(action: String): Boolean {
        return action == Intent.ACTION_GET_CONTENT ||
                action == Intent.ACTION_OPEN_DOCUMENT ||
                action == Intent.ACTION_OPEN_DOCUMENT_TREE ||
                action == Intent.ACTION_CREATE_DOCUMENT ||
                action == Intent.ACTION_PICK
    }

    /**
     * Decide whether to redirect this intent:
     * - Allow plain picker actions.
     * - Allow explicit system picker components (com.android.documentsui.*).
     * - Skip if already targeted at the chosen manager (avoid loops).
     * - Otherwise, if explicit component is some *other* package, respect it.
     */
    private fun shouldRedirect(intent: Intent, target: String): Boolean {
        // Already going to the chosen package? do not touch.
        if (intent.`package` == target) return false

        val action = intent.action ?: return false

        // Chooser handled earlier.
        if (action == Intent.ACTION_CHOOSER) return false

        val comp: ComponentName? = intent.component
        if (comp != null) {
            // Allow replacing explicit system picker
            if (comp.packageName.startsWith("com.android.documentsui")) {
                return true
            }
            // Respect other explicit components
            return false
        }

        // Plain implicit picker actions
        return isPickerAction(action)
    }

    /** Apply package redirection to the intent, preserving other fields. */
    private fun redirectIntent(intent: Intent, target: String) {
        intent.component = null                  // let resolver choose inside target package
        intent.`package` = target                // restrict resolution to target
        if (intent.action == Intent.ACTION_GET_CONTENT &&
            !intent.hasCategory(Intent.CATEGORY_OPENABLE)
        ) {
            intent.addCategory(Intent.CATEGORY_OPENABLE)
        }
    }

    /** Obtain a working Context inside system_server. */
    private fun getSystemContext(activityTaskMgr: Any): Context? {
        return try {
            XposedHelpers.getObjectField(activityTaskMgr, "mContext") as? Context
        } catch (_: Throwable) {
            null
        }
    }
}