package com.itschandra.netcut.data

import android.content.Context
import android.net.InetAddresses
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.net.Inet4Address
import java.net.NetworkInterface
import java.util.concurrent.ConcurrentHashMap

/**
 * Orchestrator semua fungsi netcut:
 *  - scan LAN            : arpkit (fallback /proc/net/arp)
 *  - monitor bandwidth   : counter iptables chain NETCUT_ACC (kernel-side)
 *  - block/unblock       : iptables DROP + ARP mitm
 *  - speed limit         : tc HTB per-IP
 *  - mitm                : daemon arpkit (poison unicast)
 *  - auto network watch  : reset state kalau WiFi/IP/MAC berubah
 */
class NetcutRepository(private val context: Context) {

    private val devices = LinkedHashMap<String, Device>()
    private val accPrev = HashMap<String, LongArray>()   // ip -> [rx, tx, timestampMs]
    private val vendorCache = ConcurrentHashMap<String, String>()
    private val logs = ArrayDeque<String>()
    private var lastMitmTargets: String = ""
    private var limits = HashMap<String, Int>()

    var net = NetInfo()
        private set
    var mitmOn = true
        private set
    private var rootOk = false
    private var forwardingReady = false

    // ---------------------------------------------------------------- net info
    private fun readNet(): NetInfo {
        var iface = ""
        var ip = ""
        var mac = ""
        // Method 1: su + ip (paling reliable di semua device termasuk MIUI)
        try {
            val r = RootShell.runBlocking("ip -4 addr show 2>/dev/null | grep -B2 'inet ' | grep -E '^[0-9]+:' | head -3", 5_000)
            r.out.lineSequence().forEach { line ->
                val name = line.trim().split(':').getOrNull(1)?.trim() ?: ""
                if (name.startsWith("wlan") && iface.isEmpty()) iface = name
            }
            if (iface.isNotEmpty()) {
                val r2 = RootShell.runBlocking(
                    "ip -4 addr show $iface 2>/dev/null | grep 'inet ' | awk '{print \$2}' | cut -d/ -f1 | head -1",
                    4_000
                )
                ip = r2.out.trim().lines().firstOrNull { it.matches(Regex("^\\d+\\.\\d+\\.\\d+\\.\\d+$")) } ?: ""
                val r3 = RootShell.runBlocking("cat /sys/class/net/$iface/address 2>/dev/null", 3_000)
                mac = r3.out.trim().lines().firstOrNull { it.contains(':') } ?: ""
            }
        } catch (_: Exception) {}

        // Method 2: Java NetworkInterface (fallback)
        if (iface.isEmpty() || ip.isEmpty() || mac.isEmpty()) {
            try {
                val nifs = NetworkInterface.getNetworkInterfaces()?.toList().orEmpty()
                val candidates = nifs.filter { !it.isLoopback && it.inetAddresses.hasMoreElements() }
                val pick = candidates.firstOrNull { it.name.startsWith("wlan") }
                    ?: candidates.firstOrNull { it.hardwareAddress != null }
                    ?: candidates.firstOrNull()
                if (pick != null) {
                    if (iface.isEmpty()) iface = pick.name
                    if (mac.isEmpty()) mac = pick.hardwareAddress?.joinToString(":") { "%02x".format(it) } ?: ""
                    if (ip.isEmpty()) {
                        pick.inetAddresses.toList().forEach { a ->
                            if (a is Inet4Address && ip.isEmpty()) ip = a.hostAddress ?: ""
                        }
                    }
                }
            } catch (_: Exception) {}
        }

        // Method 3: getprop (last resort utk interface name)
        if (iface.isEmpty()) {
            try {
                val r = RootShell.runBlocking("getprop wifi.interface", 3_000)
                iface = r.out.trim().lines().firstOrNull { it.isNotEmpty() } ?: "wlan0"
                if (ip.isEmpty()) {
                    val r2 = RootShell.runBlocking("ip -4 addr show $iface | grep 'inet ' | awk '{print \$2}' | cut -d/ -f1", 4_000)
                    ip = r2.out.trim().lines().firstOrNull { it.matches(Regex("^\\d+\\.\\d+\\.\\d+\\.\\d+$")) } ?: ""
                }
                if (mac.isEmpty()) {
                    val r3 = RootShell.runBlocking("cat /sys/class/net/$iface/address", 3_000)
                    mac = r3.out.trim().lines().firstOrNull { it.contains(':') } ?: ""
                }
            } catch (_: Exception) {}
        }

        var gateway = ""
        if (iface.isNotEmpty()) {
            val r1 = RootShell.runBlocking("ip route show table $iface", 4_000)
            Regex("default via (\\d+\\.\\d+\\.\\d+\\.\\d+)").find(r1.out)?.let { gateway = it.groupValues[1] }
            if (gateway.isEmpty()) {
                val r2 = RootShell.runBlocking("getprop dhcp.$iface.gateway", 3_000)
                val g = r2.out.trim()
                if (Regex("^\\d+\\.\\d+\\.\\d+\\.\\d+$").matches(g)) gateway = g
            }
            if (gateway.isEmpty() && ip.isNotEmpty()) gateway = ip.substringBeforeLast('.') + ".1"
        }

        var gatewayMac = ""
        if (gateway.isNotEmpty()) {
            val r = RootShell.runBlocking("cat /proc/net/arp", 3_000)
            r.out.lineSequence().forEach { line ->
                val p = line.trim().split(Regex("\\s+"))
                if (p.size >= 4 && p[0] == gateway && p[3] != "00:00:00:00:00:00") gatewayMac = p[3]
            }
        }
        return NetInfo(iface, ip, mac, gateway, gatewayMac.lowercase())
    }

