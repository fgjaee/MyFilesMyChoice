package com.example.filemanagerredirect

import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.preference.ListPreference
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.SwitchPreferenceCompat

class SettingsActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        supportFragmentManager.beginTransaction()
            .replace(android.R.id.content, RootPrefs()).commit()
    }

    class RootPrefs : PreferenceFragmentCompat() {
        override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
            preferenceManager.sharedPreferencesName = Prefs.PREFS_NAME
            val screen = preferenceManager.createPreferenceScreen(requireContext())
            screen.addPreference(SwitchPreferenceCompat(requireContext()).apply {
                key = Prefs.KEY_ENABLED
                title = "Enable MyFilesMyChoice"
                summary = "Master switch for supported file picking"
                setDefaultValue(true)
            })
            screen.addPreference(SwitchPreferenceCompat(requireContext()).apply {
                key = Prefs.KEY_GLOBAL_ENABLED
                title = "System-wide interception (LSPosed)"
                summary = "EXPERIMENTAL: automatically redirect DocumentsUI GET_CONTENT uploads; " +
                    "cancel the preferred manager to use the stock picker"
                setDefaultValue(false)
            })
            screen.addPreference(Preference(requireContext()).apply {
                title = "Required LSPosed scope"
                summary = "Enable this module for DocumentsUI (Google DocumentsUI or " +
                    "com.android.documentsui). Restart the picker process or reboot. " +
                    "No individual app scopes are needed."
                isSelectable = false
            })
            screen.addPreference(SwitchPreferenceCompat(requireContext()).apply {
                key = Prefs.KEY_FALLBACK_SAF
                title = getString(R.string.fallback_saf)
                setDefaultValue(true)
            })
            val installed = installedCandidates()
            val names = listOf("Auto (first compatible manager)") + installed.map { label(it) }
            screen.addPreference(ListPreference(requireContext()).apply {
                key = Prefs.KEY_PACKAGE
                title = getString(R.string.select_fm)
                entries = Array<CharSequence>(names.size) { names[it] }
                entryValues = Array<CharSequence>(installed.size + 1) {
                    if (it == 0) Prefs.PKG_AUTO else installed[it - 1]
                }
                setDefaultValue(Prefs.PKG_AUTO)
                summaryProvider = ListPreference.SimpleSummaryProvider.getInstance()
            })
            preferenceScreen = screen
        }

        private fun installedCandidates(): List<String> {
            val pm = requireContext().packageManager
            return resources.getStringArray(R.array.known_file_manager_packages)
                .distinct().filter { pkg ->
                    runCatching {
                        if (Build.VERSION.SDK_INT >= 33) {
                            pm.getApplicationInfo(pkg, PackageManager.ApplicationInfoFlags.of(0))
                        } else {
                            @Suppress("DEPRECATION")
                            pm.getApplicationInfo(pkg, 0)
                        }
                    }.isSuccess
                }.filterNot { it == requireContext().packageName }
        }

        private fun label(pkg: String): String = runCatching {
            val pm = requireContext().packageManager
            val ai = if (Build.VERSION.SDK_INT >= 33) {
                pm.getApplicationInfo(pkg, PackageManager.ApplicationInfoFlags.of(0))
            } else {
                @Suppress("DEPRECATION")
                pm.getApplicationInfo(pkg, 0)
            }
            pm.getApplicationLabel(ai).toString()
        }.getOrDefault(pkg)
    }
}
