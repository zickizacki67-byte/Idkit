package com.example.idkit

import android.content.ContentResolver
import android.net.wifi.WifiInfo
import android.os.Build
import android.provider.Settings
import android.telephony.TelephonyManager
import de.robv.android.xposed.IXposedHookLoadPackage
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XSharedPreferences
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.XposedHelpers
import de.robv.android.xposed.callbacks.XC_LoadPackage

class Hooker : IXposedHookLoadPackage {
    override fun handleLoadPackage(lpparam: XC_LoadPackage.LoadPackageParam) {
        val pkg = lpparam.packageName
        if (pkg == "com.example.idkit") return
        val prefs = XSharedPreferences("com.example.idkit", Store.NAME).apply { reload() }
        if (!prefs.getBoolean("$pkg.enabled", false)) return

        fun value(key: String) = prefs.getString("$pkg.$key", null)?.takeIf { it.isNotBlank() }
        fun fixed(v: String?) = object : XC_MethodHook() {
            override fun afterHookedMethod(p: MethodHookParam) { if (v != null) p.result = v }
        }

        try {
            value("android_id")?.let { id ->
                XposedHelpers.findAndHookMethod(Settings.Secure::class.java, "getString",
                    ContentResolver::class.java, String::class.java, object : XC_MethodHook() {
                        override fun afterHookedMethod(p: MethodHookParam) {
                            if (p.args[1] == Settings.Secure.ANDROID_ID) p.result = id
                        }
                    })
            }
            value("imei")?.let { imei ->
                for (m in listOf("getImei", "getDeviceId"))
                    XposedBridge.hookAllMethods(TelephonyManager::class.java, m, fixed(imei))
            }
            value("mac")?.let {
                XposedBridge.hookAllMethods(WifiInfo::class.java, "getMacAddress", fixed(it))
            }
            value("serial")?.let {
                XposedBridge.hookAllMethods(Build::class.java, "getSerial", fixed(it))
                XposedHelpers.setStaticObjectField(Build::class.java, "SERIAL", it)
            }
        } catch (t: Throwable) {
            XposedBridge.log("IdKit[$pkg]: ${t.message}")
        }
    }
}
