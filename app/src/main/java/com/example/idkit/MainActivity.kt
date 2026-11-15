package com.example.idkit

import android.app.Activity
import android.os.Bundle
import android.text.InputType
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.widget.*

class MainActivity : Activity() {
    private val fields = linkedMapOf("android_id" to "Android ID", "imei" to "IMEI", "mac" to "Wi-Fi MAC", "serial" to "Serial")
    private val inputs = mutableMapOf<String, EditText>()

    override fun onCreate(s: Bundle?) {
        super.onCreate(s)
        val prefs = Store.prefs(this)
        val col = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(40, 40, 40, 40) }
        val pkg = EditText(this).apply { hint = "Target package (e.g. com.example.app)"; inputType = InputType.TYPE_TEXT_VARIATION_URI }
        val enabled = Switch(this).apply { text = "Spoof for this app" }
        col.addView(pkg, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT))
        col.addView(enabled)
        for ((k, label) in fields) {
            val e = EditText(this).apply { hint = "$label (blank = real value)" }
            inputs[k] = e; col.addView(e, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT))
        }
        fun load() {
            val p = pkg.text.toString().trim()
            enabled.isChecked = prefs.getBoolean("$p.enabled", false)
            for ((k, e) in inputs) e.setText(prefs.getString("$p.$k", ""))
        }
        fun button(t: String, f: () -> Unit) = Button(this).apply { text = t; setOnClickListener { f() } }
        col.addView(button("Load profile") { load() })
        col.addView(button("Generate values") {
            inputs["android_id"]!!.setText(Store.androidId()); inputs["imei"]!!.setText(Store.imei())
            inputs["mac"]!!.setText(Store.mac()); inputs["serial"]!!.setText(Store.serial())
        })
        col.addView(button("Save") {
            val p = pkg.text.toString().trim()
            if (p.isEmpty()) { Toast.makeText(this, "Enter a package", Toast.LENGTH_SHORT).show(); return@button }
            prefs.edit().apply {
                putBoolean("$p.enabled", enabled.isChecked)
                for ((k, e) in inputs) putString("$p.$k", e.text.toString().trim())
            }.apply()
            Toast.makeText(this, "Saved. Force-stop the target app to apply.", Toast.LENGTH_LONG).show()
        })
        setContentView(ScrollView(this).apply { addView(col) })
    }
}
