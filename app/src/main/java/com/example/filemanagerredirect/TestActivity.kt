package com.example.filemanagerredirect

import android.content.Intent
import android.os.Bundle
import android.provider.MediaStore
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

class TestActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val root = ScrollView(this)
        val col = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(32,32,32,32) }
        root.addView(col)

        fun add(label: String, click: () -> Unit) {
            col.addView(Button(this).apply { text = label; setOnClickListener { click() } })
        }

        col.addView(TextView(this).apply { text = "Try each. Your chosen file manager should open + a toast should show." })

        add("GET_CONTENT (*/*)") {
            startActivity(Intent(Intent.ACTION_GET_CONTENT).addCategory(Intent.CATEGORY_OPENABLE).setType("*/*"))
        }
        add("OPEN_DOCUMENT (*/*)") {
            startActivity(Intent(Intent.ACTION_OPEN_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType("*/*"))
        }
        add("OPEN_DOCUMENT_TREE") {
            startActivity(Intent(Intent.ACTION_OPEN_DOCUMENT_TREE))
        }
        add("CREATE_DOCUMENT (text/plain)") {
            val i = Intent(Intent.ACTION_CREATE_DOCUMENT)
                .addCategory(Intent.CATEGORY_OPENABLE)
                .setType("text/plain")
                .putExtra(Intent.EXTRA_TITLE, "test.txt")
            startActivity(i)
        }
        add("PICK (image/*)") {
            startActivity(Intent(Intent.ACTION_PICK).setType("image/*"))
        }
        add("PICK_IMAGES (API 33+)") {
            startActivity(Intent(MediaStore.ACTION_PICK_IMAGES))
        }

        setContentView(root)
    }
}
