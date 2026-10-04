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
        // setsid = detach dari shell biar daemon tetep hidup setelah su exit
        val cmd = "pkill -f '$bin mitm' >/dev/null 2>&1; " +
            "sleep 0.2; " +
            "setsid $bin mitm ${net.iface} ${net.ip} ${net.mac} ${net.gateway} ${net.gatewayMac} " +
            "'$list' >/dev/null 2>&1 &"
        val r = RootShell.run(cmd, 10_000)
        return r.code == 0 || r.out.isBlank()
    }

    suspend fun stopMitm(context: Context) {
        val bin = binPath ?: return
        RootShell.run("pkill -f '$bin mitm' >/dev/null 2>&1", 5_000)
    }
}
