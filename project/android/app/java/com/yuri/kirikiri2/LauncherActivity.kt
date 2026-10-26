package com.yuri.kirikiri2

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.Settings
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.yuri.kirikiri2.bridge.Kr2Bridge
import com.yuri.kirikiri2.data.SettingsStore
import com.yuri.kirikiri2.ui.Kr2App
import com.yuri.kirikiri2.ui.theme.Kr2Theme
import java.io.File

/**
 * 新外壳入口：Jetpack Compose + Material 3。
 *
 * 原 MainActivity（cocos/SDL 引擎界面）保留不变，作为游戏运行时的承载。
 * 这里负责启动器、文件浏览与设置；点击启动时把路径交给引擎
 * （引擎未起来则暂存待启动路径，随后拉起 MainActivity 由引擎自动消费）。
 */
class LauncherActivity : ComponentActivity() {

    private lateinit var settings: SettingsStore

    /** 存储访问状态（P5 分区存储适配），onResume 时重查以感知从系统设置返回 */
    private var storageGranted by mutableStateOf(true)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        settings = SettingsStore(this)
        initBridge()

        // 首次启动直接落在帮助页（对应旧 UI 的 TVPTipsHelpForm 首启引导，
        // 新外壳拦截了启动路径，cocos 那套首启引导不会再触发）
        val prefs = getSharedPreferences("kr2_native", MODE_PRIVATE)
        val firstLaunch = !prefs.getBoolean("help_shown", false)
        if (firstLaunch) prefs.edit().putBoolean("help_shown", true).apply()

        storageGranted = checkStorageAccess()

        setContent {
            val mode = settings.darkMode // 读取即订阅，设置改动会自动重组
            val dark = when (mode) {
                1 -> false
                2 -> true
                else -> isSystemInDarkTheme()
            }
            Kr2Theme(darkTheme = dark, dynamicColor = settings.dynamicColor) {
                Kr2App(
                    store = settings,
                    startRoute = if (firstLaunch) "help" else "launcher",
                    storageGranted = storageGranted,
                    onRequestStorage = ::requestStorageAccess,
                    onLaunch = ::launchGame,
                    onXp3Repack = ::requestXp3Repack
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // 从游戏返回时，引擎可能改过配置（旧界面里也能改），重新拉一次
        settings.syncFromEngine()
        // 从系统「所有文件访问」设置页返回后重查授权状态
        storageGranted = checkStorageAccess()
    }

    // ------------------------------------------------------------------
    // P5 存储权限（分层策略，见 AndroidManifest.xml 注释）：
    //   API 30+: MANAGE_EXTERNAL_STORAGE → 引导到系统「所有文件访问」开关
    //   API 24-29: legacy 存储 → 运行时请求 READ/WRITE_EXTERNAL_STORAGE
    // ------------------------------------------------------------------
    private fun checkStorageAccess(): Boolean =
        if (Build.VERSION.SDK_INT >= 30) {
            Environment.isExternalStorageManager()
        } else {
            ContextCompat.checkSelfPermission(
                this, Manifest.permission.READ_EXTERNAL_STORAGE
            ) == PackageManager.PERMISSION_GRANTED
        }

    private fun requestStorageAccess() {
        if (Build.VERSION.SDK_INT >= 30) {
            try {
                startActivity(
                    Intent(
                        Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
                        Uri.parse("package:$packageName")
                    )
                )
            } catch (t: Throwable) {
                // 个别 ROM 未实现应用级入口，退回全局列表
                try {
                    startActivity(Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION))
                } catch (t2: Throwable) {
                    Log.w("KR2", "no all-files-access settings on this ROM")
                }
            }
        } else {
            ActivityCompat.requestPermissions(
                this, arrayOf(
                    Manifest.permission.READ_EXTERNAL_STORAGE,
                    Manifest.permission.WRITE_EXTERNAL_STORAGE
                ), RC_STORAGE
            )
        }
    }

    /** 引擎桥接：native 实现未链接时静默失败，UI 层自动回退 */
    private fun initBridge() {
        try {
            System.loadLibrary("krkr2yuri")
            Kr2Bridge.markAvailable()
            // 让引擎能定位 <filesDir>/.preference/ 下的 recentpath.xml / GlobalPreference.xml
            Kr2Bridge.nativeSetDataDir(filesDir.absolutePath)
        } catch (t: Throwable) {
            Log.w("KR2", "bridge unavailable: ${t.message}")
            Kr2Bridge.available = false
        }
    }

    private fun launchGame(file: File) {
        val path = file.absolutePath
        // 交给引擎：写入最近列表；引擎已在跑则就地切换，否则暂存待启动路径
        val hot = try {
            if (Kr2Bridge.available) Kr2Bridge.nativeLaunch(path) else false
        } catch (t: Throwable) {
            Log.w("KR2", "nativeLaunch failed: ${t.message}")
            false
        }
        Log.d("KR2", "launch $path (hot=$hot)")
        // CLEAR_TOP + SINGLE_TOP：已存在 MainActivity 时复用它（避免第二个引擎实例），
        // 否则新建。引擎起来后会消费待启动路径直接进入游戏。
        startActivity(
            Intent(this, MainActivity::class.java)
                .putExtra("path", path)
                .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        )
    }

    /**
     * 「转换/解密XP3封包」：对指定目录发起引擎自带的封包转换流程。
     * 引擎已在跑则转换界面直接弹出；否则暂存目录并拉起 MainActivity，
     * 引擎启动完成后会自动弹出确认框 / 封包列表 / 进度条（与旧版一致）。
     */
    private fun requestXp3Repack(dir: File) {
        val submitted = try {
            if (Kr2Bridge.available) Kr2Bridge.requestXp3Repack(dir.absolutePath) else false
        } catch (t: Throwable) {
            Log.w("KR2", "requestXp3Repack failed: ${t.message}")
            false
        }
        Log.d("KR2", "xp3 repack ${dir.absolutePath} (submitted=$submitted)")
        if (!submitted) {
            // 引擎未运行：拉起引擎界面，启动流程会消费暂存的转换请求；
            // 转换界面底下是引擎自带的文件选择器（与旧版触发场景一致）
            startActivity(
                Intent(this, MainActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            )
        }
    }

    private companion object {
        const val RC_STORAGE = 100
    }
}
