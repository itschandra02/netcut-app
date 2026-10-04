package com.itschandra.netcut.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.itschandra.netcut.data.DashboardUiState
import com.itschandra.netcut.data.Device
import com.itschandra.netcut.ui.theme.*
import com.itschandra.netcut.vm.DashboardViewModel
import java.util.Locale

private val Mono = FontFamily.Monospace

@Composable
fun DashboardScreen(vm: DashboardViewModel, dark: Boolean, onToggleTheme: () -> Unit) {
    val state by vm.state.collectAsState()
    val pal = if (dark) DarkPalette else LightPalette
    var filter by remember { mutableStateOf("") }

    LazyColumn(
        modifier = Modifier.fillMaxSize().background(pal.bg),
        contentPadding = PaddingValues(bottom = 28.dp),
    ) {
        item { TopBar(state, pal, dark, onToggleTheme, onScan = { vm.scan() }, onMitm = { vm.setMitm(it) }) }
        item { HeroSection(state, pal) }
        item { StatsSection(state, pal) }
        item { DevicesPanel(state, pal, filter, { filter = it }, vm) }
        item { LogPanel(state, pal) }
        item { Footer(pal) }
    }
}

// ═══════════════════════════════ TOPBAR
@Composable
private fun TopBar(
    state: DashboardUiState, pal: NetCutColors, dark: Boolean,
    onToggleTheme: () -> Unit, onScan: () -> Unit, onMitm: (Boolean) -> Unit,
) {
    Column(Modifier.fillMaxWidth().background(pal.bg).padding(14.dp)) {
        // Row 1: brand + theme
        Row(verticalAlignment = Alignment.CenterVertically) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("NET", fontWeight = FontWeight.Bold, fontSize = 19.sp, color = pal.text)
                Text("CUT", fontWeight = FontWeight.Bold, fontSize = 19.sp, color = pal.brand)
                BlinkCursor(pal.brand)
            }
            Spacer(Modifier.weight(1f))
            ThemeButton(dark, pal, onToggleTheme)
        }
        Spacer(Modifier.height(10.dp))
        // Row 2: MITM seg + SCAN
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
            Row(
                Modifier.clip(RoundedCornerShape(11.dp))
                    .border(1.dp, pal.border, RoundedCornerShape(11.dp))
                    .background(pal.card),
            ) {
                SegButton("MITM ON", state.mitmOn, pal) { onMitm(true) }
                SegButton("OFF", !state.mitmOn, pal) { onMitm(false) }
            }
            Spacer(Modifier.weight(1f))
            Button(
                onClick = onScan, enabled = !state.scanning,
                shape = RoundedCornerShape(11.dp),
                colors = ButtonDefaults.buttonColors(containerColor = pal.brand, contentColor = Color.White),
                contentPadding = PaddingValues(horizontal = 18.dp, vertical = 10.dp),
            ) {
                Text(if (state.scanning) "SCANNING…" else "SCAN LAN", fontSize = 12.5.sp, fontWeight = FontWeight.SemiBold)
            }
        }
        Spacer(Modifier.height(10.dp))
        // Row 3: chips scroll
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Chip(state.net.iface.ifEmpty { "—" }, pal, bold = true)
            Chip(state.net.ip.ifEmpty { "—" }, pal)
            Chip("gw ${state.net.gateway.ifEmpty { "…" }}", pal, dim = true)
            Chip(state.net.mac.ifEmpty { "—" }, pal, dim = true)
        }
    }
}

@Composable private fun BlinkCursor(color: Color) {
    var visible by remember { mutableStateOf(true) }
    LaunchedEffect(Unit) { while (true) { kotlinx.coroutines.delay(550); visible = !visible } }
    Box(Modifier.padding(start = 3.dp).size(width = 3.dp, height = 18.dp).clip(RoundedCornerShape(2.dp)).background(if (visible) color else Color.Transparent))
}

