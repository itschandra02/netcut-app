package com.itschandra.netcut.data

/** State jaringan lokal (WiFi). */
data class NetInfo(
    val iface: String = "",
    val ip: String = "",
    val mac: String = "",
    val gateway: String = "",
    val gatewayMac: String = "",
) {
    val ready: Boolean get() = ip.isNotEmpty() && mac.isNotEmpty()
    val prefix: String get() = ip.substringBeforeLast('.', "")
}

/** Device di LAN. */
data class Device(
    val ip: String,
    val mac: String,
    val vendor: String = "",
    val hostname: String = "",
    val online: Boolean = true,
    val rxBytes: Long = 0,      // download
    val txBytes: Long = 0,      // upload
    val downRate: Double = 0.0, // B/s
    val upRate: Double = 0.0,   // B/s
    val blocked: Boolean = false,
    val limitKbps: Int = 0,
    val mitm: Boolean = false,
    val isSelf: Boolean = false,
    val isGateway: Boolean = false,
    val firstSeen: Long = 0L,
    val lastSeen: Long = 0L,
)

/** Snapshot buat UI. */
data class DashboardUiState(
    val loading: Boolean = true,
    val rootOk: Boolean = false,
    val net: NetInfo = NetInfo(),
    val devices: List<Device> = emptyList(),
    val downRate: Double = 0.0,
    val upRate: Double = 0.0,
    val totalDown: Long = 0,
    val totalUp: Long = 0,
    val blockedCount: Int = 0,
    val limitedCount: Int = 0,
    val mitmOn: Boolean = true,
    val scanning: Boolean = false,
    val logs: List<String> = emptyList(),
)

data class ShellResult(val code: Int, val out: String) {
    val ok: Boolean get() = code == 0
}
