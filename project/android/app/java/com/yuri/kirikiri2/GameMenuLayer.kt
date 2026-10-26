package com.yuri.kirikiri2

import android.app.Activity
import android.content.Context
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.Gravity
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.ComposeView
import com.yuri.kirikiri2.bridge.Kr2Bridge
import com.yuri.kirikiri2.data.LibraryStore
import com.yuri.kirikiri2.ui.ComposeLifecycleOwner
import com.yuri.kirikiri2.ui.GameMenuOverlay
import com.yuri.kirikiri2.ui.Kr2GameMenuDialog
import com.yuri.kirikiri2.ui.Kr2MenuNode
import com.yuri.kirikiri2.ui.Kr2WindowMgrBar
import com.yuri.kirikiri2.ui.createComposeView
import com.yuri.kirikiri2.ui.installComposeWindowOwners
import com.yuri.kirikiri2.ui.parseKr2Menu
import com.yuri.kirikiri2.ui.theme.Kr2Theme

/**
 * 游戏内菜单浮层（P4）。
 *
 * 直接叠在引擎的 GL 视图之上：折叠时只有右侧一个小把手，展开时才是动作面板，
 * 其余区域仍然透传给游戏，语义与原 cocos 的 TVPGameMainMenu 一致。
 * 「游戏菜单」（TJS 菜单树）与「窗口管理」的 UI 也由 Compose 以 M3 渲染，
 * 引擎侧只保留动作逻辑（kr2_bridge_jni.cpp / MainScene.cpp）。
 *
 * ComposeView 的窗口区域随模式切换：
 *   默认     = 右缘窄条（把手+面板）；游戏菜单打开 = 全屏（模态遮罩）；
 *   窗口管理 = 顶部横条（M3 顶栏），其余区域透传给游戏。
 *
 * 安全策略：任何一步失败都整体放弃，让引擎回退到原来的 cocos 菜单，
 * 绝不让浮层把游戏搞崩。
 */
class GameMenuLayer(private val activity: Activity) {

    private var owner: ComposeLifecycleOwner? = null
    private var composeView: ComposeView? = null

    private var expanded by mutableStateOf(false)
    private var virtualMouse by mutableStateOf(false)
    private var inGameMenu by mutableStateOf<Kr2MenuNode?>(null)
    private var windowMgr by mutableStateOf(false)
    private var windowMgrState by mutableStateOf(0)