@Composable private fun ThemeButton(dark: Boolean, pal: NetCutColors, onClick: () -> Unit) {
    IconButton(onClick = onClick, modifier = Modifier.size(38.dp).clip(RoundedCornerShape(11.dp)).border(1.dp, pal.border, RoundedCornerShape(11.dp)).background(pal.card)) {
        Text(if (dark) "☀" else "🌙", fontSize = 15.sp, color = pal.muted)
    }
}

@Composable private fun SegButton(text: String, active: Boolean, pal: NetCutColors, onClick: () -> Unit) {
    Button(onClick = onClick, shape = RoundedCornerShape(10.dp),
        colors = ButtonDefaults.buttonColors(containerColor = if (active) pal.brand else Color.Transparent, contentColor = if (active) Color.White else pal.muted),
        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp), elevation = null) {
        Text(text, fontSize = 11.5.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 0.4.sp)
    }
}

@Composable private fun Chip(text: String, pal: NetCutColors, bold: Boolean = false, dim: Boolean = false) {
    Row(Modifier.clip(RoundedCornerShape(99.dp)).border(1.dp, pal.border, RoundedCornerShape(99.dp)).background(pal.card).padding(horizontal = 11.dp, vertical = 5.dp)) {
        Text(text, fontFamily = Mono, fontSize = 11.sp,
            color = if (bold) pal.brand else if (dim) pal.faint else pal.muted,
            fontWeight = if (bold) FontWeight.SemiBold else FontWeight.Normal, maxLines = 1)
    }
}

// ═══════════════════════════════ HERO
@Composable
private fun HeroSection(state: DashboardUiState, pal: NetCutColors) {
    val histDown = remember { mutableStateListOf<Double>() }
    val histUp = remember { mutableStateListOf<Double>() }
    LaunchedEffect(state.downRate, state.upRate) {
        histDown.add(state.downRate); histUp.add(state.upRate)
        while (histDown.size > 90) { histDown.removeAt(0); histUp.removeAt(0) }
    }

    Column(Modifier.padding(horizontal = 14.dp).fillMaxWidth()
        .clip(RoundedCornerShape(16.dp)).border(1.dp, pal.border, RoundedCornerShape(16.dp)).background(pal.card)) {
        // head
        Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 14.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("THROUGHPUT · ${state.net.iface.ifEmpty { "—" }}", fontSize = 10.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 1.2.sp, color = pal.muted)
            Spacer(Modifier.weight(1f))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                LegendDot("download", pal.acc)
                LegendDot("upload", pal.cy)
            }
        }
        // big numbers — wrap kalau sempit
        Row(Modifier.padding(start = 16.dp, end = 16.dp, top = 6.dp, bottom = 4.dp).fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween) {
            BigNumber(state.downRate, pal.acc)
            BigNumber(state.upRate, pal.cy)
        }
        ChartCanvas(histDown.toList(), histUp.toList(), pal.acc, pal.cy, pal.grid,
            Modifier.fillMaxWidth().height(140.dp))
    }
}

@Composable private fun LegendDot(label: String, color: Color) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
        Box(Modifier.size(8.dp).clip(RoundedCornerShape(2.dp)).background(color))
        Text(label, fontFamily = Mono, fontSize = 9.5.sp, letterSpacing = 0.4.sp, color = color)
    }
}

@Composable private fun BigNumber(rate: Double, color: Color) {
    val (v, u) = fmtRate(rate)
    Column {
        Text(v, fontFamily = Mono, fontWeight = FontWeight.Bold, fontSize = 28.sp, color = color, letterSpacing = (-0.6).sp, maxLines = 1)
        Text(u, fontFamily = Mono, fontSize = 11.sp, fontWeight = FontWeight.Medium, color = Color(0xFF7B8494))
    }
}

