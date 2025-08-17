package com.example.filemanagerredirect

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.provider.Settings
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isVisible

class SettingsActivity : AppCompatActivity() {

    private lateinit var enableSwitch: Switch
    private lateinit var spinner: Spinner
    private lateinit var saveBtn: Button
    private lateinit var testRow: View
    private lateinit var testGetContent: Button
    private lateinit var testOpenDoc: Button
    private lateinit var testOpenTree: Button
    private lateinit var testCreateDoc: Button
    private lateinit var testPick: Button
    private lateinit var testPickImages: Button
    private lateinit var resetBtn: Button
    private lateinit var scopeHint: TextView

    private val prefs by lazy { SafePrefs.appPrefs(this) }

    private val knownManagers = FileManagerCatalog.PACKAGES

    override fun onCreate(savedInstanceState: Bundle?) {
        // Use AppCompat theme defined in AndroidManifest (Material3-compatible)
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        enableSwitch = findViewById(R.id.switchEnable)
        spinner = findViewById(R.id.spinnerManagers)
        saveBtn = findViewById(R.id.btnSave)
        testRow = findViewById(R.id.testRow)
        testGetContent = findViewById(R.id.btnTestGetContent)
        testOpenDoc = findViewById(R.id.btnTestOpenDocument)
        testOpenTree = findViewById(R.id.btnTestOpenTree)
        testCreateDoc = findViewById(R.id.btnTestCreateDocument)
        testPick = findViewById(R.id.btnTestPick)
        testPickImages = findViewById(R.id.btnTestPickImages)
        resetBtn = findViewById(R.id.btnReset)
        scopeHint = findViewById(R.id.txtScopeHint)

        // Load current state
        val enabled = prefs.getBoolean(Keys.PREF_ENABLED, true)
        val selected = prefs.getString(Keys.PREF_SELECTED_PKG, Keys.PKG_AUTO) ?: Keys.PKG_AUTO

        // Populate spinner with discovered + known
        val candidates = discoverManagers(this).toMutableList().apply {
            // Put AUTO at the top
            if (!contains(Keys.PKG_AUTO)) add(0, Keys.PKG_AUTO)
            // Merge known list for visibility
            knownManagers.forEach { if (!contains(it)) add(it) }
        }

        spinner.adapter = ArrayAdapter(
            this,
            android.R.layout.simple_spinner_dropdown_item,
            candidates.map { prettyNameFor(it) }
        )

        // Map display back to package
        fun indexOf(pkg: String): Int {
            val list = (spinner.adapter as ArrayAdapter<String>)
            for (i in 0 until list.count) {
                if (displayToPkg(list.getItem(i)!!) == pkg) return i
            }
            return 0
        }

        enableSwitch.isChecked = enabled
        spinner.setSelection(indexOf(selected))
        testRow.isVisible = enabled

        enableSwitch.setOnCheckedChangeListener { _, isChecked ->
            prefs.edit().putBoolean(Keys.PREF_ENABLED, isChecked).apply()
            testRow.isVisible = isChecked
            toast(if (isChecked) "Enabled" else "Disabled")
        }

        saveBtn.setOnClickListener {
            val chosenDisplay = spinner.selectedItem as String
            val pkg = displayToPkg(chosenDisplay)
            prefs.edit().putString(Keys.PREF_SELECTED_PKG, pkg).apply()
            toast("Saved: ${prettyNameFor(pkg)}")
        }

        resetBtn.setOnClickListener {
            prefs.edit()
                .putBoolean(Keys.PREF_ENABLED, true)
                .putString(Keys.PREF_SELECTED_PKG, Keys.PKG_AUTO)
                .apply()
            enableSwitch.isChecked = true
            spinner.setSelection(indexOf(Keys.PKG_AUTO))
            toast("Reset to defaults")
        }

        // Quick scope hint to reduce “not hooking” confusion
        scopeHint.setOnClickListener {
            try {
                startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                    data = Uri.parse("package:${packageName}")
                })
            } catch (_: Throwable) { }
        }

        // Test buttons (launch intents that our LSPosed hook should rewrite)
        testGetContent.setOnClickListener {
            startActivity(Intent(Intent.ACTION_GET_CONTENT).apply {
                type = "*/*"
                addCategory(Intent.CATEGORY_OPENABLE)
            })
        }

        testOpenDoc.setOnClickListener {
            startActivity(Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                type = "*/*"
                addCategory(Intent.CATEGORY_OPENABLE)
            })
        }

        testOpenTree.setOnClickListener {
            startActivity(Intent(Intent.ACTION_OPEN_DOCUMENT_TREE))
        }

        testCreateDoc.setOnClickListener {
            startActivity(Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_TITLE, "example.txt")
            })
        }

        testPick.setOnClickListener {
            @Suppress("DEPRECATION")
            startActivity(Intent(Intent.ACTION_PICK, MediaStore.Files.getContentUri("external")))
        }

        testPickImages.setOnClickListener {
            if (Build.VERSION.SDK_INT >= 33) {
                startActivity(Intent(MediaStore.ACTION_PICK_IMAGES))
            } else {
                toast("PICK_IMAGES requires Android 13+")
            }
        }
    }

    private fun discoverManagers(ctx: Context): List<String> {
        val pm = ctx.packageManager
        val set = linkedSetOf<String>()

        fun addFor(action: String, type: String? = null) {
            val i = Intent(action).apply {
                type?.let { this.type = it }
                addCategory(Intent.CATEGORY_DEFAULT)
            }
            val list = pm.queryIntentActivities(i, PackageManager.MATCH_DEFAULT_ONLY)
            list.forEach { set.add(it.activityInfo.packageName) }
        }

        // Probe common actions
        addFor(Intent.ACTION_OPEN_DOCUMENT, "*/*")
        addFor(Intent.ACTION_GET_CONTENT, "*/*")
        addFor(Intent.ACTION_OPEN_DOCUMENT_TREE)
        addFor(Intent.ACTION_CREATE_DOCUMENT, "*/*")
        if (Build.VERSION.SDK_INT >= 33) {
            addFor(MediaStore.ACTION_PICK_IMAGES)
        }

        // Seed with known managers to show them even if not currently resolvable
        knownManagers.forEach { set.add(it) }

        // Inject AUTO sentinel
        set.add(Keys.PKG_AUTO)
        return set.toList()
    }

    private fun prettyNameFor(pkg: String): String {
        if (pkg == Keys.PKG_AUTO) return "AUTO (best available)"
        return try {
            val ai = packageManager.getApplicationInfo(pkg, 0)
            packageManager.getApplicationLabel(ai).toString() + "  ($pkg)"
        } catch (_: Throwable) {
            pkg
        }
    }

    private fun displayToPkg(display: String): String {
        return if (display == "AUTO (best available)") Keys.PKG_AUTO
        else if (display.endsWith(")")) {
            // "... (com.pkg)"
            val start = display.lastIndexOf('(')
            val end = display.lastIndexOf(')')
            if (start != -1 && end != -1 && end > start) display.substring(start + 1, end) else display
        } else display
    }

    private fun toast(msg: String) = Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
}

