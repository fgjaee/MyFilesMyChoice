package com.example.filemanagerredirect

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.widget.Toast
import de.robv.android.xposed.IXposedHookLoadPackage
import de.robv.android.xposed.IXposedHookZygoteInit
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.XposedHelpers
import de.robv.android.xposed.XSharedPreferences
import de.robv.android.xposed.callbacks.XC_LoadPackage
import java.io.File

private const val MODULE_PKG = "com.example.filemanagerredirect"
private const val PREFS = "fmredirect_prefs"
private const val TAG = "FMRedirect"

class FileManagerRedirectHook : IXposedHookLoadPackage, IXposedHookZygoteInit {

    private val actions = setOf(
        Intent.ACTION_GET_CONTENT,
        Intent.ACTION_OPEN_DOCUMENT,
        Intent.ACTION_OPEN_DOCUMENT_TREE,
        Intent.ACTION_CREATE_DOCUMENT,
        Intent.ACTION_PICK,
        MediaStore.ACTION_PICK_IMAGES
    )

    override fun initZygote(startupParam: IXposedHookZygoteInit.StartupParam) {}

    override fun handleLoadPackage(lpparam: XC_LoadPackage.LoadPackageParam) {
        val pkg = lpparam.packageName ?: return
        // don't hook ourselves or obvious system processes that can cause noise
        if (pkg == MODULE_PKG || pkg == "com.android.systemui") return

        try {
            val ctxClass = XposedHelpers.findClass("android.app.ContextImpl", lpparam.classLoader)
            val actClass = XposedHelpers.findClass("android.app.Activity", lpparam.classLoader)
            val instrClass = XposedHelpers.findClass("android.app.Instrumentation", lpparam.classLoader)

            XposedHelpers.findAndHookMethod(ctxClass, "startActivity", Intent::class.java,
                object : XC_MethodHook() {
                    override fun beforeHookedMethod(p: MethodHookParam) {
                        val intent = p.args[0] as? Intent ?: return
                        if (rewriteIfNeeded(p.thisObject as Context, pkg, intent)) {
                            XposedBridge.log("$TAG: [Context] -> ${intent.`package`}")
                        }
                    }
                })

            XposedHelpers.findAndHookMethod(ctxClass, "startActivity", Intent::class.java, Bundle::class.java,
                object : XC_MethodHook() {
                    override fun beforeHookedMethod(p: MethodHookParam) {
                        val intent = p.args[0] as? Intent ?: return
                        if (rewriteIfNeeded(p.thisObject as Context, pkg, intent)) {
                            XposedBridge.log("$TAG: [Context+Bundle] -> ${intent.`package`}")
                        }
                    }
                })

            runCatching {
                val userHandle = XposedHelpers.findClass("android.os.UserHandle", lpparam.classLoader)
                XposedHelpers.findAndHookMethod(ctxClass, "startActivityAsUser", Intent::class.java, userHandle,
                    object : XC_MethodHook() {
                        override fun beforeHookedMethod(p: MethodHookParam) {
                            val intent = p.args[0] as? Intent ?: return
                            if (rewriteIfNeeded(p.thisObject as Context, pkg, intent)) {
                                XposedBridge.log("$TAG: [startActivityAsUser] -> ${intent.`package`}")
                            }
                        }
                    })
            }

            XposedHelpers.findAndHookMethod(actClass, "startActivityForResult", Intent::class.java, Int::class.javaPrimitiveType,
                object : XC_MethodHook() {
                    override fun beforeHookedMethod(p: MethodHookParam) {
                        val intent = p.args[0] as? Intent ?: return
                        if (rewriteIfNeeded(p.thisObject as Activity, pkg, intent)) {
                            XposedBridge.log("$TAG: [Activity] -> ${intent.`package`}")
                        }
                    }
                })
            XposedHelpers.findAndHookMethod(actClass, "startActivityForResult", Intent::class.java, Int::class.javaPrimitiveType, Bundle::class.java,
                object : XC_MethodHook() {
                    override fun beforeHookedMethod(p: MethodHookParam) {
                        val intent = p.args[0] as? Intent ?: return
                        if (rewriteIfNeeded(p.thisObject as Activity, pkg, intent)) {
                            XposedBridge.log("$TAG: [Activity+Bundle] -> ${intent.`package`}")
                        }
                    }
                })

            XposedHelpers.findAndHookMethod(
                instrClass, "execStartActivity",
                Context::class.java, android.os.IBinder::class.java, android.os.IBinder::class.java,
                Activity::class.java, Intent::class.java, Int::class.javaPrimitiveType, Bundle::class.java,
                object : XC_MethodHook() {
                    override fun beforeHookedMethod(p: MethodHookParam) {
                        val ctx = p.args[0] as? Context ?: return
                        val intent = p.args[4] as? Intent ?: return
                        if (rewriteIfNeeded(ctx, pkg, intent)) {
                            XposedBridge.log("$TAG: [Instrumentation] -> ${intent.`package`}")
                        }
                    }
                }
            )
        } catch (t: Throwable) {
            XposedBridge.log("$TAG: hook error $t")
        }
    }

    private fun loadPrefs(): XSharedPreferences? {
        val paths = listOf(
            "/data/user_de/0/$MODULE_PKG/shared_prefs/$PREFS.xml",
            "/data/user/0/$MODULE_PKG/shared_prefs/$PREFS.xml"
        )
        val file = paths.map(::File).firstOrNull { it.canRead() } ?: return null
        return try { XSharedPreferences(file).apply { makeWorldReadable(); reload() } } catch (_: Throwable) { null }
    }

    private fun toast(ctx: Context, msg: String) {
        try { Handler(Looper.getMainLooper()).post { Toast.makeText(ctx.applicationContext, msg, Toast.LENGTH_SHORT).show() } } catch (_: Throwable) {}
    }

    private fun rewriteIfNeeded(ctx: Context, callerPkg: String, original: Intent): Boolean {
        val xp = loadPrefs() ?: return false
        xp.reload()
        if (!xp.getBoolean("enabled", true)) return false

        var target = original
        if (Intent.ACTION_CHOOSER == original.action) {
            original.getParcelableExtra<Intent>(Intent.EXTRA_INTENT)?.let { target = it }
        }

        val action = target.action ?: return false
        if (!actions.contains(action)) return false

        val preferred = xp.getString("preferred_pkg", null)
        val autoFallback = xp.getBoolean("auto_fallback", true)
        val allowSystemFallback = xp.getBoolean("fallback", true)
        val showToasts = xp.getBoolean("debug_toasts", true)

        val csv = xp.getString("fallback_list", "") ?: ""
        val candidates = csv.split(',').map { it.trim() }.filter { it.isNotEmpty() }.toMutableList()
        if (preferred != null) { candidates.remove(preferred); candidates.add(0, preferred) }

        val chosen = candidates.firstOrNull { pkg ->
            val probe = Intent(action).addCategory(Intent.CATEGORY_DEFAULT).setPackage(pkg)
            runCatching { target.type?.let { probe.setType(it) } }
            ctx.packageManager.queryIntentActivities(probe, 0).isNotEmpty()
        } ?: run {
            if (!allowSystemFallback || !autoFallback) { if (showToasts) toast(ctx, "FMRedirect: no handler") }
            return false
        }

        target.setPackage(chosen)
        target.component = null
        target.addCategory(Intent.CATEGORY_DEFAULT)
        target.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

        if (showToasts) toast(ctx, "FMRedirect → $chosen")

        if (Intent.ACTION_CHOOSER == original.action && target !== original) {
            original.putExtra(Intent.EXTRA_INTENT, target)
        }
        return true
    }
}