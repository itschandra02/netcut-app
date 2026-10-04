package com.itschandra.netcut.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.itschandra.netcut.data.DashboardUiState
import com.itschandra.netcut.data.Device
import com.itschandra.netcut.ui.theme.*
import com.itschandra.netcut.vm.DashboardViewModel
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(vm: DashboardViewModel, dark: Boolean, onToggleTheme: () -> Unit) {
    val state by vm.state.collectAsState()

    Scaffold(
        topBar = {
            TopBar(
                state = state,
                dark = dark,
                onToggleTheme = onToggleTheme,
                onScan = { vm.scan() },
                onMitm = { vm.setMitm(it) },
            )
        },
        containerColor = MaterialTheme.colorScheme.background,
    ) { pad ->
        LazyColumn(
            modifier = Modifier
                .padding(pad)
                .fillMaxSize()
                .padding(horizontal = 14.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(top = 12.dp, bottom = 28.dp),
        ) {
            item { ThroughputCard(state, dark) }
            item { StatsGrid(state, dark) }
            item {
                Text(
                    "DEVICES",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = 1.2.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 4.dp, top = 6.dp),
                )
            }
            if (!state.rootOk && !state.loading) {
                item { WarningCard("Root belum diberikan.\nSemua fitur butuh akses root (su).\nBuka Magisk/KernelSU lalu izinkan, setelah itu tap SCAN.") }
            } else if (state.devices.isEmpty()) {
                item { WarningCard(if (state.loading) "Menyiapkan…" else "Belum ada device — tap SCAN LAN.") }
            } else {
                items(state.devices, key = { it.ip }) { d ->
                    DeviceRow(
                        d = d,
                        onBlock = { vm.setBlock(d.ip, it) },
                        onLimit = { vm.setLimit(d.ip, it) },
                    )
                }
            }
            item { LogCard(state.logs) }
        }
    }
}

@Composable
private fun TopBar(
    state: DashboardUiState,
    dark: Boolean,
    onToggleTheme: () -> Unit,
    onScan: () -> Unit,
    onMitm: (Boolean) -> Unit,
) {
    Surface(color = MaterialTheme.colorScheme.background) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("NET", fontWeight = FontWeight.Bold, fontSize = 20.sp)
                Text("CUT", fontWeight = FontWeight.Bold, fontSize = 20.sp, color = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.weight(1f))
                OutlinedButton(onClick = onToggleTheme, contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)) {
                    Text(if (dark) "LIGHT" else "DARK", fontSize = 11.sp)
                }
            }
            Spacer(Modifier.height(8.dp))
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth(),
            ) {
                AssistChip(
                    onClick = {},
                    label = { Text(state.net.iface.ifEmpty { "—" }, fontSize = 11.sp) },
                )
                AssistChip(
                    onClick = {},
                    label = { Text(state.net.ip.ifEmpty { "—" }, fontSize = 11.sp) },
                )
                AssistChip(
                    onClick = {},
                    label = { Text("gw ${state.net.gateway.ifEmpty { "…" }}", fontSize = 11.sp) },
                )
            }
            Spacer(Modifier.height(8.dp))
            Row(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Switch(checked = state.mitmOn, onCheckedChange = onMitm)
                    Spacer(Modifier.width(6.dp))
                    Text("MITM", fontSize = 11.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 1.sp)
                }
                Spacer(Modifier.weight(1f))
                Button(
                    onClick = onScan,
                    enabled = !state.scanning,
                    shape = RoundedCornerShape(11.dp),
                ) {
                    Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(if (state.scanning) "SCANNING…" else "SCAN LAN", fontSize = 12.sp)
                }
            }
        }
    }
}

@Composable
private fun ThroughputCard(state: DashboardUiState, dark: Boolean) {
    val histDown = remember { mutableStateListOf<Double>() }
    val histUp = remember { mutableStateListOf<Double>() }
    LaunchedEffect(state.downRate, state.upRate) {
        histDown.add(state.downRate); histUp.add(state.upRate)
        while (histDown.size > 60) { histDown.removeAt(0); histUp.removeAt(0) }
    }
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Column(Modifier.fillMaxWidth().padding(18.dp)) {
            Text(
                "THROUGHPUT · ${state.net.iface.ifEmpty { "—" }}",
                fontSize = 10.sp,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = 1.2.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(28.dp)) {
                RateText("DOWN", state.downRate, Green)
                RateText("UP", state.upRate, Brand)
            }
            Spacer(Modifier.height(12.dp))
            RateChart(
                down = histDown.toList(),
                up = histUp.toList(),
                modifier = Modifier.fillMaxWidth().height(96.dp),
            )
        }
    }
}

