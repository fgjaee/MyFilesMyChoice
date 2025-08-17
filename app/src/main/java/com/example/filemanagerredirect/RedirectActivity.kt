package com.example.filemanagerredirect

import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.content.Intent.CATEGORY_OPENABLE
import android.content.Intent.FLAG_GRANT_PREFIX_URI_PERMISSION
import android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION
import android.content.Intent.FLAG_GRANT_WRITE_URI_PERMISSION
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.activity.result.ActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity

/**
 * Receives file-picker style intents (OPEN_DOCUMENT / OPEN_DOCUMENT_TREE / CREATE_DOCUMENT / GET_CONTENT / PICK)
 * and forwards them to the chosen file manager. If the chosen one isn't installed, it falls back to any
 * installed manager from a known list, then to the system DocumentsUI. Returns the result to the original caller.
 */
class RedirectActivity : AppCompatActivity() {

    private val devicePrefs by lazy { deviceProtectedPrefs(this) }
    private val normalPrefs by lazy { getSharedPreferences(PREFS, Context.MODE_PRIVATE) }

    private val forwardLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { res: ActivityResult ->
        // Pass through whatever we got back
        setResult(res.resultCode, res.data)
        finish()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // If we were started with no intent somehow, just bail to system picker.
        val incoming = intent ?: run {
            startSystemPickerAndFinish()
            return
        }

        // Build a new intent that mirrors the incoming one (action/type/extras/categories/flags/data/clipData)
        val working = mirrorIntent(incoming)

        // Ensure openable where appropriate
        when (working.action) {
            Intent.ACTION_GET_CONTENT,
            Intent.ACTION_OPEN_DOCUMENT,
            Intent.ACTION_CREATE_DOCUMENT -> {
                if (!working.hasCategory(CATEGORY_OPENABLE)) {
                    working.addCategory(CATEGORY_OPENABLE)
                }
            }
        }

        // Grant URI permissions to downstream picker apps just in case
        working.addFlags(
            FLAG_GRANT_READ_URI_PERMISSION or
                FLAG_GRANT_WRITE_URI_PERMISSION or
                FLAG_GRANT_PREFIX_URI_PERMISSION
        )

        // Try preferred package first
        val preferred = readPreferredPackage()
        if (!preferred.isNullOrBlank() && isInstalled(preferred)) {
            val targeted = working.setPackage(preferred)
            if (canResolve(targeted)) {
                launchForward(targeted)
                return
            }
        }

        // Try any installed from the big known list
        for (pkg in KNOWN_FILE_MANAGERS) {
            if (isInstalled(pkg)) {
                val targeted = working.setPackage(pkg)
                if (canResolve(targeted)) {
                    launchForward(targeted)
                    return
                }
            }
        }

        // Try DocumentsUI explicitly
        val docsUi = working.setPackage(SYSTEM_DOCS_UI)
        if (isInstalled(SYSTEM_DOCS_UI) && canResolve(docsUi)) {
            launchForward(docsUi)
            return
        }

        // Last resort: implicit chooser with whatever can handle it
        try {
            forwardLauncher.launch(Intent.createChooser(working.setPackage(null), getString(R.string.chooser_title)))
        } catch (_: Throwable) {
            // Absolute last fallback: system picker with a generic GET_CONTENT
            startSystemPickerAndFinish()
        }
    }

    // region — Helpers

    private fun launchForward(targeted: Intent) {
        try {
            forwardLauncher.launch(targeted)
        } catch (_: ActivityNotFoundException) {
            startSystemPickerAndFinish()
        } catch (_: Throwable) {
            startSystemPickerAndFinish()
        }
    }

