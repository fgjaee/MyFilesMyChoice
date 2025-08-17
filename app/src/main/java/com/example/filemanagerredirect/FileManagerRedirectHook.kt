package com.example.filemanagerredirect

import android.app.Activity
import android.app.AndroidAppHelper
import android.app.Application
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.MediaStore
import de.robv.android.xposed.IXposedHookLoadPackage
import de.robv.android.xposed.IXposedHookZygoteInit
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.XposedHelpers
import de.robv.android.xposed.callbacks.XC_LoadPackage

class FileManagerRedirectHook : IXposedHookLoadPackage, IXposedHookZygoteInit {

    companion object {
        private const val TAG = "FMRedirect"
        private val REDIRECT_ACTIONS = setOf(
            Intent.ACTION_GET_CONTENT,
            Intent.ACTION_OPEN_DOCUMENT,
            Intent.ACTION_OPEN_DOCUMENT_TREE,
            Intent.ACTION_CREATE_DOCUMENT,
            Intent.ACTION_PICK,
        )
    }

    override fun initZygote(startupParam: IXposedHookZygoteInit.StartupParam?) {
        // no-op
    }

    override fun handleLoadPackage(lpparam: XC_LoadPackage.LoadPackageParam) {
        // Hook only in framework-level processes to affect all apps when you scope just "System Framework"
        val pkg = lpparam.packageName ?: return
        val isFramework =
            pkg == "android" ||
            pkg == "com.android.systemui" ||
            pkg == "com.google.android.systemui" ||
            pkg == "com.android.settings"

        if (!isFramework) return

        try {
            hookContextStartActivity(lpparam)
            hookActivityStartActivity(lpparam)
            hookCreateChooser(lpparam)
            XposedBridge.log("$TAG: hooks installed in $pkg")
        } catch (t: Throwable) {
            XposedBridge.log("$TAG: hook failure in $pkg: ${t.message}")
        }
    }

    private fun hookContextStartActivity(lpparam: XC_LoadPackage.LoadPackageParam) {
        XposedHelpers.findAndHookMethod(
            "android.content.ContextWrapper",
            lpparam.classLoader,
            "startActivity",
            Intent::class.java,
            object : XC_MethodHook() {
                override fun beforeHookedMethod(param: MethodHookParam) {
                    val intent = param.args[0] as? Intent ?: return
                    val ctx = (param.thisObject as? Context) ?: return
                    rewriteIfNeeded(ctx, intent)?.let { param.args[0] = it }
                }
            }
        )
    }

    private fun hookActivityStartActivity(lpparam: XC_LoadPackage.LoadPackageParam) {
        // Activity.startActivity(Intent)
        XposedHelpers.findAndHookMethod(
            "android.app.Activity",
            lpparam.classLoader,
            "startActivity",
            Intent::class.java,
            object : XC_MethodHook() {
                override fun beforeHookedMethod(param: MethodHookParam) {
                    val intent = param.args[0] as? Intent ?: return
                    val act = param.thisObject as? Activity ?: return
                    rewriteIfNeeded(act, intent)?.let { param.args[0] = it }
                }
            }
        )

        // Activity.startActivityForResult(Intent, int)
        XposedHelpers.findAndHookMethod(
            "android.app.Activity",
            lpparam.classLoader,
            "startActivityForResult",
            Intent::class.java,
            Int::class.javaPrimitiveType,
            object : XC_MethodHook() {
                override fun beforeHookedMethod(param: MethodHookParam) {
                    val intent = param.args[0] as? Intent ?: return
                    val act = param.thisObject as? Activity ?: return
                    rewriteIfNeeded(act, intent)?.let { param.args[0] = it }
                }
            }
        )
    }