// ═══════════════════════════════ CHART
@Composable
private fun ChartCanvas(down: List<Double>, up: List<Double>, cDown: Color, cUp: Color, grid: Color, modifier: Modifier) {
    Canvas(modifier) {
        val max = (down + up).maxOrNull()?.coerceAtLeast(1024.0)?.times(1.15) ?: 1200.0
        val n = 90; val dx = size.width / (n - 1)
        fun py(v: Double) = size.height - 8f - (v / max * (size.height - 18f)).toFloat()
        // grid lines
        for (i in 1 until 4) { val y = (size.height * i / 4f); drawLine(grid, Offset(0f, y), Offset(size.width, y), 1f) }
        // baseline (zero line)
        drawLine(grid.copy(alpha = 0.35f), Offset(0f, size.height - 8f), Offset(size.width, size.height - 8f), 1.5f)
        // series
        drawSeries(up, n, dx, ::py, cUp, 0.11f)
        drawSeries(down, n, dx, ::py, cDown, 0.15f)
    }
}

private fun DrawScope.drawSeries(data: List<Double>, n: Int, dx: Float, py: (Double) -> Float, color: Color, alphaFill: Float) {
    if (data.size < 2) return
    val path = Path(); val fill = Path(); fill.moveTo(0f, size.height)
    data.forEachIndexed { i, v -> val x = i * dx; val y = py(v); if (i == 0) { path.moveTo(x, y); fill.lineTo(x, y) } else { path.lineTo(x, y); fill.lineTo(x, y) } }
    fill.lineTo((data.size - 1) * dx, size.height); fill.close()
    drawPath(fill, color.copy(alpha = alphaFill))
    drawPath(path, color, style = Stroke(width = 1.6f))
    drawCircle(color, 2.6f, Offset((data.size - 1) * dx, py(data.last())))
}

// ═══════════════════════════════ STATS 2x2
@Composable
private fun StatsSection(state: DashboardUiState, pal: NetCutColors) {
    Column(Modifier.padding(horizontal = 14.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            StatCard(Modifier.weight(1f), pal, "DEVICES ONLINE", state.devices.count { it.online }.toString(), "${state.devices.size} found", pal.brandSoft, pal.brand, Icons.Default.Person)
            StatCard(Modifier.weight(1f), pal, "TOTAL DOWN", fmtBytes(state.totalDown), "since boot", pal.greenSoft, pal.green, Icons.Default.KeyboardArrowDown)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            StatCard(Modifier.weight(1f), pal, "TOTAL UP", fmtBytes(state.totalUp), "since boot", pal.amberSoft, pal.amber, Icons.Default.KeyboardArrowUp)
            StatCard(Modifier.weight(1f), pal, "BLOCKED", state.blockedCount.toString(), "${state.limitedCount} throttled", pal.redSoft, pal.red, Icons.Default.Warning)
        }
    }
}

@Composable
private fun StatCard(modifier: Modifier, pal: NetCutColors, label: String, value: String, sub: String, iconBg: Color, accent: Color, icon: ImageVector) {
    Row(modifier.clip(RoundedCornerShape(16.dp)).border(1.dp, pal.border, RoundedCornerShape(16.dp)).background(pal.card).padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(44.dp).clip(RoundedCornerShape(13.dp)).background(iconBg), contentAlignment = Alignment.Center) {
            Icon(icon, null, tint = accent, modifier = Modifier.size(20.dp))
        }
        Spacer(Modifier.width(12.dp))
        Column {
            Text(value, fontFamily = Mono, fontWeight = FontWeight.Bold, fontSize = 20.sp, color = accent, letterSpacing = (-0.3).sp, maxLines = 1)
            Text(label, fontSize = 9.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 1.sp, color = pal.muted, maxLines = 1)
            Text(sub, fontSize = 9.sp, color = pal.muted, maxLines = 1)
        }
    }
}

