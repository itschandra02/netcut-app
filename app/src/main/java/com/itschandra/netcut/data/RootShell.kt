package com.itschandra.netcut.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.File
import java.util.concurrent.TimeUnit

/** Wrapper eksekusi perintah root (`su -c`). Aman gagal — gak pernah nge-throw. */
object RootShell {

    /** Eksekusi dengan timeout. Blocking tapi dipanggil dari dispatcher IO. */
    private fun exec(cmd: String, timeoutMs: Long): ShellResult {
        return try {
            val pb = ProcessBuilder("su", "-c", cmd)
            pb.redirectErrorStream(true)
            val proc = pb.start()
            val out = StringBuilder()
            val t = Thread {
                try {
                    BufferedReader(proc.inputStream.reader()).use { r ->
                        var line = r.readLine()
                        while (line != null) {
                            synchronized(out) { out.append(line).append('\n') }
                            line = r.readLine()
                        }
                    }
                } catch (_: Exception) {}
            }
            t.isDaemon = true
            t.start()
            val done = proc.waitFor(timeoutMs, TimeUnit.MILLISECONDS)
            if (!done) {
                proc.destroy()
                t.join(500)
                return ShellResult(-1, synchronized(out) { out.toString() })
            }
            t.join(1500)
            ShellResult(proc.exitValue(), synchronized(out) { out.toString() })
        } catch (e: Exception) {
            ShellResult(-1, e.message ?: "exec failed")
        }
    }

    suspend fun run(cmd: String, timeoutMs: Long = 15_000): ShellResult =
        withContext(Dispatchers.IO) { exec(cmd, timeoutMs) }

    fun runBlocking(cmd: String, timeoutMs: Long = 8_000): ShellResult = exec(cmd, timeoutMs)

    fun hasRoot(): Boolean = runBlocking("id", 5_000).out.contains("uid=0")

    /** Start proses background yang gak di-wait (daemon). Return true kalau berhasil. */
    fun startDetached(cmd: String): Boolean {
        return try {
            val pb = ProcessBuilder("su", "-c", "$cmd >/dev/null 2>&1")
            pb.redirectErrorStream(false)
            pb.redirectOutput(ProcessBuilder.Redirect.to(File("/dev/null")))
            pb.redirectError(ProcessBuilder.Redirect.to(File("/dev/null")))
            pb.start()
            true
        } catch (e: Exception) {
            false
        }
    }
}