    // ---------------------------------------------------------------- infra
    private suspend fun ensureForwarding() {
        val n = net
        if (!n.ready || n.gateway.isEmpty()) return
        RootShell.run("echo 1 > /proc/sys/net/ipv4/ip_forward")
        RootShell.run("echo 0 > /proc/sys/net/ipv4/conf/${n.iface}/send_redirects")
        RootShell.run("iptables -t nat -C POSTROUTING -o ${n.iface} -j MASQUERADE 2>/dev/null || " +
            "iptables -t nat -A POSTROUTING -o ${n.iface} -j MASQUERADE")
        RootShell.run("ip route flush table 901")
        RootShell.run("ip route add ${n.prefix}.0/24 dev ${n.iface} table 901")
        RootShell.run("ip route add default via ${n.gateway} dev ${n.iface} table 901")
        RootShell.run("ip rule del iif ${n.iface} lookup 901 priority 15000 2>/dev/null; " +
            "ip rule add iif ${n.iface} lookup 901 priority 15000")
        RootShell.run("iptables -C FORWARD -i ${n.iface} -o ${n.iface} -j ACCEPT 2>/dev/null || " +
            "iptables -I FORWARD 1 -i ${n.iface} -o ${n.iface} -j ACCEPT")
        RootShell.run("iptables -N NETCUT_ACC 2>/dev/null; " +
            "iptables -C FORWARD -j NETCUT_ACC 2>/dev/null || iptables -I FORWARD 1 -j NETCUT_ACC")
        forwardingReady = true
    }

    /** Rebuild accounting rules sesuai device list (2 rule/device). */
    private suspend fun syncAccounting() {
        val ips = devices.keys.filter { it != net.ip && it != net.gateway }
        val sb = StringBuilder("iptables -F NETCUT_ACC; ")
        ips.forEach {
            sb.append("iptables -A NETCUT_ACC -s $it -j RETURN; ")
            sb.append("iptables -A NETCUT_ACC -d $it -j RETURN; ")
        }
        RootShell.run(sb.toString(), 10_000)
    }

    /** Baca counter per-IP (rx=download bytes, tx=upload bytes). */
    private suspend fun readAccounting(): Map<String, LongArray> {
        val r = RootShell.run("iptables -L NETCUT_ACC -v -x -n", 8_000)
        val out = HashMap<String, LongArray>()
        r.out.lineSequence().forEach { line ->
            val p = line.trim().split(Regex("\\s+"))
            // format: num pkts bytes target prot opt in out source destination
            if (p.size >= 10 && p[0].all { it.isDigit() }) {
                val bytes = p[2].toLongOrNull() ?: return@forEach
                val src = p[8]
                val dst = p[9]
                if (src != "0.0.0.0/0") {
                    val ip = src.removeSuffix("/32")
                    out.getOrPut(ip) { LongArray(2) }[1] = bytes   // tx/upload
                } else if (dst != "0.0.0.0/0") {
                    val ip = dst.removeSuffix("/32")
                    out.getOrPut(ip) { LongArray(2) }[0] = bytes   // rx/download
                }
            }
        }
        return out
    }