// ═══════════════════════════════ DEVICES PANEL (STACKED CARD LAYOUT)
@Composable
private fun DevicesPanel(state: DashboardUiState, pal: NetCutColors, filter: String, onFilter: (String) -> Unit, vm: DashboardViewModel) {
    val filtered = state.devices.filter { d ->
        filter.isEmpty() || (d.ip + " " + d.mac + " " + d.vendor).lowercase().contains(filter.lowercase())
    }

    Column(Modifier.padding(horizontal = 14.dp).fillMaxWidth()
        .clip(RoundedCornerShape(16.dp)).border(1.dp, pal.border, RoundedCornerShape(16.dp)).background(pal.card)) {
        // head
        Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("DEVICES", fontSize = 11.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 1.2.sp, color = pal.muted)
            Spacer(Modifier.width(12.dp))
            OutlinedTextField(
                value = filter, onValueChange = onFilter,
                placeholder = { Text("filter ip / mac / vendor", fontSize = 11.sp, color = pal.faint) },
                singleLine = true, textStyle = LocalTextStyle.current.copy(fontSize = 11.sp, color = pal.text),
                shape = RoundedCornerShape(10.dp),
                colors = OutlinedTextFieldDefaults.colors(unfocusedBorderColor = pal.border, focusedBorderColor = pal.brand, unfocusedContainerColor = pal.bg, focusedContainerColor = pal.card),
                modifier = Modifier.weight(1f).height(42.dp),
            )
        }
        HorizontalDivider(color = pal.border)

        if (filtered.isEmpty()) {
            EmptyRow(pal, if (state.devices.isEmpty()) "scanning the network…" else "no match \"$filter\"")
        } else {
            filtered.forEachIndexed { i, d ->
                DeviceCard(d, pal, onBlock = { vm.setBlock(d.ip, it) }, onLimit = { vm.setLimit(d.ip, it) })
                if (i < filtered.size - 1) HorizontalDivider(color = pal.border, thickness = 0.5.dp)
            }
        }
    }
}

@Composable private fun EmptyRow(pal: NetCutColors, msg: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 46.dp), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
        PulseDot(pal.brand); Spacer(Modifier.width(8.dp))
        Text(msg, fontFamily = Mono, fontSize = 12.sp, color = pal.muted)
    }
}

@Composable private fun PulseDot(color: Color) {
    var visible by remember { mutableStateOf(true) }
    LaunchedEffect(Unit) { while (true) { kotlinx.coroutines.delay(600); visible = !visible } }
    Box(Modifier.size(7.dp).clip(CircleShape).background(if (visible) color else color.copy(alpha = 0.3f)))
}

/**
 * Device card — STACKED vertical layout (mobile-friendly).
 * Baris: [dot+IP+tags] → [MAC+vendor] → [speed+bars] → [controls]
 */
