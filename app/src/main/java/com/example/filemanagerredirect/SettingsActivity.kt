package com.example.filemanagerredirect

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.edit
import androidx.preference.ListPreference
import androidx.preference.MultiSelectListPreference
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.PreferenceManager
import androidx.preference.SwitchPreferenceCompat

/**
 * Keys must match what your hook class reads.
 */
object Prefs {
    const val PREFS_NAME = "file_redirect_prefs"

    const val KEY_ENABLED = "enabled"
    const val KEY_SCOPE_SYSTEM_FRAMEWORK = "scope_system_framework"  // hook only system server/framework
    const val KEY_PRIMARY_MANAGER = "primary_pkg"
    const val KEY_FALLBACK_MANAGERS = "fallback_pkgs"
    const val KEY_LAST_TEST_ACTION = "last_test_action"              // UX only
}

/**
 * Activity shells a single PreferenceFragment; no layout XML required.
 */
class SettingsActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        // Ensure Material3 DayNight style from your app theme is applied
        super.onCreate(savedInstanceState)

        // Show the fragment directly in the content view (no layout file needed)
        if (savedInstanceState == null) {
            supportFragmentManager
                .beginTransaction()
                .replace(android.R.id.content, SettingsFragment())
                .commit()
        }
    }

    /**
     * Preference fragment that builds the screen entirely in code.
     */
    class SettingsFragment : PreferenceFragmentCompat() {

        private lateinit var sp: SharedPreferences

        override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
            // Use Device Protected Storage for early availability to hooks
            val appCtx = requireContext().applicationContext
            val deCtx = try {
                appCtx.createDeviceProtectedStorageContext()
            } catch (_: Throwable) {
                appCtx
            }

            // Migrate from default prefs if needed (first run)
            val def = PreferenceManager.getDefaultSharedPreferences(appCtx)
            sp = deCtx.getSharedPreferences(Prefs.PREFS_NAME, Context.MODE_PRIVATE)
            if (!sp.contains(Prefs.KEY_ENABLED) && def.contains(Prefs.KEY_ENABLED)) {
                sp.edit {
                    putBoolean(Prefs.KEY_ENABLED, def.getBoolean(Prefs.KEY_ENABLED, true))
                    putBoolean(
                        Prefs.KEY_SCOPE_SYSTEM_FRAMEWORK,
                        def.getBoolean(Prefs.KEY_SCOPE_SYSTEM_FRAMEWORK, true)
                    )
                    putString(Prefs.KEY_PRIMARY_MANAGER, def.getString(Prefs.KEY_PRIMARY_MANAGER, null))
                    putStringSet(
                        Prefs.KEY_FALLBACK_MANAGERS,
                        def.getStringSet(Prefs.KEY_FALLBACK_MANAGERS, emptySet())
                    )
                }
            }

            // Build screen in code
            val screen = preferenceManager.createPreferenceScreen(requireContext())
            preferenceScreen = screen

            // Master enable switch
            val enabled = SwitchPreferenceCompat(requireContext()).apply {
                key = Prefs.KEY_ENABLED
                title = "Enable redirection"
                summary = "When on, picker intents are redirected to your chosen file manager"
                setDefaultValue(true)
                onPreferenceChangeListener = Preference.OnPreferenceChangeListener { _, newValue ->
                    sp.edit { putBoolean(Prefs.KEY_ENABLED, newValue as Boolean) }
                    true
                }
                // Load current
                isChecked = sp.getBoolean(Prefs.KEY_ENABLED, true)
            }
            screen.addPreference(enabled)

            // Scope option (safe default: only hook system framework)
            val scopeSystem = SwitchPreferenceCompat(requireContext()).apply {
                key = Prefs.KEY_SCOPE_SYSTEM_FRAMEWORK
                title = "Hook only system framework"
                summary = "Reduces detection by target apps. Turn off to be more aggressive."
                setDefaultValue(true)
                onPreferenceChangeListener = Preference.OnPreferenceChangeListener { _, newValue ->
                    sp.edit { putBoolean(Prefs.KEY_SCOPE_SYSTEM_FRAMEWORK, newValue as Boolean) }
                    true
                }
                isChecked = sp.getBoolean(Prefs.KEY_SCOPE_SYSTEM_FRAMEWORK, true)
            }
            screen.addPreference(scopeSystem)

            // Build choices from installed file managers
            val pm = requireContext().packageManager
            val managers = discoverFileManagers(pm)
            val (entries, values) = buildEntriesAndValues(pm, managers)

            // Primary manager list
            val primary = ListPreference(requireContext()).apply {
                key = Prefs.KEY_PRIMARY_MANAGER
                title = "Primary file manager"
                summaryProvider = ListPreference.SimpleSummaryProvider.getInstance()
                entries = entries
                entryValues = values
                isPersistent = false // we write manually to Device-Protected prefs
                setOnPreferenceChangeListener { _, newValue ->
                    sp.edit { putString(Prefs.KEY_PRIMARY_MANAGER, newValue as String) }
                    true
                }
                // Current value or default to system picker
                val current = sp.getString(Prefs.KEY_PRIMARY_MANAGER, SYSTEM_PICKER_PACKAGE)
                value = if (values.contains(current)) current else SYSTEM_PICKER_PACKAGE
            }
            screen.addPreference(primary)

            // Fallbacks multi-select
            val fallback = MultiSelectListPreference(requireContext()).apply {
                key = Prefs.KEY_FALLBACK_MANAGERS
                title = "Fallback managers"
                summary = "Used if primary isn’t installed or can’t handle a request"
                entries = entries
                entryValues = values
                isPersistent = false
                setOnPreferenceChangeListener { _, newValue ->
                    @Suppress("UNCHECKED_CAST")
                    val set = (newValue as? Set<String>) ?: emptySet()
                    sp.edit { putStringSet(Prefs.KEY_FALLBACK_MANAGERS, set) }
                    true
                }
                val current = sp.getStringSet(Prefs.KEY_FALLBACK_MANAGERS, emptySet()) ?: emptySet()
                values = current
            }
            screen.addPreference(fallback)

            // Test button (fires a neutral picker to see who opens)
            val test = Preference(requireContext()).apply {
                title = "Test picker"
                summary = "Launch a sample OPEN_DOCUMENT to verify redirection"
                setOnPreferenceClickListener {
                    sp.edit {
                        putString(Prefs.KEY_LAST_TEST_ACTION, Intent.ACTION_OPEN_DOCUMENT)
                    }
                    val i = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                        addCategory(Intent.CATEGORY_OPENABLE)
                        type = "*/*"
                    }
                    // Use this activity as a trampoline; your hook should rewrite it
                    startActivity(i)
                    true
                }
            }
            screen.addPreference(test)
        }

        /**
         * Return list of candidate file managers by querying common picker intents
         * and adding a curated set of known packages.
         */
        private fun discoverFileManagers(pm: PackageManager): Set<String> {
            val actions = listOf(
                Intent(Intent.ACTION_OPEN_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType("*/*"),
                Intent(Intent.ACTION_GET_CONTENT).addCategory(Intent.CATEGORY_OPENABLE).setType("*/*"),
                Intent(Intent.ACTION_CREATE_DOCUMENT).setType("*/*"),
                Intent(Intent.ACTION_OPEN_DOCUMENT_TREE),
                Intent(Intent.ACTION_PICK).setType("*/*")
            )

            val fromQueries = buildSet {
                actions.forEach { intent ->
                    val res = if (Build.VERSION.SDK_INT >= 33) {
                        pm.queryIntentActivities(intent, PackageManager.ResolveInfoFlags.of(0))
                    } else {
                        @Suppress("DEPRECATION")
                        pm.queryIntentActivities(intent, 0)
                    }
                    res.forEach { add(it.activityInfo.packageName) }
                }
            }

            return fromQueries + KNOWN_FILE_MANAGERS
        }

        /**
         * Build entries/values arrays for ListPreference & MultiSelectListPreference.
         * First option is the system picker, then discovered/known managers (sorted).
         */
        private fun buildEntriesAndValues(
            pm: PackageManager,
            pkgs: Set<String>
        ): Pair<Array<CharSequence>, Array<CharSequence>> {
            val display = pkgs
                .toMutableSet()
                .apply { add(SYSTEM_PICKER_PACKAGE) }
                .sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { labelFor(pm, it) })

            val entries = display.map { labelFor(pm, it) }.toMutableList()
            val values = display.toMutableList()

            // Ensure first entry is System picker
            val idx = values.indexOf(SYSTEM_PICKER_PACKAGE)
            if (idx > 0) {
                entries.add(0, entries.removeAt(idx))
                values.add(0, values.removeAt(idx))
            }

            return entries.toTypedArray() to values.toTypedArray()
        }

        private fun labelFor(pm: PackageManager, pkg: String): String {
            return if (pkg == SYSTEM_PICKER_PACKAGE) {
                "Android system picker"
            } else {
                try {
                    val appInfo = pm.getApplicationInfo(pkg, 0)
                    pm.getApplicationLabel(appInfo)?.toString() ?: pkg
                } catch (_: Exception) {
                    pkg
                }
            }
        }

        companion object {
            private const val SYSTEM_PICKER_PACKAGE = "com.android.documentsui"

            // Large, curated set (you can extend this; they’ll be filtered by ListPreference anyway)
            private val KNOWN_FILE_MANAGERS = setOf(
                // System variants
                "com.android.documentsui", "com.google.android.documentsui",
                // Google Files
                "com.google.android.apps.nbu.files",
                // MiXplorer
                "com.mixplorer", "com.mixplorer.silver",
                // Solid Explorer
                "pl.solidexplorer2",
                // X-plore
                "com.lonelycatgames.Xplore",
                // Root Explorer / Explorer
                "com.speedsoftware.rootexplorer", "com.speedsoftware.explorer",
                // Amaze
                "com.amaze.filemanager",
                // Cx File Explorer
                "com.cxinventor.file.explorer",
                // FX File Explorer
                "com.nextapp.fx", "nextapp.fx", "nextapp.fx.plus", "nextapp.fx.rk",
                // Material Files
                "me.zhanghai.android.files",
                // Total Commander
                "com.ghisler.android.TotalCommander",
                // OEMs
                "com.asus.filemanager", "com.sec.android.app.myfiles", "com.coloros.filemanager",
                "com.oneplus.filemanager", "com.huawei.hidisk", "com.mi.android.globalFileexplorer",
                "com.android.fileexplorer", "com.motorola.filemanager", "com.lge.filemanager",
                "com.htc.filemanager",
                // Misc / popular
                "com.rs.explorer.filemanager", "com.alphainventor.filemanager",
                "com.simplemobiletools.filemanager.pro", "com.simplemobiletools.filemanager"
            )
        }
    }
}