    // ---------------------------------------------------------------- vendor
    private fun lookupVendor(mac: String): String {
        vendorCache[mac]?.let { return it }
        val prefix = mac.replace(":", "").take(6).uppercase()
        // cache kecil lokal utk OUI umum — selebihnya online (best effort)
        val local = OUI[prefix] ?: "Unknown"
        vendorCache[mac] = local
        return local
    }

    // ---------------------------------------------------------------- totals
    private fun ifaceTotals(): Pair<Long, Long> {
        return try {
            File("/proc/net/dev").readLines()
                .firstOrNull { it.trim().startsWith("${net.iface}:") }
                ?.let { line ->
                    val p = line.trim().removePrefix("${net.iface}:").trim().split(Regex("\\s+"))
                    (p[0].toLongOrNull() ?: 0L) to (p[8].toLongOrNull() ?: 0L)
                } ?: (0L to 0L)
        } catch (_: Exception) { 0L to 0L }
    }

    // ---------------------------------------------------------------- actions
    suspend fun scanNow() = withContext(Dispatchers.IO) {
        if (net.ip.isEmpty() && net.iface.isEmpty()) return@withContext
        log("scan started (${net.iface} ${net.ip})")
        val found = ArpKit.scan(context, net)
        if (found.isEmpty()) {
            // fallback: sweep ping + /proc/net/arp
            val prefix = net.prefix.ifEmpty { net.ip.substringBeforeLast('.', "") }
            if (prefix.isNotEmpty()) {
                RootShell.run(
                    (1..254).joinToString(" ") { "ping -c1 -W1 $prefix.$it" } + " >/dev/null 2>&1",
                    20_000
                )
            }
            val r = RootShell.run("cat /proc/net/arp", 3_000)
            r.out.lineSequence().drop(1).forEach { line ->
                val p = line.trim().split(Regex("\\s+"))
                if (p.size >= 4 && p[0] != net.ip && p[3] != "00:00:00:00:00:00") {
                    addDevice(p[0], p[3].lowercase())
                }
            }
        } else {
            found.forEach { (ip, mac) -> addDevice(ip, mac) }
        }
        syncAccounting()
        restartMitmIfChanged()
        log("scan done: ${devices.size} device(s)")
    }

    private fun addDevice(ip: String, mac: String) {
        val old = devices[ip]
        devices[ip] = (old ?: Device(ip = ip, mac = mac, firstSeen = System.currentTimeMillis()))
            .copy(mac = mac, online = true, lastSeen = System.currentTimeMillis())
        if (old == null) devices[ip] = devices[ip]!!.copy(vendor = lookupVendor(mac))
    }

    suspend fun setBlock(ip: String, on: Boolean) = withContext(Dispatchers.IO) {
        if (on) {
            ensureForwarding()
            RootShell.run("iptables -I FORWARD 1 -s $ip -j DROP; iptables -I FORWARD 1 -d $ip -j DROP")
            devices[ip]?.let { devices[ip] = it.copy(blocked = true) }
            log("BLOCKED $ip")
        } else {
            RootShell.run("iptables -D FORWARD -s $ip -j DROP 2>/dev/null; " +
                "iptables -D FORWARD -d $ip -j DROP 2>/dev/null; " +
                "iptables -D FORWARD -s $ip -j DROP 2>/dev/null; " +
                "iptables -D FORWARD -d $ip -j DROP 2>/dev/null")
            devices[ip]?.let { devices[ip] = it.copy(blocked = false) }
            log("UNBLOCKED $ip")
        }
        restartMitmIfChanged()
    }