@Composable
private fun DeviceCard(d: Device, pal: NetCutColors, onBlock: (Boolean) -> Unit, onLimit: (Int) -> Unit) {
    val rowBg = if (d.blocked) pal.redSoft else pal.card
    val dotColor = when { d.blocked -> pal.red; !d.online -> pal.faint; else -> pal.green }
    val peak = maxOf(d.downRate, d.upRate, 1.0)

    Column(Modifier.fillMaxWidth().background(rowBg).padding(horizontal = 16.dp, vertical = 12.dp)) {
        // ── Baris 1: dot + IP + tags (wrap) ──
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Box(Modifier.size(8.dp).clip(CircleShape).background(dotColor))
            Spacer(Modifier.width(8.dp))
            Text(d.ip, fontFamily = Mono, fontWeight = FontWeight.Bold, fontSize = 14.sp, color = pal.text)
            Spacer(Modifier.weight(1f))
            // tags compact di kanan
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                if (d.isSelf) Tag("ME", pal.brandSoft, pal.brand)
                if (d.isGateway) Tag("GW", pal.brandSoft, pal.brand)
                if (d.blocked) Tag("BLOCKED", pal.red, Color.White)
                if (d.limitKbps > 0) Tag("${d.limitKbps}K", pal.amberSoft, pal.amber)
                if (!d.online && !d.isSelf) Tag("OFF", Color.Transparent, pal.faint, bordered = true, pal = pal)
            }
        }

        // ── Baris 2: MAC + vendor ──
        Row(Modifier.fillMaxWidth().padding(start = 16.dp, top = 3.dp)) {
            Text(d.mac, fontFamily = Mono, fontSize = 11.sp, color = pal.muted)
            Spacer(Modifier.width(10.dp))
            Text(d.vendor.ifEmpty { "?" }, fontSize = 11.sp, color = pal.faint, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
        }

        // ── Baris 3: speed + bars ──
        if (!d.isSelf && !d.isGateway) {
            val (dv, du) = fmtRate(d.downRate)
            val (uv, uu) = fmtRate(d.upRate)
            Row(Modifier.fillMaxWidth().padding(start = 16.dp, top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("▼ ", fontSize = 10.sp, color = pal.acc)
                Text("$dv $du", fontFamily = Mono, fontSize = 12.sp, color = pal.acc, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.width(14.dp))
                Text("▲ ", fontSize = 10.sp, color = pal.cy)
                Text("$uv $uu", fontFamily = Mono, fontSize = 12.sp, color = pal.cy, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.weight(1f))
                // transferred
                Text("${fmtBytes(d.rxBytes)} / ${fmtBytes(d.txBytes)}", fontFamily = Mono, fontSize = 10.sp, color = pal.faint)
            }
            // bars
            Row(Modifier.fillMaxWidth().padding(start = 16.dp, top = 6.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                SpeedBar((d.downRate / peak * 100).toFloat().coerceIn(0f, 100f), pal.acc, pal, Modifier.weight(1f))
                SpeedBar((d.upRate / peak * 100).toFloat().coerceIn(0f, 100f), pal.cy, pal, Modifier.weight(1f))
            }
            // ── Baris 4: controls ──
            Row(Modifier.fillMaxWidth().padding(start = 16.dp, top = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                MiniButton(if (d.blocked) "UNBLOCK" else "BLOCK", pal, d.blocked) { onBlock(!d.blocked) }
                LimitDropdown(d.limitKbps, pal, onSelect = onLimit)
                if (d.mitm) Tag("MITM", pal.green, Color.White)
            }
        } else {
            Text("—", fontFamily = Mono, fontSize = 10.sp, color = Color(0xFF454C58), modifier = Modifier.padding(start = 16.dp, top = 6.dp))
        }
    }
}

@Composable private fun SpeedBar(pct: Float, color: Color, pal: NetCutColors, modifier: Modifier = Modifier) {
    Box(modifier.height(4.dp).clip(RoundedCornerShape(99.dp)).background(pal.border)) {
        Box(Modifier.fillMaxHeight().fillMaxWidth(fraction = pct / 100f).clip(RoundedCornerShape(99.dp)).background(color))
    }
}

@Composable private fun Tag(text: String, bg: Color, fg: Color, pal: NetCutColors? = null, bordered: Boolean = false) {
    Row(Modifier.clip(RoundedCornerShape(99.dp))
        .then(if (bordered && pal != null) Modifier.border(1.dp, pal.border, RoundedCornerShape(99.dp)) else Modifier)
        .background(bg).padding(horizontal = 8.dp, vertical = 3.dp)) {
        Text(text, fontSize = 9.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.5.sp, color = fg)
    }
}

@Composable private fun MiniButton(text: String, pal: NetCutColors, isBlocked: Boolean, onClick: () -> Unit) {
    val bg = if (isBlocked) pal.green else pal.redSoft
    val fg = if (isBlocked) Color.White else pal.red
    val bdr = if (isBlocked) pal.green else pal.red
    Button(onClick = onClick, shape = RoundedCornerShape(9.dp),
        colors = ButtonDefaults.buttonColors(containerColor = bg, contentColor = fg),
        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 7.dp), elevation = null,
        modifier = Modifier.border(1.dp, bdr, RoundedCornerShape(9.dp))) {
        Text(text, fontSize = 10.5.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.5.sp)
    }
}

@Composable private fun LimitDropdown(current: Int, pal: NetCutColors, onSelect: (Int) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    val active = current > 0
    Box {
        Button(onClick = { expanded = true }, shape = RoundedCornerShape(9.dp),
            colors = ButtonDefaults.buttonColors(containerColor = pal.card, contentColor = if (active) pal.amber else pal.muted),
            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 7.dp), elevation = null,
            modifier = Modifier.border(1.dp, if (active) pal.amber else pal.border, RoundedCornerShape(9.dp))) {
            Text(if (active) "${current}k" else "no limit", fontFamily = Mono, fontSize = 10.5.sp, fontWeight = if (active) FontWeight.Bold else FontWeight.Medium)
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            listOf(0, 64, 128, 256, 512, 1024, 2048, 4096, 8192).forEach { k ->
                DropdownMenuItem(text = { Text(if (k == 0) "no limit" else "${k}k", fontSize = 12.sp) }, onClick = { onSelect(k); expanded = false })
            }
        }
    }
}

