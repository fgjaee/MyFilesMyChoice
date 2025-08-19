package com.example.filemanagerredirect

import android.app.Application
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.util.Log
import de.robv.android.xposed.IXposedHookLoadPackage
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.XposedHelpers
import de.robv.android.xposed.callbacks.XC_LoadPackage

class XposedEntry : IXposedHookLoadPackage {

    private val TAG = "FMR/Xposed"

    override fun handleLoadPackage(lpparam: XC_LoadPackage.LoadPackageParam) {
        // Only hook the framework process
        if (lpparam.packageName != "android") return
        log("Loaded in system server; installing hooks…")

        // ---- ActivityTaskManagerService (various startActivity* entry points)
        hookAll(
            className = "com.android.server.wm.ActivityTaskManagerService",
            loader = lpparam.classLoader,
            method = "startActivity",
        ) { param -> interceptFromArgs(param) }

        hookAll(
            className = "com.android.server.wm.ActivityTaskManagerService",
            loader = lpparam.classLoader,
            method = "startActivityAsUser",
        ) { param -> interceptFromArgs(param) }

        // ---- ActivityStarter (common launch path on A12–A14)
        hookAll(
            className = "com.android.server.wm.ActivityStarter",
            loader = lpparam.classLoader,
            method = "executeRequest",
        ) { param ->
            val req = param.args.getOrNull(0)
            val intent = req?.let { XposedHelpers.getObjectField(it, "intent") as? Intent }
            interceptIntent(intent)
        }

        hookAll(
            className = "com.android.server.wm.ActivityStarter",
            loader = lpparam.classLoader,
            method = "startActivityMayWait",
        ) { param -> interceptFromArgs(param) }

        hookAll(
            className = "com.android.server.wm.ActivityStarter",
            loader = lpparam.classLoader,
            method = "startActivityUnchecked",
        ) { param -> interceptFromArgs(param) }

        // ---- ChooserActivity: if a chooser still appears, rewrite its target
        try {
            val cls = XposedHelpers.findClass("com.android.internal.app.ChooserActivity", lpparam.classLoader)
            XposedBridge.hookAllMethods(cls, "onCreate", object : XC_MethodHook() {
                override fun beforeHookedMethod(param: MethodHookParam) {
                    // Base intent lives in mResolverIntent or mIntent on various versions; try both.
                    val base = runCatching {
                        XposedHelpers.getObjectField(param.thisObject, "mResolverIntent") as? Intent
                    }.getOrNull() ?: runCatching {
                        XposedHelpers.getObjectField(param.thisObject, "mIntent") as? Intent
                    }.getOrNull()

                    interceptIntent(base)
                }
            })
            log("Hooked ChooserActivity#onCreate ✓")
        } catch (t: Throwable) {
            log("Hook ChooserActivity failed: ${t.message}")
        }
    }

    // --- Interception plumbing ---

    private fun interceptFromArgs(param: XC_MethodHook.MethodHookParam) {
        // Walk all args and grab the first Intent (method overloads differ by ROM)
        val intent = param.args.firstOrNull { it is Intent } as? Intent
        interceptIntent(intent)
    }

    private fun interceptIntent(intent: Intent?) {
        if (intent == null) return
        val action = intent.action ?: return

        // Only touch common file picker actions
        val isPicker = action == Intent.ACTION_GET_CONTENT ||
                action == Intent.ACTION_OPEN_DOCUMENT ||
                action == Intent.ACTION_OPEN_DOCUMENT_TREE ||
                action == Intent.ACTION_CREATE_DOCUMENT
        if (!isPicker) return

        // Avoid loops/false positives
        if (intent.component?.className == "com.android.internal.app.ChooserActivity") return

        // If an app hardcodes DocumentsUI, remove it so our package retarget works
        val compPkg = intent.component?.packageName
        if (compPkg == "com.android.documentsui" || compPkg == "com.google.android.documentsui") {
            intent.component = null
        }

        val target = runCatching { FileManagerRedirectHook.resolveTargetPackage(systemContext()) }.getOrNull()
        if (target.isNullOrEmpty()) return

        if (intent.`package` == target) return // already targeted
        intent.`package` = target

        log("Redirected $action → $target")
    }

    private fun systemContext(): Context {
        val app = XposedHelpers.callStaticMethod(
            Class.forName("android.app.ActivityThread"),
            "currentApplication"
        ) as? Application
        return app ?: throw IllegalStateException("No system context")
    }

    private fun hookAll(
        className: String,
        loader: ClassLoader,
        method: String,
        before: (XC_MethodHook.MethodHookParam) -> Unit
    ) {
        try {
            val cls = XposedHelpers.findClass(className, loader)
            XposedBridge.hookAllMethods(cls, method, object : XC_MethodHook() {
                override fun beforeHookedMethod(param: MethodHookParam) = before(param)
            })
            log("Hooked $className#$method ✓")
        } catch (t: Throwable) {
            log("Hook $className#$method failed: ${t.message}")
        }
    }

    private fun log(msg: String) {
        XposedBridge.log("FMR/Xposed $msg")
        Log.d("FMR/Xposed", msg)
    }
}