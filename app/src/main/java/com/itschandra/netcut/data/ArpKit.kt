package com.itschandra.netcut.data

import android.content.Context
import java.io.File

/**
 * Manager utk binary native `arpkit` (di-ship sebagai asset, diekstrak ke filesDir,
 * dieksekusi lewat su). Kalau gagal di device tertentu, fitur turun kelas
 * (scan via /proc/net/arp) tanpa crash.
 */
object ArpKit {

    @Volatile
    private var binPath: String? = null

    fun ensure(context: Context): String? {
        binPath?.let { return it }
        synchronized(this) {
            binPath?.let { return it }
            return try {
                val out = File(context.filesDir, "arpkit")
                if (!out.exists() || out.length() == 0L) {
                    context.assets.open("bin/arpkit").use { input ->
                        out.outputStream().use { input.copyTo(it) }
                    }
                }
                out.setExecutable(true, false)
                binPath = out.absolutePath
                out.absolutePath
            } catch (e: Exception) {
                null
            }
        }
    }

    suspend fun scan(context: Context, net: NetInfo, timeoutMs: Int = 2200): List<Pair<String, String>> {
        val bin = ensure(context) ?: return emptyList()
        val cmd = "$bin scan ${net.iface} ${net.ip} ${net.mac} $timeoutMs"
        val r = RootShell.run(cmd, timeoutMs + 6_000L)
        if (!r.ok && r.out.isBlank()) return emptyList()
        return r.out.lineSequence()
            .map { it.trim().split(Regex("\\s+")) }
            .filter { it.size == 2 && it[0].contains('.') && it[1].contains(':') }
            .filter { it[1].lowercase() != net.mac.lowercase() }
            .map { it[0] to it[1].lowercase() }
            .toList()
    }

    /** Start daemon poison. Return true kalau proses berhasil di-launch. */
    suspend fun startMitm(context: Context, net: NetInfo, targets: List<Pair<String, String>>): Boolean {
        val bin = ensure(context) ?: return false
        if (targets.isEmpty()) return true
        val list = targets.joinToString(",") { "${it.first}:${it.second}" }
        val pidFile = "${context.filesDir}/arpkit.pid"
        // kill existing daemon first
        RootShell.run("kill \$(cat $pidFile 2>/dev/null) 2>/dev/null; rm -f $pidFile", 3_000)
        RootShell.run("kill \$(pidof arpkit) 2>/dev/null", 3_000)
        kotlinx.coroutines.delay(300)
        // start daemon detached (gak di-wait)
        val cmd = "$bin mitm ${net.iface} ${net.ip} ${net.mac} ${net.gateway} ${net.gatewayMac} '$list'"
        val ok = RootShell.startDetached(cmd)
        if (ok) {
            kotlinx.coroutines.delay(500)
            // save pid dari pidof
            val pidR = RootShell.run("pidof arpkit", 2_000)
            val pid = pidR.out.trim().split("\\s+".toRegex()).firstOrNull() ?: ""
            if (pid.isNotEmpty()) RootShell.run("echo $pid > $pidFile", 2_000)
            return true
        }
        return false
    }

    suspend fun stopMitm(context: Context) {
        val pidFile = "${context.filesDir}/arpkit.pid"
        RootShell.run("kill \$(cat $pidFile 2>/dev/null) 2>/dev/null; rm -f $pidFile", 3_000)
        RootShell.run("kill \$(pidof arpkit) 2>/dev/null", 3_000)
    }
}
