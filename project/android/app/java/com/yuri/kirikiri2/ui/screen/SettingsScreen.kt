package com.yuri.kirikiri2.ui.screen

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Divider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.yuri.kirikiri2.data.SettingsStore

/**
 * 设置：对应原 C++ 的 PreferenceForm / GlobalPreferenceForm / IndividualPreferenceForm。
 *
 * 原实现把每一项硬编码在 PreferenceConfig.h 里（含斑马纹背景与三按钮语义错位的问题）；
 * 这里改为数据驱动，每一项都是 { 标题, 当前值, 控件 } 的组合，72dp 双行。
 */
@Composable
fun SettingsScreen(
    store: SettingsStore,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(bottom = 24.dp)
    ) {
        SettingsSection("外观") {
            SettingSwitch(
                title = "动态取色（Android 12+）",
                subtitle = "跟随壁纸生成配色，关闭则使用内置 Violet",
                checked = store.dynamicColor,
                onChanged = { store.dynamicColor = it }
            )
            SingleChoiceRow(
                title = "深色模式",
                options = listOf("跟随系统" to 0, "浅色" to 1, "深色" to 2),
                selected = store.darkMode,
                onSelect = { store.darkMode = it }
            )
            SettingSwitch(
                title = "新版游戏内菜单",
                subtitle = "Material 3 侧边浮层；关闭后使用传统 cocos 菜单，下次启动游戏生效",
                checked = store.useNativeMenu,
                onChanged = { store.useNativeMenu = it }
            )
        }

        SettingsSection("渲染", collapsible = true) {
            SingleChoiceRow(
                title = "图形渲染器",
                options = listOf("OpenGL（试验性）" to 0, "软件渲染器" to 1),
                selected = store.renderer,
                onSelect = { store.renderer = it }
            )
            SingleChoiceRow(
                title = "限制 FPS",
                options = listOf("60" to 60, "45" to 45, "30" to 30, "15" to 15),
                selected = store.fpsLimit,
                onSelect = { store.fpsLimit = it }
            )
            SingleChoiceRow(
                title = "内存限制",
                subtitle = "超出时回收纹理等资源，越低越省内存",
                options = listOf("不限制" to 0, "高" to 1, "中" to 2, "低" to 3),
                selected = store.memUsage,
                onSelect = { store.memUsage = it }
            )
            SingleChoiceRow(
                title = "软件渲染 · 绘制线程",
                options = listOf("自动" to 0) + (1..8).map { "$it" to it },
                selected = store.softwareDrawThread,
                onSelect = { store.softwareDrawThread = it }
            )
            SingleChoiceRow(
                title = "软件渲染 · 纹理压缩",
                options = listOf("不压缩" to 0, "半线 (halfline)" to 1, "LZ4" to 2, "LZ4+TLG5" to 3),
                selected = store.softwareCompressTex,
                onSelect = { store.softwareCompressTex = it }
            )
            SettingSwitch(
                title = "OpenGL · 精确渲染",
                subtitle = "修正半透明绘制顺序，更快但可能有色差（旧版行为）",
                checked = store.oglAccurateRender,
                onChanged = { store.oglAccurateRender = it }
            )
            SingleChoiceRow(
                title = "OpenGL · 最大纹理尺寸",
                options = listOf("自动" to 0, "1024" to 1024, "2048" to 2048, "4096" to 4096, "8192" to 8192, "16384" to 16384),
                selected = store.oglMaxTexSize,
                onSelect = { store.oglMaxTexSize = it }
            )
            SingleChoiceRow(
                title = "OpenGL · 纹理压缩格式",
                options = listOf("不压缩" to 0, "半分辨率" to 1, "ETC2" to 2, "PVRTC" to 3),
                selected = store.oglCompressTex,
                onSelect = { store.oglCompressTex = it }
            )
        }

        SettingsSection("OpenGL 扩展开关", collapsible = true, initiallyExpanded = false) {
            Text(
                "仅 OpenGL 渲染器生效；按设备 GPU 情况调整，普通用户保持默认即可",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp)
            )
            store.glExtKeys.forEach { key ->
                SettingSwitch(
                    title = key.removePrefix("GL_"),
                    subtitle = key,
                    checked = store.glExt(key),
                    onChanged = { store.setGlExt(key, it) }
                )
            }
        }

        SettingsSection("字体") {
            TextInputRow(
                title = "默认字体",
                subtitle = store.defaultFont.ifEmpty { "未设置（使用内置字体）" },
                dialogTitle = "输入字体文件路径（.ttf / .otf）",
                value = store.defaultFont,
                onConfirm = { store.defaultFont = it.trim() }
            )
            SettingSwitch(
                title = "强制使用默认字体",
                subtitle = "忽略游戏自带的字体文件",
                checked = store.forceDefaultFont,
                onChanged = { store.forceDefaultFont = it }
            )
        }

        SettingsSection("系统") {
            SettingSwitch(
                title = "输出日志",
                subtitle = "写入日志文件，排障用",
                checked = store.outputLog,
                onChanged = { store.outputLog = it }
            )
            SettingSwitch(
                title = "隐藏安卓系统按钮",
                subtitle = "全屏沉浸模式（部分 ROM 需重启应用）",
                checked = store.hideSysButtons,
                onChanged = { store.hideSysButtons = it }
            )
            SettingSwitch(
                title = "记住最后使用的路径",
                subtitle = "下次启动时回到上次目录",
                checked = store.rememberLastPath,
                onChanged = { store.rememberLastPath = it }
            )
            SettingSwitch(
                title = "保持屏幕常亮",
                subtitle = "游戏运行期间不熄屏",
                checked = store.keepScreenOn,
                onChanged = { store.keepScreenOn = it }
            )
            SettingSwitch(
                title = "显示 FPS",
                subtitle = "在画面右上角叠加帧率",
                checked = store.showFps,
                onChanged = { store.showFps = it }
            )
            SliderRow(
                title = "虚拟鼠标缩放比",
                value = store.cursorScale,
                onChanged = { store.cursorScale = it }
            )
            SliderRow(
                title = "菜单按钮不透明度",
                value = store.menuOpacity,
                onChanged = { store.menuOpacity = it }
            )
        }
    }
}

