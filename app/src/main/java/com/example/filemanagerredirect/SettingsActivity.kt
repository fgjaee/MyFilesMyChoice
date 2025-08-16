package com.example.filemanagerredirect

import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.appcompat.app.AppCompatActivity

private const val PREFS = "fmredirect_prefs"
private const val KEY_ENABLED = "enabled"
private const val KEY_FALLBACK_TO_SYSTEM = "fallback"
private const val KEY_AUTO_FALLBACK = "auto_fallback"
private const val KEY_PREFERRED = "preferred_pkg"
private const val KEY_FALLBACK_LIST = "fallback_list"
private const val KEY_DEBUG_TOASTS = "debug_toasts"

data class AppEntry(val label: String, val packageName: String, val icon: Drawable)
class AppEntryAdapter(ctx: android.content.Context, private val items: List<AppEntry>)
    : ArrayAdapter<AppEntry>(ctx, android.R.layout.simple_list_item_single_choice, items) {
    override fun getCount() = items.size
    override fun getItem(position: Int) = items[position]
    override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
        val row = convertView ?: View.inflate(context, android.R.layout.simple_list_item_single_choice, null)
        val text = row.findViewById<TextView>(android.R.id.text1)
        val e = items[position]
        text.text = "${e.label}  (${e.packageName})"
        e.icon.setBounds(0, 0, text.lineHeight * 3 / 2, text.lineHeight * 3 / 2)
        text.setCompoundDrawables(e.icon, null, null, null)
        text.compoundDrawablePadding = (text.lineHeight * 0.4f).toInt()
        return row
    }
}

class SettingsActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val layout = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(32,32,32,32) }
        val enable = Switch(this).apply { text = getString(R.string.toggle_enable) }
        val fallbackToSystem = CheckBox(this).apply { text = getString(R.string.toggle_fallback) }
        val autoFallback = CheckBox(this).apply { text = "Auto fallback to another manager" }
        val debugToasts = CheckBox(this).apply { text = getString(R.string.toggle_debug) }
        val header = TextView(this).apply { text = getString(R.string.select_manager) }
        val list = ListView(this).apply { choiceMode = ListView.CHOICE_MODE_SINGLE }
        val save = Button(this).apply { text = getString(R.string.save) }
        val openTest = Button(this).apply { text = getString(R.string.test_screen) }

        layout.addView(enable)
        layout.addView(fallbackToSystem)
        layout.addView(autoFallback)
        layout.addView(debugToasts)
        layout.addView(header)
        layout.addView(list, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f))
        layout.addView(save)
        layout.addView(openTest)
        setContentView(layout)

        val sp = getSharedPreferences(PREFS, MODE_PRIVATE)
        enable.isChecked = sp.getBoolean(KEY_ENABLED, true)
        fallbackToSystem.isChecked = sp.getBoolean(KEY_FALLBACK_TO_SYSTEM, true)
        autoFallback.isChecked = sp.getBoolean(KEY_AUTO_FALLBACK, true)
        debugToasts.isChecked = sp.getBoolean(KEY_DEBUG_TOASTS, true)

        val knownPkgs = resources.getStringArray(R.array.known_file_manager_packages).toMutableSet()
        val pm = packageManager
        fun discover(action: String) {
            val probe = Intent(action).addCategory(Intent.CATEGORY_DEFAULT).setType("*/*")
            pm.queryIntentActivities(probe, PackageManager.MATCH_DEFAULT_ONLY)
                .map { it.activityInfo.packageName }
                .forEach { knownPkgs.add(it) }
        }
        discover(Intent.ACTION_GET_CONTENT)
        discover(Intent.ACTION_OPEN_DOCUMENT)
        discover(Intent.ACTION_OPEN_DOCUMENT_TREE)
        discover(Intent.ACTION_PICK)

        val installed = pm.getInstalledPackages(0).associateBy { it.packageName }
        val entries = knownPkgs.mapNotNull { pkg ->
            val pi = installed[pkg] ?: return@mapNotNull null
            val ai: ApplicationInfo = pi.applicationInfo ?: return@mapNotNull null
            val label = pm.getApplicationLabel(ai).toString()
            val icon = try { ai.loadIcon(pm) } catch (_: Throwable) { pm.defaultActivityIcon }
            AppEntry(label, pkg, icon)
        }.distinctBy { it.packageName }
         .sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.label })

        val adapter = AppEntryAdapter(this, entries)
        list.adapter = adapter

        sp.getString(KEY_PREFERRED, null)?.let { saved ->
            val idx = entries.indexOfFirst { it.packageName == saved }
            if (idx >= 0) list.setItemChecked(idx, true)
        }

        save.setOnClickListener {
            val chosen = entries.getOrNull(list.checkedItemPosition)?.packageName
            val fallbackList = buildList {
                if (chosen != null) add(chosen)
                entries.map { it.packageName }.filter { it != chosen }.forEach { add(it) }
            }.joinToString(",")

            sp.edit()
                .putBoolean(KEY_ENABLED, enable.isChecked)
                .putBoolean(KEY_FALLBACK_TO_SYSTEM, fallbackToSystem.isChecked)
                .putBoolean(KEY_AUTO_FALLBACK, autoFallback.isChecked)
                .putBoolean(KEY_DEBUG_TOASTS, debugToasts.isChecked)
                .putString(KEY_PREFERRED, chosen)
                .putString(KEY_FALLBACK_LIST, fallbackList)
                .apply()

            try {
                val deCtx = createDeviceProtectedStorageContext()
                deCtx.moveSharedPreferencesFrom(this, PREFS)
                val prefsFile = java.io.File(deCtx.applicationInfo.dataDir, "shared_prefs/$PREFS.xml")
                prefsFile.setReadable(true, false)
            } catch (_: Throwable) {}

            Toast.makeText(this, "Saved", Toast.LENGTH_SHORT).show()
        }

        openTest.setOnClickListener {
            startActivity(Intent(this, TestActivity::class.java))
        }
    }
}