// ═══════════════════════════════ EVENT LOG
@Composable
private fun LogPanel(state: DashboardUiState, pal: NetCutColors) {
    val now = remember { mutableStateOf("--:--:--") }
    LaunchedEffect(Unit) { while (true) { now.value = java.text.SimpleDateFormat("HH:mm:ss", Locale.US).format(java.util.Date()); kotlinx.coroutines.delay(1000) } }

    Column(Modifier.padding(horizontal = 14.dp, vertical = 12.dp).fillMaxWidth()
        .clip(RoundedCornerShape(16.dp)).border(1.dp, pal.border, RoundedCornerShape(16.dp)).background(pal.card)) {
        Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("EVENT LOG", fontSize = 11.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 1.2.sp, color = pal.muted)
            Spacer(Modifier.weight(1f))
            Text(now.value, fontFamily = Mono, fontSize = 11.sp, color = pal.muted)
        }
        HorizontalDivider(color = pal.border)
        Column(Modifier.heightIn(max = 170.dp).padding(horizontal = 16.dp, vertical = 10.dp)) {
            state.logs.take(20).forEachIndexed { i, msg ->
                Row(Modifier.padding(vertical = 2.dp)) {
                    Text("›", fontFamily = Mono, fontSize = 11.sp, color = pal.faint, modifier = Modifier.width(14.dp))
                    Text(msg, fontFamily = Mono, fontSize = 10.5.sp,
                        color = if (i == 0) pal.brand else pal.muted,
                        fontWeight = if (i == 0) FontWeight.SemiBold else FontWeight.Normal,
                        maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }
}

// ═══════════════════════════════ FOOTER
@Composable
private fun Footer(pal: NetCutColors) {
    Row(Modifier.padding(horizontal = 14.dp, vertical = 18.dp).fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
            Box(Modifier.size(7.dp).clip(CircleShape).background(pal.green))
            Text("ROOT", fontSize = 10.sp, fontWeight = FontWeight.Medium, letterSpacing = 1.sp, color = pal.faint)
        }
        Text("NETCUT v1.0", fontSize = 10.sp, fontWeight = FontWeight.Medium, letterSpacing = 1.sp, color = pal.faint)
        Spacer(Modifier.weight(1f))
        Text("credits · @itschandra_28", fontSize = 10.sp, fontWeight = FontWeight.Medium, letterSpacing = 0.3.sp, color = pal.muted)
    }
}

// ═══════════════════════════════ HELPERS
private fun fmtRate(bps: Double): Pair<String, String> {
    var v = bps; val units = listOf("B/s", "KB/s", "MB/s", "GB/s"); var i = 0
    while (v >= 1024 && i < units.size - 1) { v /= 1024; i++ }
    return String.format(Locale.US, if (v < 10) "%.2f" else "%.1f", v) to units[i]
}

private fun fmtBytes(b: Long): String {
    var v = b.toDouble(); val units = listOf("B", "KB", "MB", "GB", "TB"); var i = 0
    while (v >= 1024 && i < units.size - 1) { v /= 1024; i++ }
    return String.format(Locale.US, if (v < 10) "%.2f %s" else "%.1f %s", v, units[i])
}
