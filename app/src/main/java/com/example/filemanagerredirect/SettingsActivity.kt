package com.example.filemanagerredirect

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SwitchCompat

private const val PREFS = "fm_redirect_prefs"
private const val KEY_ENABLED = "enabled"
private const val KEY_STEALTH = "stealth_mode"
private const val KEY_SELECTED = "selected_fm_pkg"
private const val KEY_FALLBACK_SAF = "fallback_saf"

class SettingsActivity : AppCompatActivity() {

    data class FM(val label: String, val packageName: String) {
        override fun toString(): String = "$label  ($packageName)"
    }

    // keep in sync with the hook’s list
    private val knownFms = listOf(
        "com.mixplorer", "com.mixplorer.silver",
        "pl.solidexplorer2",
        "com.lonelycatgames.Xplore",
        "com.speedsoftware.rootexplorer",
        "com.amaze.filemanager",
        "com.cxinventor.file.explorer",           // CX
        "nextapp.fx", "com.nextapp.fx",          // FX variants seen
        "me.zhanghai.android.files",
        "com.ghisler.android.TotalCommander",
        "com.oneplus.filemanager",
        "com.google.android.apps.nbu.files",
        "bin.mt.plus", "bin.mt.plus.canary",
        "ru.zdevs.zarchiver",
        "com.soft.explorer",
        "org.kman.AmazeFileUtilities"
    )

    private lateinit var switchEnable: SwitchCompat
    private lateinit var switchStealth: SwitchCompat
    private lateinit var switchSaf: SwitchCompat
    private lateinit var listView: ListView
    private lateinit var btnSave: View
    private lateinit var btnTest: View

    private val items = mutableListOf<FM>()
    private lateinit var adapter: ArrayAdapter<FM>

    private fun Context.safePrefs() = try {
        createDeviceProtectedStorageContext().getSharedPreferences(PREFS, MODE_PRIVATE)
    } catch (_: Throwable) {
        getSharedPreferences(PREFS, MODE_PRIVATE)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Make sure your app theme in Manifest is an AppCompat descendant (already handled elsewhere)
        setContentView(R.layout.activity_settings)

        // bind views (IDs must exist in activity_settings.xml)
        switchEnable = findViewById(R.id.switch_enable)
        switchStealth = findViewById(R.id.switch_stealth)
        switchSaf = findViewById(R.id.switch_saf)
        listView = findViewById(R.id.list_view)
        btnSave = findViewById(R.id.btn_save)
        btnTest = findViewById(R.id.btn_test)

        val sp = safePrefs()

        // load switches
        switchEnable.isChecked = sp.getBoolean(KEY_ENABLED, false)
        switchStealth.isChecked = sp.getBoolean(KEY_STEALTH, true)
        switchSaf.isChecked = sp.getBoolean(KEY_FALLBACK_SAF, true)

        // persist switches immediately
        switchEnable.setOnCheckedChangeListener { _, v ->
            sp.edit().putBoolean(KEY_ENABLED, v).apply()
        }
        switchStealth.setOnCheckedChangeListener { _, v ->
            sp.edit().putBoolean(KEY_STEALTH, v).apply()
        }
        switchSaf.setOnCheckedChangeListener { _, v ->
            sp.edit().putBoolean(KEY_FALLBACK_SAF, v).apply()
        }

        // build FM list (installed known + anything that claims to handle picker intents)
        items.clear()
        items.addAll(discoverFileManagers())

        adapter = ArrayAdapter(this, android.R.layout.simple_list_item_single_choice, items)
        listView.adapter = adapter
        listView.choiceMode = ListView.CHOICE_MODE_SINGLE  // <- correct constant

        // preselect from prefs
        val selectedPkg = sp.getString(KEY_SELECTED, "") ?: ""
        if (selectedPkg.isNotBlank()) {
            val idx = items.indexOfFirst { it.packageName == selectedPkg }
            if (idx >= 0) listView.setItemChecked(idx, true)
        }

        // tap-to-select saves instantly
        listView.setOnItemClickListener { _, _, position, _ ->
            val pkg = items.getOrNull(position)?.packageName ?: ""
            sp.edit().putString(KEY_SELECTED, pkg).apply()
        }

        // SAVE is just a friendly confirmation (everything already persisted)
        btnSave.setOnClickListener {
            Toast.makeText(this, "Saved", Toast.LENGTH_SHORT).show()
        }

        // Quick test: launches the platform picker flow your hook should redirect
        btnTest.setOnClickListener {
            val i = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                addCategory(Intent.CATEGORY_OPENABLE)
                type = "*/*"
            }
            try {
                startActivity(i)
            } catch (e: Exception) {
                Toast.makeText(this, "No activity found: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun discoverFileManagers(): List<FM> {
        val pm = packageManager
        val out = LinkedHashMap<String, String>()  // pkg -> label

        fun addIfInstalled(pkg: String) {
            try {
                val info = pm.getPackageInfo(pkg, 0)
                val label = pm.getApplicationLabel(info.applicationInfo)?.toString() ?: pkg
                out[pkg] = label
            } catch (_: PackageManager.NameNotFoundException) { /* not installed */ }
        }

        // 1) add installed from known list
        knownFms.forEach { addIfInstalled(it) }

        // 2) add anything that resolves file picker/viewer intents
        val probes = listOf(
            Intent(Intent.ACTION_OPEN_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType("*/*"),
            Intent(Intent.ACTION_GET_CONTENT).addCategory(Intent.CATEGORY_OPENABLE).setType("*/*"),
            Intent(Intent.ACTION_VIEW).setDataAndType(android.net.Uri.parse("content://dummy"), "*/*"),
            Intent("android.provider.action.PICK_IMAGES")
        )

        probes.forEach { intent ->
            pm.queryIntentActivities(intent, PackageManager.MATCH_DEFAULT_ONLY)
                ?.forEach { ri ->
                    val pkg = ri.activityInfo?.packageName ?: return@forEach
                    val label = ri.loadLabel(pm)?.toString() ?: pkg
                    if (!out.containsKey(pkg)) out[pkg] = label
                }
        }

        // map to list sorted by label
        return out.entries
            .map { FM(it.value, it.key) }
            .sortedBy { it.label.lowercase() }
    }
}