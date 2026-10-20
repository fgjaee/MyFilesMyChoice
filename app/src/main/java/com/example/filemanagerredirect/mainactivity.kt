package com.example.filemanagerredirect

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity

/** On-device smoke test verifies readability of returned content URIs. */
class MainActivity : AppCompatActivity() {
    private lateinit var status: TextView

    private val picker = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode != Activity.RESULT_OK) {
            status.text = "Selection canceled or unavailable"
            return@registerForActivityResult
        }
        val uri = result.data?.data ?: result.data?.clipData?.getItemAt(0)?.uri
        if (uri == null) {
            status.text = "FAIL: No URI returned"
            return@registerForActivityResult
        }
        status.text = try {
            val first = contentResolver.openInputStream(uri)?.use { it.read() }
            if (first == null) "FAIL: Cannot open selected URI: $uri"
            else "PASS: Returned and readable ($uri)"
        } catch (e: Exception) {
            "FAIL: URI returned but unreadable: ${e.message}"
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val col = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            val p = (24 * resources.displayMetrics.density).toInt()
            setPadding(p, p, p, p)
        }
        status = TextView(this).apply {
            text = "Select a file; MyFilesMyChoice will verify the returned URI."
            textSize = 14f
        }
        col.addView(TextView(this).apply {
            text = "MyFilesMyChoice\nMy files. My choice."
            textSize = 22f
        })
        col.addView(Button(this).apply {
            text = "Settings"
            setOnClickListener {
                startActivity(Intent(this@MainActivity, SettingsActivity::class.java))
            }
        })
        col.addView(Button(this).apply {
            text = "Test MyFilesMyChoice bridge"
            setOnClickListener {
                picker.launch(Intent(this@MainActivity, RedirectActivity::class.java).apply {
                    action = Intent.ACTION_GET_CONTENT
                    type = "*/*"
                    addCategory(Intent.CATEGORY_OPENABLE)
                })
            }
        })
        col.addView(Button(this).apply {
            text = "Test Android system picker"
            setOnClickListener {
                picker.launch(Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                    type = "*/*"
                    addCategory(Intent.CATEGORY_OPENABLE)
                })
            }
        })
        col.addView(status)
        setContentView(ScrollView(this).apply { addView(col) })
    }
}