    private fun startSystemPickerAndFinish() {
        val sys = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(CATEGORY_OPENABLE)
            type = "*/*"
            addFlags(
                FLAG_GRANT_READ_URI_PERMISSION or
                    FLAG_GRANT_WRITE_URI_PERMISSION or
                    FLAG_GRANT_PREFIX_URI_PERMISSION
            )
        }
        try {
            forwardLauncher.launch(sys)
        } catch (_: Throwable) {
            // If even the system picker can't start, just finish with no result
            setResult(RESULT_CANCELED, null)
            finish()
        }
    }

    private fun mirrorIntent(src: Intent): Intent {
        val dst = Intent(src.action)

        // Data + type
        val data: Uri? = src.data
        val type: String? = src.type
        if (data != null && type != null) {
            dst.setDataAndType(data, type)
        } else if (data != null) {
            dst.data = data
        } else if (type != null) {
            dst.type = type
        }

        // Categories
        src.categories?.forEach { dst.addCategory(it) }

        // Flags (whitelist the ones that are safe to pass on)
        val passFlags =
            (src.flags and (FLAG_GRANT_READ_URI_PERMISSION or FLAG_GRANT_WRITE_URI_PERMISSION or FLAG_GRANT_PREFIX_URI_PERMISSION))
        dst.addFlags(passFlags)

        // ClipData
        if (src.clipData != null) {
            // Some pickers require ClipData to come along to maintain grants
            dst.clipData = cloneClipData(src.clipData!!)
        }

        // Extras (copy shallow — avoid classloader issues)
        if (src.extras != null) {
            try {
                dst.putExtras(src.extras!!)
            } catch (_: Throwable) {
                // Ignore any bad extras we can't parcel
            }
        }

        // Multiple selection passthrough hint
        if (src.getBooleanExtra(Intent.EXTRA_ALLOW_MULTIPLE, false)) {
            dst.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true)
        }

        // ACTION_PICK edge case: many FMs only respond to GET_CONTENT; map if necessary
        if (src.action == Intent.ACTION_PICK && !canResolve(dst)) {
            dst.action = Intent.ACTION_GET_CONTENT
            if (dst.type == null) dst.type = "*/*"
            dst.addCategory(CATEGORY_OPENABLE)
        }

        return dst
    }

    private fun cloneClipData(cd: ClipData): ClipData {
        val desc = cd.description
        val out = ClipData(desc, cd.getItemAt(0))
        for (i in 1 until cd.itemCount) out.addItem(cd.getItemAt(i))
        return out
    }

    private fun readPreferredPackage(): String? {
        // Prefer device-protected if available (works before user unlock on boot)
        val k = KEY_SELECTED_MANAGER
        val fromDevice = devicePrefs.getString(k, null)
        if (!fromDevice.isNullOrBlank()) return fromDevice
        val fromNormal = normalPrefs.getString(k, null)
        if (!fromNormal.isNullOrBlank()) return fromNormal
        return null
    }

    private fun isInstalled(pkg: String): Boolean =
        try {
            packageManager.getPackageInfo(pkg, 0)
            true
        } catch (_: PackageManager.NameNotFoundException) {
            false
        } catch (_: Throwable) {
            false
        }

    private fun canResolve(intent: Intent): Boolean =
        packageManager.resolveActivity(intent, PackageManager.MATCH_DEFAULT_ONLY) != null

    private fun deviceProtectedPrefs(context: Context) =
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                val de = context.createDeviceProtectedStorageContext()
                de.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            } else {
                context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            }
        } catch (_: Throwable) {
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        }

    // endregion

    companion object {
        private const val PREFS = "fm_redirect_prefs"
        private const val KEY_SELECTED_MANAGER = "key_selected_manager"

        // System DocumentsUI package (most AOSP/Pixel/Samsung, etc.)
        private const val SYSTEM_DOCS_UI = "com.android.documentsui"

        // Big list of popular file managers (no problem if some aren't installed)
        private val KNOWN_FILE_MANAGERS = listOf(
            // Google / AOSP
            "com.android.documentsui",
            "com.google.android.documentsui",
            "com.google.android.apps.nbu.files", // Files by Google

            // MiXplorer
            "com.mixplorer",
            "com.mixplorer.silver",

            // Solid Explorer
            "pl.solidexplorer2",

            // X-Plore
            "com.lonelycatgames.Xplore",

            // Root Explorer / Speed Software
            "com.speedsoftware.rootexplorer",
            "com.speedsoftware.explorer",

            // Amaze
            "com.amaze.filemanager",

            // CX File Explorer
            "com.cxinventor.file.explorer",

            // FX File Explorer
            "nextapp.fx",          // sometimes appears without com.
            "nextapp.fx.rk",       // root add-on
            "com.nextapp.fx",      // common id
            "nextapp.fx.plus",     // plus key

            // Material Files
            "me.zhanghai.android.files",

            // Total Commander
            "com.ghisler.android.TotalCommander",

            // Asus File Manager (varies by OEM)
            "com.asus.filemanager",

            // Samsung My Files
            "com.sec.android.app.myfiles",

            // OnePlus/Oppo/ColorOS File Manager
            "com.coloros.filemanager",
            "com.oneplus.filemanager",

            // Huawei
            "com.huawei.hidisk",

            // Xiaomi
            "com.mi.android.globalFileexplorer",
            "com.android.fileexplorer",

            // AOSP variants others ship
            "com.google.android.documentsui",
            "com.motorola.filemanager",
            "com.lge.filemanager",
            "com.htc.filemanager",

            // RS File
            "com.rs.explorer.filemanager",

            // File Manager+ (Flashlight + Clock)
            "com.alphainventor.filemanager",

            // Simple File Manager
            "com.simplemobiletools.filemanager.pro",
            "com.simplemobiletools.filemanager"

            // (Add more here freely – harmless if missing)
        )
    }
}