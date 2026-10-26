package com.yuri.kirikiri2.data

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.yuri.kirikiri2.bridge.Kr2Bridge
import java.util.Locale

/**
 * 设置项存储。
 *
 * 真数据源是引擎的 <filesDir>/.preference/GlobalPreference.xml（键值见
 * src/core/environ/ui/PreferenceConfig.h）。JNI 桥接可用时：
 *   - 构造时把引擎值拉回本地，保证 UI 显示的就是引擎实际使用的配置；
 *   - 每次写入同时落到引擎，新的 Compose 界面与旧的 cocos 界面共用同一份配置。
 * 桥接不可用时退化成纯 SharedPreferences（界面仍可独立运行）。
 */
class SettingsStore(context: Context) {

    private val pref = context.getSharedPreferences("kr2_settings", Context.MODE_PRIVATE)

    // ---------------- 仅外壳使用的外观项 ----------------

    private var _dynamicColor by mutableStateOf(pref.getBoolean("dynamic_color", true))
    var dynamicColor: Boolean
        get() = _dynamicColor
        set(v) { _dynamicColor = v; pref.edit().putBoolean("dynamic_color", v).apply() }

    /** 0 跟随系统 / 1 浅色 / 2 深色 */
    private var _darkMode by mutableIntStateOf(pref.getInt("dark_mode", 0))
    var darkMode: Int
        get() = _darkMode
        set(v) { _darkMode = v; pref.edit().putInt("dark_mode", v).apply() }

    // ---------------- 与引擎共用的配置项 ----------------

    /** 0 OpenGL（试验性） / 1 软件渲染器 —— 对应引擎 renderer=opengl|software */
    private var _renderer by mutableIntStateOf(pref.getInt("renderer", 1))
    var renderer: Int
        get() = _renderer
        set(v) {
            _renderer = v
            pref.edit().putInt("renderer", v).apply()
            pushString(KEY_RENDERER, if (v == 0) "opengl" else "software")
        }

    /** 帧率上限数值（60 / 45 / 30 / 15）—— 对应引擎 fps_limit */
    private var _fpsLimit by mutableIntStateOf(pref.getInt("fps_limit", 60))
    var fpsLimit: Int
        get() = _fpsLimit
        set(v) {
            _fpsLimit = v
            pref.edit().putInt("fps_limit", v).apply()
            pushString(KEY_FPS_LIMIT, v.toString())
        }

    /** 菜单按钮不透明度 0..1 —— 对应引擎 menu_handler_opa */
    private var _menuOpacity by mutableFloatStateOf(pref.getFloat("menu_handler_opa", 0.15f))
    var menuOpacity: Float
        get() = _menuOpacity
        set(v) {
            _menuOpacity = v
            pref.edit().putFloat("menu_handler_opa", v).apply()
            pushString(KEY_MENU_OPA, fmtFloat(v))
        }

    /** 虚拟鼠标缩放比 0..1 —— 对应引擎 vcursor_scale */
    private var _cursorScale by mutableFloatStateOf(pref.getFloat("vcursor_scale", 0.5f))
    var cursorScale: Float
        get() = _cursorScale
        set(v) {
            _cursorScale = v
            pref.edit().putFloat("vcursor_scale", v).apply()
            pushString(KEY_CURSOR_SCALE, fmtFloat(v))
        }

    /** 记住最后使用的路径 —— 对应引擎 remember_last_path */
    private var _rememberLastPath by mutableStateOf(pref.getBoolean("remember_last_path", true))
    var rememberLastPath: Boolean
        get() = _rememberLastPath
        set(v) {
            _rememberLastPath = v
            pref.edit().putBoolean("remember_last_path", v).apply()
            pushString(KEY_REMEMBER_PATH, if (v) "1" else "0")
        }

    /** 保持屏幕常亮 —— 对应引擎 keep_screen_alive */
    private var _keepScreenOn by mutableStateOf(pref.getBoolean("keep_screen_alive", true))
    var keepScreenOn: Boolean
        get() = _keepScreenOn
        set(v) {
            _keepScreenOn = v
            pref.edit().putBoolean("keep_screen_alive", v).apply()
            pushString(KEY_KEEP_SCREEN, if (v) "1" else "0")
        }