    suspend fun setLimit(ip: String, kbps: Int) = withContext(Dispatchers.IO) {
        if (kbps > 0) {
            ensureForwarding()
            limits[ip] = kbps
            devices[ip]?.let { devices[ip] = it.copy(limitKbps = kbps) }
            log("LIMIT $ip -> ${kbps}kbps")
        } else {
            limits.remove(ip)
            devices[ip]?.let { devices[ip] = it.copy(limitKbps = 0) }
            log("LIMIT removed $ip")
        }
        rebuildTc()
        restartMitmIfChanged()
    }

    private suspend fun rebuildTc() {
        val n = net
        RootShell.run("tc qdisc del dev ${n.iface} root 2>/dev/null")
        if (limits.isEmpty()) return
        val sb = StringBuilder()
        sb.append("tc qdisc add dev ${n.iface} root handle 1: htb default 999; ")
        sb.append("tc class add dev ${n.iface} parent 1: classid 1:999 htb rate 1000mbit ceil 1000mbit; ")
        var idx = 10
        limits.forEach { (ip, kbps) ->
            val rate = "${maxOf(kbps, 8)}kbit"
            val up = "1:$idx"
            val down = "1:${idx + 500}"
            sb.append("tc class add dev ${n.iface} parent 1: classid $up htb rate $rate ceil $rate; ")
            sb.append("tc class add dev ${n.iface} parent 1: classid $down htb rate $rate ceil $rate; ")
            sb.append("tc filter add dev ${n.iface} parent 1: protocol ip prio 1 u32 match ip src $ip/32 flowid $up; ")
            sb.append("tc filter add dev ${n.iface} parent 1: protocol ip prio 2 u32 match ip dst $ip/32 flowid $down; ")
            idx++
        }
        RootShell.run(sb.toString(), 12_000)
    }

    suspend fun setMitm(on: Boolean) = withContext(Dispatchers.IO) {
        mitmOn = on
        if (on) {
            ensureForwarding()
            restartMitmIfChanged(force = true)
            log("MITM ON")
        } else {
            ArpKit.stopMitm(context)
            lastMitmTargets = ""
            log("MITM OFF")
        }
    }

    private suspend fun restartMitmIfChanged(force: Boolean = false) {
        if (!mitmOn || net.ip.isEmpty()) return
        // resolve gateway MAC kalau belum ada (ping gateway buat populate ARP)
        if (net.gatewayMac.isEmpty() && net.gateway.isNotEmpty()) {
            RootShell.run("ping -c1 -W1 ${net.gateway} >/dev/null 2>&1", 3_000)
            val r = RootShell.run("cat /proc/net/arp", 3_000)
            r.out.lineSequence().forEach { line ->
                val p = line.trim().split(Regex("\\s+"))
                if (p.size >= 4 && p[0] == net.gateway && p[3] != "00:00:00:00:00:00") {
                    net = net.copy(gatewayMac = p[3].lowercase())
                }
            }
        }
        if (net.gatewayMac.isEmpty() || net.gateway.isEmpty()) return
        val targets = devices.entries
            .filter { it.key != net.ip && it.key != net.gateway && it.value.mac.isNotEmpty() }
            .map { it.key to it.value.mac }
        val sig = targets.joinToString("|") { "${it.first}:${it.second}" }
        if (!force && sig == lastMitmTargets) return
        lastMitmTargets = sig
        ArpKit.startMitm(context, net, targets)
    }

