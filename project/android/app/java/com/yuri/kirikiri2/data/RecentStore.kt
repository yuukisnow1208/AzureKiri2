package com.yuri.kirikiri2.data

import android.content.Context
import com.yuri.kirikiri2.bridge.Kr2Bridge
import java.io.File

/**
 * 最近启动记录。
 *
 * JNI 桥接可用时，直接读写引擎侧的 <filesDir>/.preference/recentpath.xml，
 * 与内置（cocos）界面完全共用同一份数据；不可用时回退到 SharedPreferences，
 * 保证新界面在没有引擎的情况下也能独立运行。
 */
data class RecentGame(
    val path: String,      // 完整路径（目录或启动文件）
    val name: String,      // 展示名（目录名 / 文件名）
    val parent: String,    // 所在目录，用于辅助行展示
    val time: Long         // 最近启动时间
)

object RecentStore {
    private const val PREF = "kr2_recent"
    private const val KEY = "list"

    fun load(context: Context): List<RecentGame> {
        // 1) 引擎侧（真数据源）
        val native = Kr2Bridge.recentList()
        if (native != null) {
            val now = System.currentTimeMillis()
            val list = native.mapIndexed { i, e ->
                // recentpath.xml 只保存顺序，不保存时间戳；用下标反推一个稳定的次序值
                RecentGame(e.path, e.name, e.parent, now - i)
            }
            // 顺带同步一份到本地，作为引擎不可用时的兜底
            saveLocal(context, list)
            return list
        }
        // 2) 本地兜底
        return loadLocal(context)
    }

    fun add(context: Context, file: File) {
        if (Kr2Bridge.addRecent(file.absolutePath)) return
        val list = loadLocal(context).toMutableList()
        val path = file.absolutePath
        list.removeAll { it.path == path }
        list.add(
            0, RecentGame(
                path = path,
                name = file.name,
                parent = file.parent ?: "",
                time = System.currentTimeMillis()
            )
        )
        saveLocal(context, list.take(50))
    }

    fun remove(context: Context, path: String) {
        if (Kr2Bridge.available) {
            Kr2Bridge.removeRecent(path)
            return
        }
        saveLocal(context, loadLocal(context).filterNot { it.path == path })
    }

    // ---------------- 本地（SharedPreferences）实现 ----------------

    private fun loadLocal(context: Context): List<RecentGame> {
        val raw = context.getSharedPreferences(PREF, Context.MODE_PRIVATE)
            .getString(KEY, "") ?: ""
        if (raw.isEmpty()) return emptyList()
        return raw.split("\n").mapNotNull { line ->
            val f = line.split("\t")
            if (f.size >= 4) RecentGame(f[1], f[2], f[3], f[0].toLongOrNull() ?: 0L) else null
        }.sortedByDescending { it.time }
    }

    private fun saveLocal(context: Context, list: List<RecentGame>) {
        val raw = list.joinToString("\n") { "${it.time}\t${it.path}\t${it.name}\t${it.parent}" }
        context.getSharedPreferences(PREF, Context.MODE_PRIVATE)
            .edit().putString(KEY, raw).apply()
    }
}

/** 判断一个目录是否像"可启动的游戏"：含 startup.tjs 或 xp3 数据包 */
fun looksLikeGame(dir: File): Boolean {
    if (!dir.isDirectory) return false
    if (File(dir, "startup.tjs").exists()) return true
    return dir.listFiles()?.any { it.isFile && it.extension.equals("xp3", true) } ?: false
}