    /** 显示 FPS —— 对应引擎 showfps */
    private var _showFps by mutableStateOf(pref.getBoolean("showfps", false))
    var showFps: Boolean
        get() = _showFps
        set(v) {
            _showFps = v
            pref.edit().putBoolean("showfps", v).apply()
            pushString(KEY_SHOW_FPS, if (v) "1" else "0")
        }

    // ---------------- P6 补齐：旧 GlobalPreferenceForm 其余项 ----------------

    /** 新版游戏内菜单浮层（仅外壳使用的开关；关 = 引擎回落到 cocos 旧菜单） */
    private var _useNativeMenu by mutableStateOf(pref.getBoolean("use_native_menu", true))
    var useNativeMenu: Boolean
        get() = _useNativeMenu
        set(v) { _useNativeMenu = v; pref.edit().putBoolean("use_native_menu", v).apply() }

    /** 输出日志 —— 对应引擎 outputlog（默认开） */
    private var _outputLog by mutableStateOf(pref.getBoolean("outputlog", true))
    var outputLog: Boolean
        get() = _outputLog
        set(v) {
            _outputLog = v
            pref.edit().putBoolean("outputlog", v).apply()
            pushString(KEY_OUTPUT_LOG, if (v) "1" else "0")
        }

    /** 内存限制 0=unlimited 1=high 2=medium 3=low —— 对应引擎 memusage */
    private var _memUsage by mutableIntStateOf(pref.getInt("memusage", 0))
    var memUsage: Int
        get() = _memUsage
        set(v) {
            _memUsage = v
            pref.edit().putInt("memusage", v).apply()
            pushString(KEY_MEM_USAGE, MEM_VALUES[v])
        }

    /** 隐藏安卓系统按钮 —— 对应引擎 hide_android_sys_btn */
    private var _hideSysButtons by mutableStateOf(pref.getBoolean("hide_android_sys_btn", false))
    var hideSysButtons: Boolean
        get() = _hideSysButtons
        set(v) {
            _hideSysButtons = v
            pref.edit().putBoolean("hide_android_sys_btn", v).apply()
            pushString(KEY_HIDE_SYS_BTN, if (v) "1" else "0")
        }

    /** 默认字体文件路径 —— 对应引擎 default_font（空 = 内置字体） */
    private var _defaultFont by mutableStateOf(pref.getString("default_font", "") ?: "")
    var defaultFont: String
        get() = _defaultFont
        set(v) {
            _defaultFont = v
            pref.edit().putString("default_font", v).apply()
            pushString(KEY_DEFAULT_FONT, v)
        }

    /** 强制使用默认字体 —— 对应引擎 force_default_font */
    private var _forceDefaultFont by mutableStateOf(pref.getBoolean("force_default_font", false))
    var forceDefaultFont: Boolean
        get() = _forceDefaultFont
        set(v) {
            _forceDefaultFont = v
            pref.edit().putBoolean("force_default_font", v).apply()
            pushString(KEY_FORCE_DEF_FONT, if (v) "1" else "0")
        }

    /** 软件渲染绘制线程数 0=自动 1..8 —— 对应引擎 software_draw_thread */
    private var _softwareDrawThread by mutableIntStateOf(pref.getInt("software_draw_thread", 0))
    var softwareDrawThread: Int
        get() = _softwareDrawThread
        set(v) {
            _softwareDrawThread = v
            pref.edit().putInt("software_draw_thread", v).apply()
            pushString(KEY_SOFT_DRAW_THREAD, v.toString())
        }

    /** 软件渲染纹理压缩 0=none 1=halfline 2=lz4 3=lz4+TLG5 —— 对应引擎 software_compress_tex */
    private var _softwareCompressTex by mutableIntStateOf(pref.getInt("software_compress_tex", 0))
    var softwareCompressTex: Int
        get() = _softwareCompressTex
        set(v) {
            _softwareCompressTex = v
            pref.edit().putInt("software_compress_tex", v).apply()
            pushString(KEY_SOFT_COMPRESS_TEX, SOFT_TEX_VALUES[v])
        }