@Composable
private fun RateText(label: String, rate: Double, color: Color) {
    Column {
        Text(label, fontSize = 9.sp, letterSpacing = 1.2.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Row(verticalAlignment = Alignment.Bottom) {
            Text(
                fmtRate(rate).first,
                fontSize = 30.sp,
                fontWeight = FontWeight.Bold,
                color = color,
                letterSpacing = (-0.5).sp,
            )
            Text(" ${fmtRate(rate).second}", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun RateChart(down: List<Double>, up: List<Double>, modifier: Modifier) {
    val cDown = Green
    val cUp = Brand
    Canvas(modifier) {
        val max = (down + up).maxOrNull()?.coerceAtLeast(1024.0) ?: 1024.0
        val n = maxOf(down.size, 2)
        val dx = size.width / (n - 1)
        fun y(v: Double) = size.height - (v / max * (size.height * 0.85f)).toFloat() - 4f
        drawSeries(up, dx, ::y, cUp)
        drawSeries(down, dx, ::y, cDown)
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawSeries(
    data: List<Double>,
    dx: Float,
    y: (Double) -> Float,
    color: Color,
) {
    if (data.size < 2) return
    val path = androidx.compose.ui.graphics.Path()
    data.forEachIndexed { i, v ->
        val x = i * dx
        val yy = y(v)
        if (i == 0) path.moveTo(x, yy) else path.lineTo(x, yy)
    }
    drawPath(path, color, style = androidx.compose.ui.graphics.drawscope.Stroke(width = 3f))
}

@Composable
private fun StatsGrid(state: DashboardUiState, dark: Boolean) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            StatCard(Modifier.weight(1f), "Devices Online", state.devices.count { it.online }.toString(),
                "${state.devices.size} found", Person_soft(dark), Person_fg(dark), Icons.Default.Person)
            StatCard(Modifier.weight(1f), "Total Down", fmtBytes(state.totalDown),
                "since boot", GreenSoft2(dark), Green, Icons.Default.KeyboardArrowDown)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            StatCard(Modifier.weight(1f), "Total Up", fmtBytes(state.totalUp),
                "since boot", AmberSoft2(dark), Amber, Icons.Default.KeyboardArrowUp)
            StatCard(Modifier.weight(1f), "Blocked", state.blockedCount.toString(),
                "${state.limitedCount} limited", RedSoft2(dark), Red, Icons.Default.Warning)
        }
    }
}

@Composable
private fun StatCard(
    modifier: Modifier,
    label: String,
    value: String,
    sub: String,
    iconBg: Color,
    accent: Color,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(44.dp).clip(RoundedCornerShape(13.dp)).background(iconBg),
                contentAlignment = Alignment.Center,
            ) {
                Icon(icon, contentDescription = null, tint = accent, modifier = Modifier.size(22.dp))
            }
            Spacer(Modifier.width(11.dp))
            Column {
                Text(value, fontSize = 19.sp, fontWeight = FontWeight.Bold, color = accent)
                Text(label, fontSize = 10.5.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(sub, fontSize = 9.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun WarningCard(msg: String) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Text(msg, Modifier.fillMaxWidth().padding(22.dp), fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

// ---------------------------------------------------------------- device row

@Composable
private fun DeviceRow(d: Device, onBlock: (Boolean) -> Unit, onLimit: (Int) -> Unit) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (d.blocked) RedSoft2(false) else MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Column(Modifier.fillMaxWidth().padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier.size(9.dp).clip(CircleShape)
                        .background(if (d.blocked) Red else if (d.online) Green else Color(0xFFA8B0BE))
                )
                Spacer(Modifier.width(9.dp))
                Column(Modifier.weight(1f)) {
                    Text(d.ip, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    Text(
                        d.mac,
                        fontSize = 10.5.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                    )
                    Text(
                        d.vendor,
                        fontSize = 10.5.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text("▼ ${fmtRate(d.downRate).first} ${fmtRate(d.downRate).second}",
                        fontSize = 11.sp, color = Green, fontWeight = FontWeight.SemiBold)
                    Text("▲ ${fmtRate(d.upRate).first} ${fmtRate(d.upRate).second}",
                        fontSize = 11.sp, color = Brand, fontWeight = FontWeight.SemiBold)
                    Text("${fmtBytes(d.rxBytes)} / ${fmtBytes(d.txBytes)}",
                        fontSize = 9.5.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                if (d.isGateway) Badge("GATEWAY", Brand)
                if (d.isSelf) Badge("THIS PHONE", Color(0xFF7C5CFF))
                if (d.mitm && !d.isSelf && !d.isGateway) Badge("MITM", Green)
                if (d.limitKbps > 0) Badge("LIMIT ${d.limitKbps}K", Amber)
                if (d.blocked) Badge("BLOCKED", Red)
                if (!d.online && !d.isSelf) Badge("OFFLINE", Color(0xFFA8B0BE))
            }
            if (!d.isSelf && !d.isGateway) {
                Spacer(Modifier.height(10.dp))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    OutlinedButton(
                        onClick = { onBlock(!d.blocked) },
                        shape = RoundedCornerShape(9.dp),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = if (d.blocked) Green else Red
                        ),
                    ) {
                        Text(if (d.blocked) "UNBLOCK" else "BLOCK", fontSize = 11.sp)
                    }
                    LimitMenu(current = d.limitKbps, onSelect = onLimit)
                }
            }
        }
    }
}

@Composable
private fun Badge(text: String, color: Color) {
    Box(
        Modifier
            .clip(RoundedCornerShape(99.dp))
            .background(color.copy(alpha = 0.14f))
            .padding(horizontal = 9.dp, vertical = 3.dp),
    ) {
        Text(text, fontSize = 9.sp, fontWeight = FontWeight.Bold, color = color, letterSpacing = 0.6.sp)
    }
}

@Composable
private fun LimitMenu(current: Int, onSelect: (Int) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        OutlinedButton(onClick = { expanded = true }, shape = RoundedCornerShape(9.dp)) {
            Text(if (current > 0) "$current kbps" else "no limit", fontSize = 11.sp)
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            listOf(0, 64, 128, 256, 512, 1024, 2048, 4096, 8192).forEach { k ->
                DropdownMenuItem(
                    text = { Text(if (k == 0) "no limit" else "$k kbps") },
                    onClick = { onSelect(k); expanded = false },
                )
            }
        }
    }
}

// ---------------------------------------------------------------- log

@Composable
private fun LogCard(logs: List<String>) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(Modifier.fillMaxWidth().padding(16.dp)) {
            Text(
                "EVENT LOG",
                fontSize = 10.sp,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = 1.2.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(8.dp))
            if (logs.isEmpty()) {
                Text("—", color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                logs.take(20).forEachIndexed { i, l ->
                    Text(
                        l,
                        fontSize = 10.5.sp,
                        fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                        color = if (i == 0) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

// ---------------------------------------------------------------- helpers

private fun fmtRate(bps: Double): Pair<String, String> {
    var v = bps
    val units = listOf("B/s", "KB/s", "MB/s", "GB/s")
    var i = 0
    while (v >= 1024 && i < units.size - 1) { v /= 1024; i++ }
    return String.format(Locale.US, if (v < 10) "%.2f" else "%.1f", v) to units[i]
}

private fun fmtBytes(b: Long): String {
    var v = b.toDouble()
    val units = listOf("B", "KB", "MB", "GB", "TB")
    var i = 0
    while (v >= 1024 && i < units.size - 1) { v /= 1024; i++ }
    return String.format(Locale.US, if (v < 10) "%.2f %s" else "%.1f %s", v, units[i])
}

@Composable
private fun Person_soft(dark: Boolean) = if (dark) Color(0x243D8BFD) else BrandSoft

@Composable
private fun Person_fg(dark: Boolean) = if (dark) BrandDarkTheme else Brand

@Composable
private fun GreenSoft2(dark: Boolean) = if (dark) Color(0x2434D399) else GreenSoft

@Composable
private fun AmberSoft2(dark: Boolean) = if (dark) Color(0x24F5B544) else AmberSoft

@Composable
private fun RedSoft2(dark: Boolean) = if (dark) Color(0x24FF6B6B) else RedSoft