    private fun hookCreateChooser(lpparam: XC_LoadPackage.LoadPackageParam) {
        // Intercept Intent.createChooser(inner, title) -> replace INNER intent if needed
        XposedHelpers.findAndHookMethod(
            "android.content.Intent",
            lpparam.classLoader,
            "createChooser",
            Intent::class.java,
            CharSequence::class.java,
            object : XC_MethodHook() {
                override fun afterHookedMethod(param: MethodHookParam) {
                    val returned = param.result as? Intent ?: return
                    val extras = returned.extras ?: return
                    val inner = extras.getParcelable<Intent>(Intent.EXTRA_INTENT) ?: return
                    val ctx = currentContext() ?: return
                    val rewritten = rewriteIfNeeded(ctx, inner) ?: return
                    extras.putParcelable(Intent.EXTRA_INTENT, rewritten)
                    param.result = returned
                    XposedBridge.log("$TAG: chooser inner replaced")
                }
            }
        )
    }

    /** Core logic: if intent matches file-picker actions, retarget to selected (or best available) FM. */
    private fun rewriteIfNeeded(ctx: Context, inIntent: Intent): Intent? {
        val action = inIntent.action ?: return null

        // SPECIAL: Android 13+ PICK_IMAGES
        val isPickImages = (Build.VERSION.SDK_INT >= 33 && action == MediaStore.ACTION_PICK_IMAGES)
        val isKnown = action in REDIRECT_ACTIONS || isPickImages
        if (!isKnown) return null

        val prefs = try {
            val de = ctx.createDeviceProtectedStorageContext()
            de.getSharedPreferences(Keys.PREFS, Context.MODE_PRIVATE)
        } catch (_: Throwable) {
            ctx.getSharedPreferences(Keys.PREFS, Context.MODE_PRIVATE)
        }

        val enabled = prefs.getBoolean(Keys.PREF_ENABLED, true)
        if (!enabled) return null

        val selected = prefs.getString(Keys.PREF_SELECTED_PKG, Keys.PKG_AUTO) ?: Keys.PKG_AUTO
        val chosenPkg = pickTargetPackage(ctx.packageManager, selected)

        if (chosenPkg == null) {
            XposedBridge.log("$TAG: no suitable FM found; leaving intent as-is ($action)")
            return null
        }

        // Build explicit intent that mirrors the original request
        val explicit = Intent(inIntent)
        explicit.`package` = null
        explicit.component = null
        explicit.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

        // Resolve a best component inside chosen package
        val resolved = ctx.packageManager.queryIntentActivities(explicit, PackageManager.MATCH_DEFAULT_ONLY)
            .firstOrNull { it.activityInfo.packageName == chosenPkg }

        if (resolved != null) {
            explicit.component = ComponentName(chosenPkg, resolved.activityInfo.name)
        } else {
            // As a fallback, just set the package; let the system pick the default activity from that app
            explicit.`package` = chosenPkg
        }

        XposedBridge.log("$TAG: redirect $action -> $chosenPkg")
        return explicit
    }

    private fun pickTargetPackage(pm: PackageManager, selected: String): String? {
        if (selected != Keys.PKG_AUTO) {
            return try {
                pm.getPackageInfo(selected, 0)
                selected
            } catch (_: Throwable) {
                // selected not installed; fall through
                findAnyInstalled(pm)
            }
        }
        return findAnyInstalled(pm)
    }

    private fun findAnyInstalled(pm: PackageManager): String? {
        // Prefer a sane order; otherwise pick first that resolves actions
        val order = FileManagerCatalog.PACKAGES
        for (pkg in order) {
            try {
                pm.getPackageInfo(pkg, 0)
                return pkg
            } catch (_: Throwable) { /* not installed */ }
        }
        // As a last resort, scan resolvers for OPEN_DOCUMENT
        val probe = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            type = "*/*"
            addCategory(Intent.CATEGORY_OPENABLE)
        }
        val list = pm.queryIntentActivities(probe, PackageManager.MATCH_DEFAULT_ONLY)
        return list.firstOrNull()?.activityInfo?.packageName
    }

    private fun currentContext(): Context? {
        return try {
            val app: Application? = AndroidAppHelper.currentApplication()
            app ?: AndroidAppHelper.currentApplication()
        } catch (_: Throwable) {
            null
        }
    }
}