    /** OpenGL 精确渲染 —— 对应引擎 ogl_accurate_render */
    private var _oglAccurateRender by mutableStateOf(pref.getBoolean("ogl_accurate_render", false))
    var oglAccurateRender: Boolean
        get() = _oglAccurateRender
        set(v) {
            _oglAccurateRender = v
            pref.edit().putBoolean("ogl_accurate_render", v).apply()
            pushString(KEY_OGL_ACCURATE, if (v) "1" else "0")
        }

    /** OpenGL 最大纹理尺寸 0=自动 1024..16384 —— 对应引擎 ogl_max_texsize */
    private var _oglMaxTexSize by mutableIntStateOf(pref.getInt("ogl_max_texsize", 0))
    var oglMaxTexSize: Int
        get() = _oglMaxTexSize
        set(v) {
            _oglMaxTexSize = v
            pref.edit().putInt("ogl_max_texsize", v).apply()
            pushString(KEY_OGL_MAX_TEX, v.toString())
        }

    /** OpenGL 纹理压缩 0=none 1=half 2=etc2 3=pvrtc —— 对应引擎 ogl_compress_tex */
    private var _oglCompressTex by mutableIntStateOf(pref.getInt("ogl_compress_tex", 0))
    var oglCompressTex: Int
        get() = _oglCompressTex
        set(v) {
            _oglCompressTex = v
            pref.edit().putInt("ogl_compress_tex", v).apply()
            pushString(KEY_OGL_COMPRESS_TEX, OGL_TEX_VALUES[v])
        }