/** Centralized keys & catalog (shared with hook). */
object Keys {
    const val PREFS = "fm_redirect_prefs"
    const val PREF_ENABLED = "enabled"
    const val PREF_SELECTED_PKG = "selected_pkg"
    const val PKG_AUTO = "__AUTO__"
}

object FileManagerCatalog {
    // Add/curate as you like
    val PACKAGES = listOf(
        "com.mixplorer",
        "com.mixplorer.silver",
        "com.google.android.apps.nbu.files",
        "pl.solidexplorer2",
        "com.lonelycatgames.Xplore",
        "com.speedsoftware.rootexplorer",
        "com.amaze.filemanager",
        "com.cxinventor.file.explorer", // CX
        "nextapp.fx",
        "com.ghisler.android.TotalCommander",
        "com.asus.filemanager",
        "com.sec.android.app.myfiles",
        "com.oneplus.filemanager",
        "me.zhanghai.android.files",
        "ru.zdevs.zarchiver",
        "com.alphainventor.filemanager", // File Manager+
        "com.transsion.filemanager",
        "com.huawei.hidisk",
        "com.mi.android.globalFileexplorer",
        "com.oppo.filemanager",
        "com.coloros.filemanager",
        "com.vivo.filemanager",
        "com.realme.filemanager",
        "com.motorola.filemanager",
        "com.htc.filemanager",
        "com.hmdglobal.app.filemanager",
        "com.lge.filemanager",
        "com.sonyericsson.android.filemanager",
        "com.microsoft.files",
        "com.file.manager",
        "com.files.fileexplorer"
    )
}

/** Device-protected prefs that survive system process contexts. */
object SafePrefs {
    fun appPrefs(ctx: Context) =
        try {
            val de = ctx.createDeviceProtectedStorageContext()
            de.getSharedPreferences(Keys.PREFS, Context.MODE_PRIVATE)
        } catch (_: Throwable) {
            ctx.getSharedPreferences(Keys.PREFS, Context.MODE_PRIVATE)
        }
}