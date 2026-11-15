package com.example.idkit

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.content.SharedPreferences
import android.os.Bundle
import android.view.View
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.widget.*
import java.security.SecureRandom

class MainActivity : Activity() {
    private lateinit var prefs: SharedPreferences

    private fun newSeed(): String {
        val b = ByteArray(32)
        SecureRandom().nextBytes(b)
        return b.joinToString("") { "%02x".format(it) }
    }

    private fun pickApp() {
        val pm = packageManager
        val launch = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val apps = pm.queryIntentActivities(launch, 0)
            .map { it.loadLabel(pm).toString() to it.activityInfo.packageName }
            .filter { it.second != packageName }
            .distinctBy { it.second }
            .sortedBy { it.first.lowercase() }
        val names = apps.map {
            (if (prefs.getBoolean("${it.second}.off", false)) "✗ " else "") + it.first
        }.toTypedArray()
        AlertDialog.Builder(this).setTitle("Choose app (✗ = spoofing off)")
            .setItems(names) { _, i -> appMenu(apps[i].first, apps[i].second) }
            .show()
    }

    private fun appMenu(label: String, pkg: String) {
        val off = prefs.getBoolean("$pkg.off", false)
        val opts = arrayOf(
            "Reset identity for this app",
            if (off) "Turn spoofing back on" else "Turn spoofing off for this app"
        )
        AlertDialog.Builder(this).setTitle(label).setItems(opts) { _, i ->
            if (i == 0) {
                prefs.edit().putInt("$pkg.n", prefs.getInt("$pkg.n", 0) + 1).apply()
            } else {
                prefs.edit().putBoolean("$pkg.off", !off).apply()
            }
            Toast.makeText(this, "Done. Force-stop that app to apply.", Toast.LENGTH_LONG).show()
        }.show()
    }

    override fun onCreate(s: Bundle?) {
        super.onCreate(s)
        prefs = Store.prefs(this)
        if (prefs.getString("seed", null) == null) {
            prefs.edit().putString("seed", newSeed()).apply()
        }

        val col = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(40, 40, 40, 40) }
        fun add(v: View) = col.addView(v, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT))
        fun label(t: String) = TextView(this).apply { text = t; setPadding(0, 16, 0, 16) }
        fun button(t: String, f: () -> Unit) = Button(this).apply { text = t; setOnClickListener { f() } }
        fun toggle(t: String, key: String) = Switch(this).apply {
            text = t
            isChecked = prefs.getBoolean(key, false)
            setOnCheckedChangeListener { _, on -> prefs.edit().putBoolean(key, on).apply() }
        }

        val status = TextView(this)
        fun refresh() { status.text = "Current identity: " + (prefs.getString("seed", "") ?: "").take(8) }
        refresh()

        add(label("Every app ticked in LSPosed scope is spoofed automatically, each with its own identity. Force-stop an app after changing anything here."))
        add(status)
        add(button("New identity (all apps)") {
            AlertDialog.Builder(this).setMessage("Create a completely new identity for every app?")
                .setPositiveButton("Create") { _, _ ->
                    prefs.edit().putString("seed", newSeed()).apply()
                    refresh()
                    Toast.makeText(this, "New identity created. Force-stop apps to apply.", Toast.LENGTH_LONG).show()
                }
                .setNegativeButton("Cancel", null).show()
        })
        add(toggle("Match region (timezone, language, carrier)", "region"))
        add(toggle("Spoof location (random spot in that region)", "loc"))
        add(button("Per-app options...") { pickApp() })
        setContentView(ScrollView(this).apply { addView(col) })
    }
}