    /** GL 扩展开关（键名即引擎配置键，见 PreferenceConfig.h OpenglOptPreference） */
    private val _glExts = mutableStateMapOf<String, Boolean>()
    val glExtKeys = listOf(
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

    fun glExt(key: String): Boolean {
        if (_glExts.containsKey(key)) return _glExts.getValue(key)
        val v = if (pref.contains(key)) pref.getBoolean(key, true) else GL_EXT_DEFAULTS[key] ?: true
        _glExts[key] = v
        return v
    }

    fun setGlExt(key: String, v: Boolean) {
        _glExts[key] = v
        pref.edit().putBoolean(key, v).apply()
        pushString(key, if (v) "1" else "0")
    }

    // ---------------- 引擎同步 ----------------

    init {
        if (Kr2Bridge.available) syncFromEngine()
    }

    /** 用引擎里的真实配置覆盖本地（桥接可用时） */
    fun syncFromEngine() {
        if (!Kr2Bridge.available) return
        engineString(KEY_RENDERER)?.let {
            _renderer = if (it == "opengl") 0 else 1
            pref.edit().putInt("renderer", _renderer).apply()
        }
        engineString(KEY_FPS_LIMIT)?.toIntOrNull()?.let {
            _fpsLimit = it
            pref.edit().putInt("fps_limit", it).apply()
        }
        engineString(KEY_MENU_OPA)?.toFloatOrNull()?.let {
            _menuOpacity = it
            pref.edit().putFloat("menu_handler_opa", it).apply()
        }
        engineString(KEY_CURSOR_SCALE)?.toFloatOrNull()?.let {
            _cursorScale = it
            pref.edit().putFloat("vcursor_scale", it).apply()
        }
        engineString(KEY_REMEMBER_PATH)?.let {
            val v = it != "0"
            _rememberLastPath = v
            pref.edit().putBoolean("remember_last_path", v).apply()
        }
        engineString(KEY_KEEP_SCREEN)?.let {
            val v = it != "0"
            _keepScreenOn = v
            pref.edit().putBoolean("keep_screen_alive", v).apply()
        }
        engineString(KEY_SHOW_FPS)?.let {
            val v = it != "0"
            _showFps = v
            pref.edit().putBoolean("showfps", v).apply()
        }
        engineString(KEY_OUTPUT_LOG)?.let {
            val v = it != "0"
            _outputLog = v
            pref.edit().putBoolean("outputlog", v).apply()
        }
        engineString(KEY_MEM_USAGE)?.let { v ->
            MEM_VALUES.indexOf(v).takeIf { it >= 0 }?.let { idx ->
                _memUsage = idx
                pref.edit().putInt("memusage", idx).apply()
            }
        }
        engineString(KEY_HIDE_SYS_BTN)?.let {
            val v = it != "0"
            _hideSysButtons = v
            pref.edit().putBoolean("hide_android_sys_btn", v).apply()
        }
        engineString(KEY_DEFAULT_FONT)?.let {
            _defaultFont = it
            pref.edit().putString("default_font", it).apply()
        }
        engineString(KEY_FORCE_DEF_FONT)?.let {
            val v = it != "0"
            _forceDefaultFont = v
            pref.edit().putBoolean("force_default_font", v).apply()
        }
        engineString(KEY_SOFT_DRAW_THREAD)?.toIntOrNull()?.let {
            val v = it.coerceIn(0, 8)
            _softwareDrawThread = v
            pref.edit().putInt("software_draw_thread", v).apply()
        }
        engineString(KEY_SOFT_COMPRESS_TEX)?.let { v ->
            SOFT_TEX_VALUES.indexOf(v).takeIf { it >= 0 }?.let { idx ->
                _softwareCompressTex = idx
                pref.edit().putInt("software_compress_tex", idx).apply()
            }
        }
        engineString(KEY_OGL_ACCURATE)?.let {
            val v = it != "0"
            _oglAccurateRender = v
            pref.edit().putBoolean("ogl_accurate_render", v).apply()
        }
        engineString(KEY_OGL_MAX_TEX)?.toIntOrNull()?.let {
            _oglMaxTexSize = it
            pref.edit().putInt("ogl_max_texsize", it).apply()
        }
        engineString(KEY_OGL_COMPRESS_TEX)?.let { v ->
            OGL_TEX_VALUES.indexOf(v).takeIf { it >= 0 }?.let { idx ->
                _oglCompressTex = idx
                pref.edit().putInt("ogl_compress_tex", idx).apply()
            }
        }
        glExtKeys.forEach { key ->
            engineString(key)?.let {
                val v = it != "0"
                _glExts[key] = v
                pref.edit().putBoolean(key, v).apply()
            }
        }
    }

    private fun engineString(key: String): String? = Kr2Bridge.getPref(key)
    private fun pushString(key: String, value: String) {
        Kr2Bridge.setPref(key, value)
    }

    private fun fmtFloat(v: Float): String {
        // 与引擎 iSysConfigManager::SetValueFloat 的 sprintf("%g") 对齐
        val s = String.format(Locale.US, "%.6g", v)
        return s
    }

    private companion object {
        const val KEY_RENDERER = "renderer"
        const val KEY_FPS_LIMIT = "fps_limit"
        const val KEY_MENU_OPA = "menu_handler_opa"
        const val KEY_CURSOR_SCALE = "vcursor_scale"
        const val KEY_REMEMBER_PATH = "remember_last_path"
        const val KEY_KEEP_SCREEN = "keep_screen_alive"
        const val KEY_SHOW_FPS = "showfps"
        const val KEY_OUTPUT_LOG = "outputlog"
        const val KEY_MEM_USAGE = "memusage"
        const val KEY_HIDE_SYS_BTN = "hide_android_sys_btn"
        const val KEY_DEFAULT_FONT = "default_font"
        const val KEY_FORCE_DEF_FONT = "force_default_font"
        const val KEY_SOFT_DRAW_THREAD = "software_draw_thread"
        const val KEY_SOFT_COMPRESS_TEX = "software_compress_tex"
        const val KEY_OGL_ACCURATE = "ogl_accurate_render"
        const val KEY_OGL_MAX_TEX = "ogl_max_texsize"
        const val KEY_OGL_COMPRESS_TEX = "ogl_compress_tex"

        // 与 PreferenceConfig.h 的选项表逐项对齐
        val MEM_VALUES = arrayOf("unlimited", "high", "medium", "low")
        val SOFT_TEX_VALUES = arrayOf("none", "halfline", "lz4", "lz4+tlg5")
        val OGL_TEX_VALUES = arrayOf("none", "half", "etc2", "pvrtc")
        val GL_EXT_DEFAULTS = mapOf(
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
    }
}
