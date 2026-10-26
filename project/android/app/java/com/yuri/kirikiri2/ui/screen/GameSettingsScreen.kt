package com.yuri.kirikiri2.ui.screen

import android.util.Xml
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.yuri.kirikiri2.bridge.Kr2Bridge
import org.xmlpull.v1.XmlPullParser
import java.io.File

/**
 * 独立设置（对应旧 IndividualPreferenceForm）：
 * 直接读写游戏目录下的 Kirikiroid2Preference.xml，与引擎同一份文件、同一格式
 * （<GlobalPreference><Item key=".." value=".."/> + Custom/KeyMap）。
 *
 * 语义与引擎一致：只保存用户改过的键，其余键在游戏启动时继承全局配置
 * （iSysConfigManager 模板回退链：个体值 → 全局值 → 内置默认）。
 * 改动在下次启动该游戏时生效。
 */
private const val PREF_FILENAME = "Kirikiroid2Preference.xml"

@Composable
fun GameSettingsScreen(
    gamePath: String,
    modifier: Modifier = Modifier
) {
    val gameName = File(gamePath).nameWithoutExtension.let { if (it.endsWith(".xp3")) it.removeSuffix(".xp3") else it }
    val items = remember(gamePath) { loadPrefDoc(gamePath) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(bottom = 24.dp)
    ) {
        Text(
            "「$gameName」独立设置 · 改动在下次启动该游戏时生效",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
        )

        fun get(key: String, def: String): String =
            items[key] ?: globalValue(key, def)

        fun set(key: String, value: String) {
            items[key] = value
            savePrefDoc(gamePath, items)
        }

        fun getBool(key: String, def: Boolean) = get(key, if (def) "1" else "0") != "0"

        SettingsSection("通用") {
            SettingSwitch("输出日志", "写入日志文件，排障用",
                getBool("outputlog", true)) { set("outputlog", if (it) "1" else "0") }
            SingleChoiceRow("图形渲染器",
                listOf("OpenGL（试验性）" to 0, "软件渲染器" to 1),
                if (get("renderer", "software") == "opengl") 0 else 1) {
                set("renderer", if (it == 0) "opengl" else "software")
            }
            SingleChoiceRow("限制 FPS",
                listOf("60" to 60, "45" to 45, "30" to 30, "15" to 15),
                get("fps_limit", "60").toIntOrNull() ?: 60) {
                set("fps_limit", it.toString())
            }
            SingleChoiceRow("内存限制",
                listOf("不限制" to 0, "高" to 1, "中" to 2, "低" to 3),
                MEM_VALUES.indexOf(get("memusage", "unlimited")).coerceAtLeast(0)) {
                set("memusage", MEM_VALUES[it])
            }
            SettingSwitch("保持屏幕常亮", "游戏运行期间不熄屏",
                getBool("keep_screen_alive", true)) { set("keep_screen_alive", if (it) "1" else "0") }
            SettingSwitch("显示 FPS", "在画面右上角叠加帧率",
                getBool("showfps", false)) { set("showfps", if (it) "1" else "0") }
        }

        SettingsSection("界面") {
            SliderRow("虚拟鼠标缩放比", get("vcursor_scale", "0.5").toFloatOrNull() ?: 0.5f) {
                set("vcursor_scale", fmtFloat(it))
            }
            SliderRow("菜单按钮不透明度", get("menu_handler_opa", "0.15").toFloatOrNull() ?: 0.15f) {
                set("menu_handler_opa", fmtFloat(it))
            }
        }

        SettingsSection("软件渲染器选项") {
            SingleChoiceRow("绘制线程", listOf("自动" to 0) + (1..8).map { "$it" to it },
                get("software_draw_thread", "0").toIntOrNull() ?: 0) {
                set("software_draw_thread", it.toString())
            }
            SingleChoiceRow("纹理压缩",
                listOf("不压缩" to 0, "半线 (halfline)" to 1, "LZ4" to 2, "LZ4+TLG5" to 3),
                SOFT_TEX_VALUES.indexOf(get("software_compress_tex", "none")).coerceAtLeast(0)) {
                set("software_compress_tex", SOFT_TEX_VALUES[it])
            }
        }

        SettingsSection("OpenGL 渲染器选项") {
            SettingSwitch("精确渲染", "修正半透明绘制顺序，更快但可能有色差",
                getBool("ogl_accurate_render", false)) { set("ogl_accurate_render", if (it) "1" else "0") }
            SingleChoiceRow("最大纹理尺寸",
                listOf("自动" to 0, "1024" to 1024, "2048" to 2048, "4096" to 4096, "8192" to 8192, "16384" to 16384),
                get("ogl_max_texsize", "0").toIntOrNull() ?: 0) {
                set("ogl_max_texsize", it.toString())
            }
            SingleChoiceRow("纹理压缩格式",
                listOf("不压缩" to 0, "半分辨率" to 1, "ETC2" to 2, "PVRTC" to 3),
                OGL_TEX_VALUES.indexOf(get("ogl_compress_tex", "none")).coerceAtLeast(0)) {
                set("ogl_compress_tex", OGL_TEX_VALUES[it])
            }
        }

        SettingsSection("OpenGL 扩展开关") {
            GL_EXT_KEYS.forEach { key ->
                SettingSwitch(
                    key.removePrefix("GL_"), key,
                    getBool(key, GL_EXT_DEFAULTS[key] ?: true)
                ) { set(key, if (it) "1" else "0") }
            }
        }

        SettingsSection("字体") {
            TextInputRow(
                title = "默认字体",
                subtitle = get("default_font", "").ifEmpty { "未设置（使用内置字体）" },
                dialogTitle = "输入字体文件路径（.ttf / .otf）",
                value = get("default_font", ""),
                onConfirm = { set("default_font", it.trim()) }
            )
            SettingSwitch("强制使用默认字体", "忽略游戏自带的字体文件",
                getBool("force_default_font", false)) { set("force_default_font", if (it) "1" else "0") }
        }
    }
}

