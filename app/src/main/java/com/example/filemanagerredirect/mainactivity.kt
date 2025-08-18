package com.example.filemanagerredirect

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.Gravity
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {

    private val tag = "MainActivity"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Ultra-safe UI: no XML, no Material components. This avoids theme/layout crashes.
        val root = ScrollView(this)
        val col = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(48, 48, 48, 48)
            gravity = Gravity.CENTER_HORIZONTAL
        }
        root.addView(col)

        val title = TextView(this).apply {
            text = getString(R.string.app_name)
            textSize = 22f
        }
        val btnSettings = Button(this).apply {
            text = getString(R.string.title_activity_settings)
            setOnClickListener { openSettingsSafe() }
        }
        val btnTest = Button(this).apply {
            text = getString(R.string.btn_test)
            setOnClickListener { testRedirectSafe() }
        }

        col.addView(title)
        col.addView(space(24))
        col.addView(btnSettings)
        col.addView(space(12))
        col.addView(btnTest)

        setContentView(root)
    }

    private fun space(dp: Int) = TextView(this).apply {
        // simple spacer
        height = (dp * resources.displayMetrics.density).toInt()
    }

    private fun openSettingsSafe() {
        try {
            startActivity(Intent(this, SettingsActivity::class.java))
        } catch (t: Throwable) {
            Log.e(tag, "Failed to open SettingsActivity", t)
            Toast.makeText(this, "Couldn’t open Settings", Toast.LENGTH_LONG).show()
        }
    }

    private fun testRedirectSafe() {
        try {
            // Kick the RedirectActivity in a safe way; it will show chooser/system if needed.
            startActivity(Intent(this, RedirectActivity::class.java))
        } catch (t: Throwable) {
            Log.e(tag, "Failed to start RedirectActivity", t)
            Toast.makeText(this, "Couldn’t start redirect", Toast.LENGTH_LONG).show()
        }
    }
}