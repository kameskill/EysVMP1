package com.example.eysvm

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.Typeface
import android.os.Bundle
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import com.example.eysvm.diagnostics.HostDiagnostics

class MainActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val pad = (16 * resources.displayMetrics.density).toInt()

        val report = try {
            HostDiagnostics.run(this).joinToString("\n") { "${it.name}: ${it.value}" }
        } catch (t: Throwable) {
            "Diagnostic failed: $t"
        }

        val text = TextView(this).apply {
            this.text = report
            typeface = Typeface.MONOSPACE
            textSize = 12f
            setTextIsSelectable(true)
        }
        val copy = Button(this).apply {
            this.text = "Copy report"
            setOnClickListener {
                (getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager)
                    .setPrimaryClip(ClipData.newPlainText("EysDiag", report))
                Toast.makeText(context, "Copied", Toast.LENGTH_SHORT).show()
            }
        }
        val col = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(pad, pad * 2, pad, pad)
            addView(copy)
            addView(text)
        }
        setContentView(ScrollView(this).apply {
            layoutParams = ViewGroup.LayoutParams(-1, -1)
            addView(col)
        })
    }
}