    fun onCreate(savedInstanceState: Bundle?) {
        // 设置里关闭了新版菜单 → 不挂浮层也不通知引擎，MainScene 会照常创建 cocos 旧菜单
        val useNativeMenu = activity.getSharedPreferences("kr2_settings", Context.MODE_PRIVATE)
            .getBoolean("use_native_menu", true)
        if (!useNativeMenu) {
            Log.d("KR2", "native game menu disabled in settings, using cocos menu")
            return
        }
        try {
            System.loadLibrary("krkr2yuri")
            Kr2Bridge.markAvailable()
            Kr2Bridge.nativeSetDataDir(activity.filesDir.absolutePath)

            val lifecycleOwner = ComposeLifecycleOwner()
            lifecycleOwner.onCreate(savedInstanceState)
            owner = lifecycleOwner

            val view = createComposeView(activity as Context, lifecycleOwner) {
                Kr2Theme {
                    val menu = inGameMenu
                    when {
                        menu != null -> Kr2GameMenuDialog(
                            root = menu,
                            onItemClick = ::onMenuItemClick,
                            onDismiss = ::closeGameMenu,
                        )
                        windowMgr -> Kr2WindowMgrBar(
                            state = windowMgrState,
                            onAction = ::onWindowMgrAction,
                        )
                        else -> GameMenuOverlay(
                            expanded = expanded,
                            onExpandedChange = { expanded = it },
                            virtualMouse = virtualMouse,
                            onAction = ::onOverlayAction,
                        )
                    }
                }
            }
            composeView = view

            val decor = activity.window.decorView as? ViewGroup ?: return
            // Compose 重组器读的是窗口级 owner（decorView），必须先装再挂视图，
            // 否则 attach 时抛 ViewTreeLifecycleOwner not found 闪退
            installComposeWindowOwners(activity, lifecycleOwner)
            view.layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.MATCH_PARENT,
                Gravity.END or Gravity.CENTER_VERTICAL
            )
            decor.addView(view)

            // 通知引擎不要再创建 cocos 那套游戏内菜单
            Kr2Bridge.nativeSetNativeGameMenu(true)
            Log.d("KR2", "game menu overlay attached")
        } catch (t: Throwable) {
            Log.w("KR2", "game menu overlay unavailable, fallback to cocos menu: ${t.message}")
            composeView = null
            owner = null
        }
    }

    private fun onOverlayAction(action: Int) {
        try {
            when (action) {
                0 -> toggleGameMenu()
                1 -> {
                    Kr2Bridge.nativeGameMenuAction(1)
                    windowMgr = true
                    windowMgrState = Kr2Bridge.windowManagerState()
                    applyLayout()
                }
                5 -> captureScreenshot()
                else -> {
                    Kr2Bridge.nativeGameMenuAction(action)
                    virtualMouse = Kr2Bridge.nativeIsVirtualMouse()
                }
            }
        } catch (t: Throwable) {
            Log.w("KR2", "game menu action $action failed: ${t.message}")
        }
    }

    /**
     * 游戏内截图：PixelCopy 直取 GL SurfaceView 的画面（不含本浮层等上层视图），
     * 存入当前游戏的截图库（filesDir/screenshots/<gameId>/）。
     */
    private fun captureScreenshot() {
        try {
            val gamePath = LibraryStore.currentGamePath
            if (gamePath == null) {
                toast("当前没有运行中的游戏")
                return
            }
            val surface = findSurfaceView(activity.window.decorView)
            if (surface == null) {
                toast("找不到游戏画面")
                return
            }
            val bitmap = android.graphics.Bitmap.createBitmap(
                surface.width, surface.height, android.graphics.Bitmap.Config.ARGB_8888
            )
            val handler = Handler(Looper.getMainLooper())
            android.view.PixelCopy.request(
                surface, bitmap, { result ->
                    if (result == android.view.PixelCopy.SUCCESS) {
                        Thread {
                            try {
                                val dst = LibraryStore.newScreenshotFile(activity, gamePath)
                                java.io.FileOutputStream(dst).use { out ->
                                    bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, out)
                                }
                                handler.post { toast("截图已存入游戏截图库") }
                            } catch (t: Throwable) {
                                Log.w("KR2", "screenshot save failed: ${t.message}")
                                handler.post { toast("截图保存失败") }
                            }
                        }.start()
                    } else {
                        toast("截图失败")
                    }
                }, handler
            )
        } catch (t: Throwable) {
            Log.w("KR2", "screenshot failed: ${t.message}")
            toast("截图失败")
        }
    }

    private fun findSurfaceView(view: android.view.View): android.view.SurfaceView? {
        if (view is android.view.SurfaceView) return view
        if (view is ViewGroup) {
            for (i in 0 until view.childCount) {
                findSurfaceView(view.getChildAt(i))?.let { return it }
            }
        }
        return null
    }

    private fun toast(msg: String) {
        android.widget.Toast.makeText(activity, msg, android.widget.Toast.LENGTH_SHORT).show()
    }

    private fun toggleGameMenu() {
        if (inGameMenu != null) {
            closeGameMenu()
            return
        }
        val root = Kr2Bridge.gameMenuJson()?.let(::parseKr2Menu)
        if (root == null) {
            Log.d("KR2", "game menu tree unavailable")
            return
        }
        inGameMenu = root
        applyLayout()
    }

    private fun closeGameMenu() {
        inGameMenu = null
        applyLayout()
    }

    private fun onMenuItemClick(node: Kr2MenuNode) {
        try {
            Kr2Bridge.gameMenuItemClick(node.index)
        } catch (t: Throwable) {
            Log.w("KR2", "menu item ${node.index} failed: ${t.message}")
        }
    }

    private fun onWindowMgrAction(action: Int) {
        try {
            Kr2Bridge.windowManagerAction(action)
            if (action == 3) {
                windowMgr = false
                applyLayout()
            } else {
                // 动作在 GL 线程异步执行，稍后刷新上一个/下一个窗口的可用状态
                Handler(Looper.getMainLooper()).postDelayed({
                    windowMgrState = Kr2Bridge.windowManagerState()
                }, 120)
            }
        } catch (t: Throwable) {
            Log.w("KR2", "window mgr action $action failed: ${t.message}")
        }
    }

    /** 按当前模式切换 ComposeView 的窗口区域（见类注释） */
    private fun applyLayout() {
        val view = composeView ?: return
        view.layoutParams = when {
            inGameMenu != null -> FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            windowMgr -> FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.TOP
            )
            else -> FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.MATCH_PARENT,
                Gravity.END or Gravity.CENTER_VERTICAL
            )
        }
    }

    fun onStart() { owner?.handle(androidx.lifecycle.Lifecycle.Event.ON_START) }
    fun onResume() {
        owner?.handle(androidx.lifecycle.Lifecycle.Event.ON_RESUME)
        try {
            virtualMouse = Kr2Bridge.nativeIsVirtualMouse()
        } catch (t: Throwable) { /* ignore */ }
    }
    fun onPause() { owner?.handle(androidx.lifecycle.Lifecycle.Event.ON_PAUSE) }
    fun onStop() { owner?.handle(androidx.lifecycle.Lifecycle.Event.ON_STOP) }
    fun onDestroy() { owner?.handle(androidx.lifecycle.Lifecycle.Event.ON_DESTROY) }
}
