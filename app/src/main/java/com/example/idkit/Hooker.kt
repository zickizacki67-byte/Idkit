package com.example.idkit

import android.bluetooth.BluetoothAdapter
import android.content.ContentResolver
import android.database.MatrixCursor
import android.location.Location
import android.net.Uri
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
import java.net.NetworkInterface
import java.util.Locale
import java.util.TimeZone

class Hooker : IXposedHookLoadPackage {
    override fun handleLoadPackage(lpparam: XC_LoadPackage.LoadPackageParam) {
        val pkg = lpparam.packageName
        if (pkg == "com.example.idkit" || pkg == "android") return
        val prefs = XSharedPreferences("com.example.idkit", Store.NAME).apply { reload() }
        val seed = prefs.getString("seed", null) ?: return
        if (prefs.getBoolean("$pkg.off", false)) return
        val id = Gen.identity(seed, pkg + "#" + prefs.getInt("$pkg.n", 0))
        val c = id.carrier
        XposedBridge.log("IdKit: spoofing $pkg")

        fun safe(f: () -> Unit) {
            try { f() } catch (t: Throwable) { XposedBridge.log("IdKit[$pkg]: ${t.message}") }
        }
        fun fixed(v: Any) = object : XC_MethodHook() {
            override fun afterHookedMethod(p: MethodHookParam) { p.result = v }
        }
        fun hookAll(cls: Class<*>, name: String, v: Any) {
            XposedBridge.hookAllMethods(cls, name, fixed(v))
        }

        val tm = TelephonyManager::class.java

        safe {
            XposedHelpers.findAndHookMethod(Settings.Secure::class.java, "getString",
                ContentResolver::class.java, String::class.java, object : XC_MethodHook() {
                    override fun afterHookedMethod(p: MethodHookParam) {
                        when (p.args[1]) {
                            Settings.Secure.ANDROID_ID -> p.result = id.androidId
                            "bluetooth_address" -> p.result = id.btMac
                        }
                    }
                })
        }
        safe { for (m in listOf("getImei", "getDeviceId")) hookAll(tm, m, id.imei) }
        safe { hookAll(tm, "getSubscriberId", id.imsi) }
        safe { hookAll(tm, "getSimSerialNumber", id.simSerial) }
        safe { hookAll(WifiInfo::class.java, "getMacAddress", id.mac) }
        safe { hookAll(BluetoothAdapter::class.java, "getAddress", id.btMac) }
        safe {
            val macB = id.mac.split(":").map { it.toInt(16).toByte() }.toByteArray()
            XposedBridge.hookAllMethods(NetworkInterface::class.java, "getHardwareAddress", object : XC_MethodHook() {
                override fun afterHookedMethod(p: MethodHookParam) {
                    if ((p.thisObject as NetworkInterface).name == "wlan0") p.result = macB
                }
            })
        }

        // Build fields: one coherent device
        val d = id.dev
        for ((f, v) in listOf("BRAND" to d.brand, "MANUFACTURER" to d.manufacturer, "MODEL" to d.model,
            "DEVICE" to d.device, "PRODUCT" to d.product, "FINGERPRINT" to d.fingerprint, "SERIAL" to id.serial)) {
            safe { XposedHelpers.setStaticObjectField(Build::class.java, f, v) }
        }
        safe { hookAll(Build::class.java, "getSerial", id.serial) }

        // Google advertising ID
        safe {
            val info = XposedHelpers.findClassIfExists(
                "com.google.android.gms.ads.identifier.AdvertisingIdClient\$Info", lpparam.classLoader)
            if (info != null) hookAll(info, "getId", id.adId)
        }

        // Google Services Framework ID
        safe {
            XposedBridge.hookAllMethods(ContentResolver::class.java, "query", object : XC_MethodHook() {
                override fun afterHookedMethod(p: MethodHookParam) {
                    val uri = p.args.getOrNull(0) as? Uri ?: return
                    if (!uri.toString().startsWith("content://com.google.android.gsf.gservices")) return
                    val asked = p.args.any { it is Array<*> && it.any { x -> x == "android_id" } }
                    if (asked) {
                        p.result = MatrixCursor(arrayOf("key", "value")).apply {
                            addRow(arrayOf<Any>("android_id", id.gsfDec))
                        }
                    }
                }
            })
        }

        // Optional: region (timezone, language, carrier)
        if (prefs.getBoolean("region", false)) {
            safe { TimeZone.setDefault(TimeZone.getTimeZone(c.tz)) }
            safe { Locale.setDefault(Locale(c.lang, c.country.uppercase())) }
            safe {
                hookAll(tm, "getNetworkOperatorName", c.name)
                hookAll(tm, "getSimOperatorName", c.name)
                hookAll(tm, "getNetworkOperator", c.mccmnc)
                hookAll(tm, "getSimOperator", c.mccmnc)
                hookAll(tm, "getNetworkCountryIso", c.country)
                hookAll(tm, "getSimCountryIso", c.country)
            }
        }

        // Optional: location
        if (prefs.getBoolean("loc", false)) {
            safe {
                hookAll(Location::class.java, "getLatitude", id.lat)
                hookAll(Location::class.java, "getLongitude", id.lon)
            }
        }
    }
}
