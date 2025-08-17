package com.example.filemanagerredirect

import android.app.AndroidAppHelper
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Binder
import de.robv.android.xposed.IXposedHookLoadPackage
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.XposedHelpers
import de.robv.android.xposed.callbacks.XC_LoadPackage

class FileManagerRedirectHook : IXposedHookLoadPackage {

    companion object {
        private const val TAG = "FMRedirect"
        private const val PREFS = "fm_redirect_prefs"
        private const val KEY_ENABLED = "enabled"
        private const val KEY_STEALTH = "stealth_mode"
        private const val KEY_SELECTED = "selected_fm_pkg"
        private const val KEY_FALLBACK_SAF = "fallback_saf"

        // refuse to hook these even if scoped by mistake
        private val DO_NOT_TOUCH = setOf(
            "com.google.android.gms",                      // Play Integrity
            "com.google.android.gms.policy_sidecar",
            "com.google.android.gsf",
            "com.netflix.mediaclient",
            "com.disney.disneyplus",
            "com.amazon.avod.thirdpartyclient",
            "com.paypal.android.p2pmobile",
            // add your own banks/games here as needed
        )

        // intent actions we care about
        private val INTERESTING = setOf(
            Intent.ACTION_GET_CONTENT,
            Intent.ACTION_PICK,
            Intent.ACTION_VIEW,
            Intent.ACTION_CHOOSER,
            Intent.ACTION_OPEN_DOCUMENT,
            Intent.ACTION_OPEN_DOCUMENT_TREE,
            Intent.ACTION_CREATE_DOCUMENT,
            "android.provider.action.PICK_IMAGES",          // Picker 13+
            "android.provider.action.PICK_IMAGES_FROM_SYSTEM"
        )

        // known file managers (more can be added)
        private val KNOWN_FMS = listOf(
            "com.mixplorer", "com.mixplorer.silver",
            "pl.solidexplorer2",
            "com.lonelycatgames.Xplore",
            "com.speedsoftware.rootexplorer",
            "com.amaze.filemanager",
            "com.cxinventor.file.explorer",                 // CX
            "nextapp.fx", "com.nextapp.fx",                // FX (two ids seen)
            "me.zhanghai.android.files",                   // Material Files
            "com.ghisler.android.TotalCommander",
            "com.oneplus.filemanager",
            "com.google.android.apps.nbu.files",
            "bin.mt.plus", "bin.mt.plus.canary",
            "ru.zdevs.zarchiver",
            "com.soft.explorer",
            "org.kman.AmazeFileUtilities"
        )
    }

    override fun handleLoadPackage(lpparam: XC_LoadPackage.LoadPackageParam) {
        // skip our own and sensitive pkgs
        if (lpparam.packageName == "com.example.filemanagerredirect") return
        if (lpparam.packageName in DO_NOT_TOUCH) return

        // Hook Activity.startActivity / startActivityForResult
        hookActivity(lpparam)
        // Also hook ContextWrapper.startActivity (many components call this)
        hookContextStartActivity(lpparam)
        // And hook Intent.createChooser to rewrite the target inside choosers
        hookCreateChooser(lpparam)
    }

    private fun hookActivity(lpparam: XC_LoadPackage.LoadPackageParam) {
        XposedHelpers.findAndHookMethod(
            "android.app.Activity",
            lpparam.classLoader,
            "startActivity",
            Intent::class.java,
            object : XC_MethodHook() {
                override fun beforeHookedMethod(param: MethodHookParam) {
                    val inIntent = param.args[0] as? Intent ?: return
                    rewriteIfNeeded(inIntent)?.let { param.args[0] = it }
                }
            }
        )

        // startActivityForResult(Intent, int)
        XposedHelpers.findAndHookMethod(
            "android.app.Activity",
            lpparam.classLoader,
            "startActivityForResult",
            Intent::class.java, Int::class.javaPrimitiveType,
            object : XC_MethodHook() {
                override fun beforeHookedMethod(param: MethodHookParam) {
                    val inIntent = param.args[0] as? Intent ?: return
                    rewriteIfNeeded(inIntent)?.let { param.args[0] = it }
                }
            }
        )
    }

