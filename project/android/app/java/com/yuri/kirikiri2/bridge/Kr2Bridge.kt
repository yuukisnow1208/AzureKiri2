package com.yuri.kirikiri2.bridge

import org.json.JSONArray

/**
 * Kotlin ↔ 引擎（C++）的桥接层。
 *
 * native 实现位于 project/android/app/cpp/kr2_bridge_jni.cpp，
 * 复用引擎现有的 recentpath.xml / GlobalPreference.xml 与 TVPMainScene::startupFrom()，
 * 不重写业务规则。
 *
 * 未链接到实现时（available == false）所有方法返回 null / false，
 * UI 层自动回退到 SharedPreferences 实现，保证新界面可独立运行。
 */
object Kr2Bridge {

    /** native 库是否可用（由 LauncherActivity 在 System.loadLibrary 成功后设置） */
    @Volatile
    var available: Boolean = false

    fun markAvailable() {
        available = try {
            nativePing()
        } catch (t: Throwable) {
            false
        }
    }

    // ---------- 引擎能力 ----------
    external fun nativePing(): Boolean

    /**
     * 请求引擎启动指定路径。
     * 返回 true  = 引擎已在运行，已就地切换游戏（外壳无需再拉起 Activity）
     * 返回 false = 引擎未起来，待启动路径已暂存；外壳拉起 MainActivity 后引擎会自动消费
     */
    external fun nativeLaunch(path: String): Boolean

    /** 告知引擎应用数据目录（用于定位 .preference/ 下的配置文件） */
    external fun nativeSetDataDir(path: String)

    /** 读取引擎侧最近启动记录（JSON 数组），未实现时返回 null */
    external fun nativeRecentList(): String?

    external fun nativeAddRecent(path: String): Boolean

    external fun nativeRemoveRecent(path: String): Boolean

    /** 读取 / 写入引擎偏好设置（GlobalPreference.xml），未设置时返回 null */
    external fun nativeGetPref(key: String): String?
    external fun nativeSetPref(key: String, value: String): Boolean

    // ---------- 游戏内菜单（P4） ----------

    /** true = 交给 Compose 浮层，false = 回到引擎自带的 cocos 菜单 */
    external fun nativeSetNativeGameMenu(on: Boolean)

    /** 0=游戏菜单 1=窗口管理 2=切换鼠标/触摸 3=输入法 4=退出 */
    external fun nativeGameMenuAction(action: Int): Boolean

    /** 当前是否虚拟鼠标模式（用于切换按钮图标） */
    external fun nativeIsVirtualMouse(): Boolean

    // ---------- 游戏内菜单树（M3） ----------

    /**
     * TJS 菜单树 JSON：{"c":标题,"children":[{i,c,sep,sub,grp,checked,children?}]}
     * 序列化语义与 ui/InGameMenuForm.cpp 一致；无菜单/树为空时返回 null
     */
    external fun nativeGameMenuJson(): String?

    /** 点击菜单项（前序遍历索引），动作在 GL 线程异步执行 */
    external fun nativeGameMenuItemClick(index: Int): Boolean

    // ---------- 窗口管理（M3 顶栏驱动引擎动作） ----------

    /** 0=上一窗口 1=下一窗口 2=画面填充 3=完成 */
    external fun nativeWindowManagerAction(action: Int): Boolean

    /** bit0=有上一窗口 bit1=有下一窗口 bit2=窗口管理进行中 */
    external fun nativeWindowManagerState(): Int

    // ---------- XP3 封包转换（转换/解密XP3封包） ----------

    /**
     * 请求引擎对指定目录执行「转换/解密XP3封包」（复用引擎自带的 XP3RepackForm 流程）。
     * 返回 true = 引擎已在运行，转换界面已直接弹出；
     * 返回 false = 目录已暂存，外壳需拉起 MainActivity，引擎启动后自动弹出转换界面
     */
    external fun nativeRequestXp3Repack(dir: String): Boolean

    fun requestXp3Repack(dir: String): Boolean {
        if (!available || dir.isEmpty()) return false
        return try {
            nativeRequestXp3Repack(dir)
        } catch (t: Throwable) {
            false
        }
    }

    // ---------- 便捷封装 ----------

    /** 最近列表；native 不可用时返回 null，由调用方回退到 SharedPreferences */
    fun recentList(): List<RecentEntry>? {
        if (!available) return null
        val raw = try {
            nativeRecentList()
        } catch (t: Throwable) {
            null
        } ?: return null
        return try {
            val arr = JSONArray(raw)
            val out = ArrayList<RecentEntry>(arr.length())
            for (i in 0 until arr.length()) {
                val o = arr.optJSONObject(i) ?: continue
                val path = o.optString("path", "")
                if (path.isEmpty()) continue
                out += RecentEntry(
                    path = path,
                    name = o.optString("name", path.substringAfterLast('/')),
                    parent = o.optString("parent", path.substringBeforeLast('/', "")),
                )
            }
            out
        } catch (t: Throwable) {
            null
        }
    }

    fun addRecent(path: String): Boolean {
        if (!available || path.isEmpty()) return false
        return try {
            nativeAddRecent(path)
        } catch (t: Throwable) {
            false
        }
    }

    fun removeRecent(path: String) {
        if (!available) return
        try {
            nativeRemoveRecent(path)
        } catch (t: Throwable) {
            // ignore
        }
    }

    fun getPref(key: String): String? {
        if (!available) return null
        return try {
            nativeGetPref(key)
        } catch (t: Throwable) {
            null
        }
    }

    fun setPref(key: String, value: String): Boolean {
        if (!available) return false
        return try {
            nativeSetPref(key, value)
        } catch (t: Throwable) {
            false
        }
    }

    /** TJS 菜单树 JSON；不可用/无菜单时返回 null */
    fun gameMenuJson(): String? {
        if (!available) return null
        return try {
            nativeGameMenuJson()
        } catch (t: Throwable) {
            null
        }
    }

    fun gameMenuItemClick(index: Int): Boolean {
        if (!available) return false
        return try {
            nativeGameMenuItemClick(index)
        } catch (t: Throwable) {
            false
        }
    }

    fun windowManagerAction(action: Int): Boolean {
        if (!available) return false
        return try {
            nativeWindowManagerAction(action)
        } catch (t: Throwable) {
            false
        }
    }

    fun windowManagerState(): Int {
        if (!available) return 0
        return try {
            nativeWindowManagerState()
        } catch (t: Throwable) {
            0
        }
    }
}

/** 引擎侧最近启动记录条目（对应 RecentStore.RecentGame 的子集） */
data class RecentEntry(
    val path: String,
    val name: String,
    val parent: String,
)