    // ---------------------------------------------------------------- tick
    /** Dipanggil tiap detik dari ViewModel. */
    suspend fun tick(): DashboardUiState = withContext(Dispatchers.IO) {
        // Pastikan binary native selalu ter-extract (walau scan belum jalan)
        ArpKit.ensure(context)
        if (!rootOk) rootOk = RootShell.hasRoot()
        if (!rootOk) return@withContext snapshot(loading = false)

        val fresh = readNet()
        if (net.ready && fresh.ready && fresh != net) {
            onNetworkChange(fresh)
        } else if (!net.ready && fresh.ready) {
            net = fresh
            ensureForwarding()
            scanNow()
        } else if (!net.ready && fresh.ip.isNotEmpty()) {
            // Network info parsial — tetap coba scan dengan data yang ada
            net = fresh
            scanNow()
        } else {
            net = fresh
        }

        // bandwidth per-device dari counter kernel
        val acc = readAccounting()
        val now = System.currentTimeMillis()
        acc.forEach { (ip, v) ->
            val d = devices[ip] ?: return@forEach
            val prev = accPrev[ip]
            var down = 0.0
            var up = 0.0
            if (prev != null) {
                val dt = (now - prev[2]) / 1000.0
                if (dt > 0.2) {
                    down = ((v[0] - prev[0]).coerceAtLeast(0)) / dt
                    up = ((v[1] - prev[1]).coerceAtLeast(0)) / dt
                }
            }
            accPrev[ip] = longArrayOf(v[0], v[1], now)
            devices[ip] = d.copy(rxBytes = v[0], txBytes = v[1], downRate = down, upRate = up)
        }

        // online flag
        devices.forEach { (ip, d) ->
            val active = (now - d.lastSeen) < 30_000 ||
                d.downRate > 0 || d.upRate > 0 ||
                (devices[ip]?.rxBytes ?: 0) > 0
            if (!active && d.online && (now - d.lastSeen) > 30_000) devices[ip] = d.copy(online = false)
        }
        snapshot(loading = false)
    }

    private suspend fun onNetworkChange(fresh: NetInfo) {
        log("NETWORK CHANGED -> ip ${fresh.ip} gw ${fresh.gateway}")
        ArpKit.stopMitm(context)
        lastMitmTargets = ""
        RootShell.run("iptables -F NETCUT_ACC 2>/dev/null")
        devices.keys.toList().forEach { ip ->
            RootShell.run("iptables -D FORWARD -s $ip -j DROP 2>/dev/null; " +
                "iptables -D FORWARD -d $ip -j DROP 2>/dev/null")
        }
        devices.clear()
        accPrev.clear()
        limits.clear()
        RootShell.run("tc qdisc del dev ${fresh.iface} root 2>/dev/null")
        net = fresh
        ensureForwarding()
        scanNow()
    }

    private fun log(msg: String) {
        synchronized(logs) {
            logs.addLast(msg)
            while (logs.size > 40) logs.removeFirst()
        }
    }

    private fun snapshot(loading: Boolean): DashboardUiState {
        val (totRx, totTx) = ifaceTotals()
        val list = synchronized(devices) { devices.values.sortedBy { ipToInt(it.ip) } }
        return DashboardUiState(
            loading = loading,
            rootOk = rootOk,
            net = net,
            devices = list,
            downRate = list.sumOf { it.downRate },
            upRate = list.sumOf { it.upRate },
            totalDown = totRx,
            totalUp = totRx.let { totTx },
            blockedCount = list.count { it.blocked },
            limitedCount = list.count { it.limitKbps > 0 },
            mitmOn = mitmOn,
            logs = synchronized(logs) { logs.toList().asReversed() },
        )
    }

    private fun ipToInt(ip: String): Long = try {
        ip.split(".").fold(0L) { a, s -> a * 256 + (s.toLongOrNull() ?: 0) }
    } catch (_: Exception) { 0L }

    companion object {
        // mini OUI — cukup utk identifikasi kasar offline
        private val OUI = mapOf(
            "F0B4D2" to "Xiaomi", "64CC2E" to "Xiaomi", "78D34F" to "Xiaomi",
            "203B69" to "vivo Mobile", "3A7348" to "Randomized/Private MAC",
            "CCB071" to "Fiberhome", "CCE17F" to "Fiberhome",
            "E45F01" to "Raspberry Pi", "B827EB" to "Raspberry Pi",
            "001A2B" to "Ayecom", "D83214" to "Tenda", "50C7BF" to "TP-Link",
            "1C61B4" to "TP-Link", "A0F3C1" to "TP-Link", "EC086B" to "TP-Link",
            "00E0FC" to "HUAWEI", "4846FB" to "HUAWEI", "001E10" to "Cisco",
            "001122" to "Cisco", "AC84C6" to "TP-Link", "9C5322" to "Ubiquiti",
            "74ACB9" to "Ubiquiti", "FC15B4" to "Hewlett Packard",
            "3C5A37" to "Samsung", "5CF6DC" to "Samsung", "08ECA9" to "Samsung",
            "F8FF0B" to "Apple", "3C0754" to "Apple", "A4B197" to "Apple",
        )
    }
}