    private fun hookContextStartActivity(lpparam: XC_LoadPackage.LoadPackageParam) {
        XposedHelpers.findAndHookMethod(
            "android.content.ContextWrapper",
            lpparam.classLoader,
            "startActivity",
            Intent::class.java,
            object : XC_MethodHook() {
                override fun beforeHookedMethod(param: MethodHookParam) {
                    val inIntent = param.args[0] as? Intent ?: return
                    rewriteIfNeeded(inIntent)?.let { param.args[0] = it }
                }
            }
        )
    }

    private fun hookCreateChooser(lpparam: XC_LoadPackage.LoadPackageParam) {
        XposedHelpers.findAndHookMethod(
            Intent::class.java,
            "createChooser",
            Intent::class.java, CharSequence::class.java,
            object : XC_MethodHook() {
                override fun beforeHookedMethod(param: MethodHookParam) {
                    val base = param.args[0] as? Intent ?: return
                    rewriteIfNeeded(base)?.let { param.args[0] = it }
                }
            }
        )
    }

    private fun rewriteIfNeeded(inIntent: Intent): Intent? {
        val ctx = AndroidAppHelper.currentApplication() ?: return null
        val sp = safePrefs(ctx)

        if (!sp.getBoolean(KEY_ENABLED, false)) return null

        // stealth mode: only touch if it looks like a file-picker / file-view intent
        val stealth = sp.getBoolean(KEY_STEALTH, true)
        if (stealth && !isInteresting(inIntent)) return null

        val preferred = sp.getString(KEY_SELECTED, null)
        val fallbackSaf = sp.getBoolean(KEY_FALLBACK_SAF, true)

        // If user chose a specific FM and it’s installed, force it via setPackage()
        val targetPkg = when {
            !preferred.isNullOrBlank() && isInstalled(ctx, preferred) -> preferred
            else -> autoDetectInstalledFm(ctx)
        } ?: run {
            // nothing installed: optionally let SAF/system handle it
            return if (fallbackSaf) null else null
        }

        // For chooser, rewrite the inner intent
        val intent = if (inIntent.action == Intent.ACTION_CHOOSER) {
            (inIntent.getParcelableExtra(Intent.EXTRA_INTENT) as? Intent) ?: inIntent
        } else inIntent

        // Only force package when the action is about picking/opening content
        if (isInteresting(intent)) {
            // Don’t clobber content uri/type; just direct to our package
            intent.setPackage(targetPkg)
            // Some apps use VIEW on content://; allow persisted permissions
            if (intent.action == Intent.ACTION_OPEN_DOCUMENT || intent.action == Intent.ACTION_CREATE_DOCUMENT) {
                intent.addFlags(
                    Intent.FLAG_GRANT_READ_URI_PERMISSION or
                            Intent.FLAG_GRANT_WRITE_URI_PERMISSION or
                            Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION
                )
            }
            XposedBridge.log("$TAG: redirected -> $targetPkg for ${intent.action}")
        }

        // If original was chooser, put the modified base back
        if (inIntent.action == Intent.ACTION_CHOOSER) {
            inIntent.putExtra(Intent.EXTRA_INTENT, intent)
            return inIntent
        }
        return intent
    }

    private fun safePrefs(appCtx: Context): android.content.SharedPreferences {
        // device-protected so prefs are readable in early/isolated processes
        return try {
            val de = appCtx.createDeviceProtectedStorageContext()
            de.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        } catch (_: Throwable) {
            appCtx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        }
    }

    private fun isInteresting(i: Intent?): Boolean {
        if (i == null) return false
        if (i.action in INTERESTING) return true
        // VIEW with content:// or file:// is interesting
        if (i.action == Intent.ACTION_VIEW) {
            val u: Uri? = i.data
            return u != null && (u.scheme == "content" || u.scheme == "file")
        }
        return false
    }

    private fun isInstalled(ctx: Context, pkg: String): Boolean =
        try { ctx.packageManager.getPackageInfo(pkg, 0); true } catch (_: Throwable) { false }

    private fun autoDetectInstalledFm(ctx: Context): String? =
        KNOWN_FMS.firstOrNull { isInstalled(ctx, it) }
}