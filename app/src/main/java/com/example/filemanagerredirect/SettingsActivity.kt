package com.example.filemanagerredirect
import android.content.pm.PackageManager
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.DocumentsContract
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.edit
import androidx.preference.*

class SettingsActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        setTheme(R.style.Theme_FileManagerRedirect) // uses your theme name
        super.onCreate(savedInstanceState)

        supportFragmentManager
            .beginTransaction()
            .replace(android.R.id.content, RootPrefs())
            .commit()
    }

    class RootPrefs : PreferenceFragmentCompat() {

        private val prefs by lazy {
            requireContext().getSharedPreferences(Keys.PREFS, MODE_PRIVATE)
        }

        override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
            preferenceManager.sharedPreferencesName = Keys.PREFS

            val screen = preferenceManager.createPreferenceScreen(requireContext())

            // Enable redirect
            val enable = SwitchPreferenceCompat(requireContext()).apply {
                key = Keys.KEY_ENABLED
                title = getString(R.string.enable_redirect)
                isChecked = prefs.getBoolean(Keys.KEY_ENABLED, true)
            }
            screen.addPreference(enable)

            // Stealth
            val stealth = SwitchPreferenceCompat(requireContext()).apply {
                key = Keys.KEY_STEALTH
                title = getString(R.string.stealth_mode)
                isChecked = prefs.getBoolean(Keys.KEY_STEALTH, false)
            }
            screen.addPreference(stealth)

            // Fallback to SAF
            val fallback = SwitchPreferenceCompat(requireContext()).apply {
                key = Keys.KEY_FALLBACK_SAF
                title = getString(R.string.fallback_saf)
                isChecked = prefs.getBoolean(Keys.KEY_FALLBACK_SAF, true)
            }
            screen.addPreference(fallback)

            // File manager chooser (ListPreference expects CharSequence[] types)
            val (entries, entryValues) = buildFmEntries()
            val fm = ListPreference(requireContext()).apply {
                key = Keys.KEY_PACKAGE
                title = getString(R.string.select_fm)
                this.entries = entries
                this.entryValues = entryValues
                value = prefs.getString(Keys.KEY_PACKAGE, null) ?: ""
                summaryProvider = ListPreference.SimpleSummaryProvider.getInstance()
            }
            screen.addPreference(fm)

            // Test buttons
            val test = Preference(requireContext()).apply {
                title = getString(R.string.btn_test)
                setOnPreferenceClickListener {
                    launchTestPicker()
                    true
                }
            }
            screen.addPreference(test)

            val save = Preference(requireContext()).apply {
                title = getString(R.string.btn_save)
                setOnPreferenceClickListener {
                    // Persist current values explicitly (ListPreference already writes its own)
                    prefs.edit {
                        putBoolean(Keys.KEY_ENABLED, enable.isChecked)
                        putBoolean(Keys.KEY_STEALTH, stealth.isChecked)
                        putBoolean(Keys.KEY_FALLBACK_SAF, fallback.isChecked)
                        putString(Keys.KEY_PACKAGE, fm.value ?: "")
                    }
                    true
                }
            }
            screen.addPreference(save)

            preferenceScreen = screen
        }

        private fun buildFmEntries(): Pair<Array<CharSequence>, Array<CharSequence>> {
            // Pull all (or installed) packages and build parallel arrays
            val pkgs = FileManagerCatalog.installedOrAll(requireContext())

            // Try to resolve app labels for nicer display; fall back to package name
            val pm = requireContext().packageManager
            val names = pkgs.map { pkg ->
                runCatching {
                    val appInfo = if (android.os.Build.VERSION.SDK_INT >= 33) {
                        pm.getApplicationInfo(pkg, PackageManager.ApplicationInfoFlags.of(0))
                    } else {
                        @Suppress("DEPRECATION")
                        pm.getApplicationInfo(pkg, 0)
                    }
                    pm.getApplicationLabel(appInfo)?.toString() ?: pkg
                }.getOrDefault(pkg)
            }

            val entries: Array<CharSequence> = names.toTypedArray()
            val entryValues: Array<CharSequence> = pkgs.toTypedArray()
            return entries to entryValues
        }

        private fun launchTestPicker() {
            // Simple ACTION_OPEN_DOCUMENT test to confirm redirect path
            val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                addCategory(Intent.CATEGORY_OPENABLE)
                type = "*/*"
            }
            // Use chooser title from strings
            startActivity(Intent.createChooser(intent, getString(R.string.chooser_title)))
        }
    }
}