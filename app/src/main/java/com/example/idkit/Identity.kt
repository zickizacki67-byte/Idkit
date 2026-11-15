package com.example.idkit

import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

class Dev(
    val name: String, val brand: String, val manufacturer: String,
    val model: String, val device: String, val product: String, val fingerprint: String
)

class Carrier(
    val name: String, val mccmnc: String, val country: String,
    val tz: String, val lang: String, val lat: Double, val lon: Double
)

class Identity(
    val androidId: String, val gsfDec: String, val imei: String, val imsi: String,
    val simSerial: String, val mac: String, val btMac: String, val serial: String,
    val adId: String, val dev: Dev, val carrier: Carrier, val lat: Double, val lon: Double
)

object Gen {
    private val devices = listOf(
        Dev("Google Pixel 8", "google", "Google", "Pixel 8", "shiba", "shiba",
            "google/shiba/shiba:14/UQ1A.240205.002/11224170:user/release-keys"),
        Dev("Google Pixel 7", "google", "Google", "Pixel 7", "panther", "panther",
            "google/panther/panther:14/UQ1A.240205.002/11224170:user/release-keys"),
        Dev("Samsung Galaxy S23", "samsung", "samsung", "SM-S911B", "dm1q", "dm1qxxx",
            "samsung/dm1qxxx/dm1q:14/UP1A.231005.007/S911BXXU3CXA1:user/release-keys"),
        Dev("Samsung Galaxy A54", "samsung", "samsung", "SM-A546B", "a54x", "a54xnsxx",
            "samsung/a54xnsxx/a54x:14/UP1A.231005.007/A546BXXU6CXA1:user/release-keys"),
        Dev("Xiaomi 13", "Xiaomi", "Xiaomi", "2211133G", "fuxi", "fuxi",
            "Xiaomi/fuxi/fuxi:14/UKQ1.230804.001/V816.0.5.0.UMCCNXM:user/release-keys"),
        Dev("OnePlus 11", "OnePlus", "OnePlus", "CPH2449", "OP594DL1", "CPH2449",
            "OnePlus/CPH2449/OP594DL1:14/UKQ1.230924.001/T.18d3f4f_2:user/release-keys")
    )

    private val carriers = listOf(
        Carrier("T-Mobile", "310260", "us", "America/New_York", "en", 40.7128, -74.0060),
        Carrier("Vodafone", "26202", "de", "Europe/Berlin", "de", 52.5200, 13.4050),
        Carrier("Orange", "20801", "fr", "Europe/Paris", "fr", 48.8566, 2.3522),
        Carrier("Airtel", "40410", "in", "Asia/Kolkata", "en", 28.6139, 77.2090),
        Carrier("Vodafone UK", "23415", "gb", "Europe/London", "en", 51.5074, -0.1278),
        Carrier("Telstra", "50501", "au", "Australia/Sydney", "en", -33.8688, 151.2093),
        Carrier("Rogers", "302720", "ca", "America/Toronto", "en", 43.6532, -79.3832),
        Carrier("Movistar", "21407", "es", "Europe/Madrid", "es", 40.4168, -3.7038)
    )

    private const val SERIAL_CHARS = "ABCDEFGHJKLMNPQRSTUVWXYZ0123456789"

    private fun h(seed: String, pkg: String, label: String): ByteArray {
        val m = Mac.getInstance("HmacSHA256")
        m.init(SecretKeySpec(seed.toByteArray(), "HmacSHA256"))
        return m.doFinal("$pkg|$label".toByteArray())
    }

    private fun u(b: Byte) = b.toInt() and 0xff

    private fun digits(b: ByteArray, n: Int) = (0 until n).map { u(b[it]) % 10 }.joinToString("")

    private fun luhn(d: String): Int {
        var sum = 0
        for (i in d.indices) {
            var x = d[i] - '0'
            if (i % 2 == 1) { x *= 2; if (x > 9) x -= 9 }
            sum += x
        }
        return (10 - sum % 10) % 10
    }

    private fun macBytes(b: ByteArray): ByteArray {
        val m = b.copyOf(6)
        m[0] = ((u(m[0]) and 0xFC) or 0x02).toByte()
        return m
    }

    fun identity(seed: String, pkg: String): Identity {
        val pick = h(seed, pkg, "pick")
        val c = carriers[u(pick[1]) % carriers.size]
        val imeiBody = "35" + digits(h(seed, pkg, "imei"), 12)
        val wifi = macBytes(h(seed, pkg, "wifi"))
        val bt = macBytes(h(seed, pkg, "bt"))
        val ad = h(seed, pkg, "ad").copyOf(16)
        ad[6] = ((u(ad[6]) and 0x0f) or 0x40).toByte()
        ad[8] = ((u(ad[8]) and 0x3f) or 0x80).toByte()
        val hex = ad.joinToString("") { "%02x".format(it) }
        val g = h(seed, pkg, "gsf")
        var gsf = 0L
        for (i in 0 until 8) gsf = (gsf shl 8) or u(g[i]).toLong()
        gsf = gsf and Long.MAX_VALUE
        val ser = h(seed, pkg, "serial")
        return Identity(
            androidId = h(seed, pkg, "aid").copyOf(8).joinToString("") { "%02x".format(it) },
            gsfDec = gsf.toString(),
            imei = imeiBody + luhn(imeiBody),
            imsi = c.mccmnc + digits(h(seed, pkg, "imsi"), 15 - c.mccmnc.length),
            simSerial = "89" + digits(h(seed, pkg, "iccid"), 17),
            mac = wifi.joinToString(":") { "%02x".format(it) },
            btMac = bt.joinToString(":") { "%02X".format(it) },
            serial = (0 until 10).map { SERIAL_CHARS[u(ser[it]) % SERIAL_CHARS.length] }.joinToString(""),
            adId = hex.substring(0, 8) + "-" + hex.substring(8, 12) + "-" + hex.substring(12, 16) +
                "-" + hex.substring(16, 20) + "-" + hex.substring(20),
            dev = devices[u(pick[0]) % devices.size],
            carrier = c,
            lat = c.lat + (u(pick[2]) / 255.0 - 0.5) * 0.04,
            lon = c.lon + (u(pick[3]) / 255.0 - 0.5) * 0.04
        )
    }
}
