package com.example.idkit

import android.annotation.SuppressLint
import android.content.Context
import android.content.SharedPreferences
import java.security.SecureRandom

object Store {
    const val NAME = "profiles"
    private val rnd = SecureRandom()

    @Suppress("DEPRECATION")
    @SuppressLint("WorldReadableFiles")
    fun prefs(c: Context): SharedPreferences = try {
        c.getSharedPreferences(NAME, Context.MODE_WORLD_READABLE) // works when module is active in LSPosed
    } catch (e: SecurityException) {
        c.getSharedPreferences(NAME, Context.MODE_PRIVATE)
    }

    fun androidId() = ByteArray(8).also(rnd::nextBytes).joinToString("") { "%02x".format(it) }

    fun imei(): String {
        val d = IntArray(14) { rnd.nextInt(10) }
        d[0] = 3; d[1] = 5
        var sum = 0
        for (i in d.indices) { var x = d[i]; if (i % 2 == 1) { x *= 2; if (x > 9) x -= 9 }; sum += x }
        return d.joinToString("") + ((10 - sum % 10) % 10)
    }

    fun mac(): String {
        val b = ByteArray(6).also(rnd::nextBytes)
        b[0] = ((b[0].toInt() and 0xFC) or 0x02).toByte() // locally administered, unicast
        return b.joinToString(":") { "%02x".format(it) }
    }

    fun serial() = (1..10).map { "ABCDEFGHJKLMNPQRSTUVWXYZ0123456789"[rnd.nextInt(34)] }.joinToString("")
}