// ---------------------------------------------------------------------------
// Kirikiroid2Preference.xml 读写（与引擎 iSysConfigManager::SaveToFile 同格式）
// ---------------------------------------------------------------------------

private fun prefFile(gamePath: String): File? {
    val f = File(gamePath)
    val dir = if (f.isDirectory) f else f.parentFile ?: return null
    return File(dir, PREF_FILENAME)
}

/** 读出已有的 Item 键值；文件不存在/损坏时返回空表（引擎语义：全部继承全局） */
private fun loadPrefDoc(gamePath: String): MutableMap<String, String> {
    val map = mutableStateMapOf<String, String>()
    val file = prefFile(gamePath) ?: return map
    if (!file.isFile) return map
    try {
        file.inputStream().use { ins ->
            val parser = Xml.newPullParser()
            parser.setInput(ins, null)
            var event = parser.eventType
            while (event != XmlPullParser.END_DOCUMENT) {
                if (event == XmlPullParser.START_TAG && parser.name == "Item") {
                    val k = parser.getAttributeValue(null, "key")
                    val v = parser.getAttributeValue(null, "value")
                    if (k != null) map[k] = v ?: ""
                }
                event = parser.next()
            }
        }
    } catch (t: Throwable) {
        // 文件损坏则视为空配置重写（引擎读不到也会同样回退全局）
    }
    return map
}

/** 全量重写文件；Custom/KeyMap 节点原样保留（从原文档摘出再回填） */
private fun savePrefDoc(gamePath: String, items: Map<String, String>) {
    val file = prefFile(gamePath) ?: return
    try {
        // 先从旧文档摘出 Custom/KeyMap（若有）
        val customs = mutableListOf<Pair<String, String>>()
        val keymaps = mutableListOf<Pair<String, String>>()
        if (file.isFile) {
            file.inputStream().use { ins ->
                val p = Xml.newPullParser()
                p.setInput(ins, null)
                var ev = p.eventType
                while (ev != XmlPullParser.END_DOCUMENT) {
                    if (ev == XmlPullParser.START_TAG) {
                        val k = p.getAttributeValue(null, "key")
                        val v = p.getAttributeValue(null, "value")
                        when (p.name) {
                            "Custom" -> if (k != null) customs.add(k to (v ?: ""))
                            "KeyMap" -> if (k != null) keymaps.add(k to (v ?: ""))
                        }
                    }
                    ev = p.next()
                }
            }
        }

        val ser = Xml.newSerializer()
        file.parentFile?.mkdirs()
        file.outputStream().use { os ->
            ser.setOutput(os, "UTF-8")
            ser.startDocument("UTF-8", true)
            ser.startTag(null, "GlobalPreference")
            fun item(tag: String, k: String, v: String) {
                ser.startTag(null, tag)
                ser.attribute(null, "key", k)
                ser.attribute(null, "value", v)
                ser.endTag(null, tag)
            }
            items.forEach { (k, v) -> item("Item", k, v) }
            customs.forEach { (k, v) -> item("Custom", k, v) }
            keymaps.forEach { (k, v) -> item("KeyMap", k, v) }
            ser.endTag(null, "GlobalPreference")
            ser.endDocument()
            ser.flush()
        }
    } catch (t: Throwable) {
        // 写盘失败（只读目录等）：静默，下次再试
    }
}

/** 个体值缺省时显示全局配置值（与引擎回退链一致） */
private fun globalValue(key: String, def: String): String =
    if (Kr2Bridge.available) {
        try {
            Kr2Bridge.getPref(key) ?: def
        } catch (t: Throwable) { def }
    } else def

private fun fmtFloat(v: Float): String =
    String.format(java.util.Locale.US, "%.6g", v)

private val MEM_VALUES = arrayOf("unlimited", "high", "medium", "low")
private val SOFT_TEX_VALUES = arrayOf("none", "halfline", "lz4", "lz4+tlg5")
private val OGL_TEX_VALUES = arrayOf("none", "half", "etc2", "pvrtc")
private val GL_EXT_KEYS = listOf(
    "GL_EXT_shader_framebuffer_fetch",
    "GL_ARM_shader_framebuffer_fetch",
    "GL_NV_shader_framebuffer_fetch",
    "GL_EXT_copy_image",
    "GL_OES_copy_image",
    "GL_ARB_copy_image",
    "GL_NV_copy_image",
    "GL_EXT_clear_texture",
    "GL_ARB_clear_texture",
    "GL_QCOM_alpha_test"
)
private val GL_EXT_DEFAULTS = mapOf(
    "GL_EXT_shader_framebuffer_fetch" to false,
    "GL_ARM_shader_framebuffer_fetch" to true,
    "GL_NV_shader_framebuffer_fetch" to true,
    "GL_EXT_copy_image" to false,
    "GL_OES_copy_image" to false,
    "GL_ARB_copy_image" to false,
    "GL_NV_copy_image" to false,
    "GL_EXT_clear_texture" to true,
    "GL_ARB_clear_texture" to true,
    "GL_QCOM_alpha_test" to true
)