@Composable
fun SettingsSection(
    title: String,
    collapsible: Boolean = false,
    initiallyExpanded: Boolean = true,
    content: @Composable () -> Unit
) {
    var expanded by remember { mutableStateOf(!collapsible || initiallyExpanded) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .let { if (collapsible) it.clickable { expanded = !expanded } else it }
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            title,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.weight(1f)
        )
        if (collapsible) {
            Icon(
                if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                contentDescription = if (expanded) "收起" else "展开",
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
    androidx.compose.animation.AnimatedVisibility(visible = expanded) {
        Column {
            content()
        }
    }
    Divider(modifier = Modifier.padding(vertical = 8.dp), color = MaterialTheme.colorScheme.outlineVariant)
}

@Composable
fun SettingSwitch(
    title: String,
    subtitle: String,
    checked: Boolean,
    onChanged: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onChanged(!checked) }
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(
                subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Switch(checked = checked, onCheckedChange = onChanged)
    }
}

/** 折叠式单选行：收起时只显示标题与当前值，点开列出选项，选中后自动收起 */
@Composable
fun SingleChoiceRow(
    title: String,
    options: List<Pair<String, Int>>,
    selected: Int,
    subtitle: String? = null,
    onSelect: (Int) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    val currentLabel = options.firstOrNull { it.second == selected }?.first ?: "未知"

    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { expanded = !expanded }
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium)
                Text(
                    if (subtitle != null) "$subtitle（当前：$currentLabel）" else "当前：$currentLabel",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Icon(
                if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                contentDescription = if (expanded) "收起" else "展开",
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        androidx.compose.animation.AnimatedVisibility(visible = expanded) {
            Column(modifier = Modifier.padding(horizontal = 16.dp)) {
                options.forEach { (label, value) ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                onSelect(value)
                                expanded = false
                            }
                            .padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(selected = selected == value, onClick = {
                            onSelect(value)
                            expanded = false
                        })
                        Text(label, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        }
    }
}

@Composable
fun SliderRow(
    title: String,
    value: Float,
    onChanged: (Float) -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
            Text(
                "${(value * 100).toInt()}%",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Slider(value = value, onValueChange = onChanged, valueRange = 0f..1f)
    }
}

/** 点击弹输入对话框的行（默认字体路径等），空输入 = 清除 */
@Composable
fun TextInputRow(
    title: String,
    subtitle: String,
    dialogTitle: String,
    value: String,
    onConfirm: (String) -> Unit
) {
    var editing by remember { mutableStateOf(false) }
    var draft by remember { mutableStateOf(value) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { draft = value; editing = true }
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        Text(title, style = MaterialTheme.typography.titleMedium)
        Text(
            subtitle,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }

    if (editing) {
        AlertDialog(
            onDismissRequest = { editing = false },
            title = { Text(dialogTitle) },
            text = {
                OutlinedTextField(
                    value = draft,
                    onValueChange = { draft = it },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                TextButton(onClick = { editing = false; onConfirm(draft) }) { Text("确定") }
            },
            dismissButton = {
                TextButton(onClick = { editing = false }) { Text("取消") }
            }
        )
    }
}
