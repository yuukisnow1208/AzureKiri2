package com.yuri.kirikiri2.data

import android.content.Context
import org.json.JSONObject
import java.io.File
import java.security.MessageDigest

/**
 * 游戏库元数据：游玩总时长、上次启动时间、封面图。
 *
 * 以游戏路径的 MD5 为 id，持久化在 <filesDir>/library.json；
 * 截图库按游戏分目录存放在 <filesDir>/screenshots/<id>/，
 * 封面固定复制到 <filesDir>/covers/<id>.jpg（截图被删也不影响封面）。
 */
data class GameMeta(
    val path: String,
    val playtimeMs: Long = 0,
    val lastPlayed: Long = 0,
    val coverPath: String? = null,
    val name: String? = null, // 用户自定义名称；null = 用默认（游戏所在文件夹名）
)

object LibraryStore {
    private const val FILE = "library.json"

    fun gameId(path: String): String =
        MessageDigest.getInstance("MD5").digest(path.toByteArray())
            .joinToString("") { "%02x".format(it) }

    fun load(context: Context): Map<String, GameMeta> = try {
        val file = File(context.filesDir, FILE)
        val root = JSONObject(if (file.exists()) file.readText() else "{}")
        val games = root.optJSONObject("games") ?: JSONObject()
        games.keys().asSequence().mapNotNull { id ->
            val o = games.optJSONObject(id) ?: return@mapNotNull null
            val path = o.optString("path")
            if (path.isEmpty()) null else id to GameMeta(
                path = path,
                playtimeMs = o.optLong("playtime"),
                lastPlayed = o.optLong("last"),
                coverPath = o.optString("cover").ifEmpty { null },
                name = o.optString("name").ifEmpty { null },
            )
        }.toMap()
    } catch (t: Throwable) {
        emptyMap()
    }

    private fun save(context: Context, games: Map<String, GameMeta>) {
        try {
            val gamesJson = JSONObject()
            games.forEach { (id, m) ->
                gamesJson.put(
                    id, JSONObject()
                        .put("path", m.path)
                        .put("playtime", m.playtimeMs)
                        .put("last", m.lastPlayed)
                        .put("cover", m.coverPath ?: "")
                        .put("name", m.name ?: "")
                )
            }
            File(context.filesDir, FILE).writeText(JSONObject().put("games", gamesJson).toString())
        } catch (t: Throwable) {
            // 持久化失败不致命，下次再写
        }
    }

    private fun upsert(context: Context, path: String, transform: (GameMeta) -> GameMeta) {
        val games = load(context).toMutableMap()
        val id = gameId(path)
        games[id] = transform(games[id] ?: GameMeta(path = path))
        save(context, games)
    }

    fun meta(context: Context, path: String): GameMeta =
        load(context)[gameId(path)] ?: GameMeta(path = path)

    // ---------------- 命名 ----------------

    /** 默认名：文件夹游戏用自身名，xp3 文件用所在文件夹名（data.xp3 → SETUP 这类） */
    fun defaultName(path: String): String {
        val f = File(path)
        return if (f.isDirectory) f.name
        else f.parent?.substringAfterLast('/')?.takeIf { it.isNotEmpty() } ?: f.name
    }

    /** 展示名：用户命名 → 默认名 */
    fun displayName(context: Context, path: String): String =
        meta(context, path).name ?: defaultName(path)

    fun setName(context: Context, path: String, name: String) {
        upsert(context, path) { it.copy(name = name.trim().ifEmpty { null }) }
    }

    // ---------------- 封面 ----------------

    fun coverFile(context: Context, gamePath: String): File {
        val dir = File(context.filesDir, "covers").apply { mkdirs() }
        return File(dir, "${gameId(gamePath)}.jpg")
    }

    /** 把图片（截图库文件 / 相册图）设为封面 */
    fun setCover(context: Context, gamePath: String, source: java.io.InputStream): Boolean = try {
        val dst = coverFile(context, gamePath)
        dst.outputStream().use { out -> source.copyTo(out) }
        upsert(context, gamePath) { it.copy(coverPath = dst.absolutePath) }
        true
    } catch (t: Throwable) {
        false
    }

    // ---------------- 游玩计时 ----------------

    private var sessionPath: String? = null
    private var sessionAnchor: Long = 0 // 本段计时起点；0 = 未在计时（离开前台）

    /** 开始 / 热切换游戏会话：先结算旧会话再开新段 */
    @Synchronized
    fun startSession(context: Context, path: String) {
        if (sessionPath == path) return
        settle(context)
        sessionPath = path
        sessionAnchor = System.currentTimeMillis()
        upsert(context, path) { it.copy(lastPlayed = sessionAnchor) }
    }

    /** 离开前台：结算本段时长 */
    @Synchronized
    fun pauseSession(context: Context) = settle(context)

    /** 回到前台：从现在继续计时 */
    @Synchronized
    fun resumeSession(context: Context) {
        if (sessionPath != null && sessionAnchor == 0L) {
            sessionAnchor = System.currentTimeMillis()
        }
    }

    @Synchronized
    fun endSession(context: Context) {
        settle(context)
        sessionPath = null
    }

    val currentGamePath: String?
        @Synchronized get() = sessionPath

    @Synchronized
    private fun settle(context: Context) {
        val path = sessionPath ?: return
        val anchor = sessionAnchor
        if (anchor > 0L) {
            val now = System.currentTimeMillis()
            if (now > anchor) addPlaytime(context, path, now - anchor)
        }
        sessionAnchor = 0L
    }

    private fun addPlaytime(context: Context, path: String, ms: Long) {
        if (ms <= 0) return
        upsert(context, path) { it.copy(playtimeMs = it.playtimeMs + ms) }
    }

    // ---------------- 截图库 ----------------

    fun screenshotDir(context: Context, gamePath: String): File =
        File(File(context.filesDir, "screenshots"), gameId(gamePath)).apply { mkdirs() }

    fun newScreenshotFile(context: Context, gamePath: String): File =
        File(screenshotDir(context, gamePath), "shot_${System.currentTimeMillis()}.png")

    /** 某游戏的全部截图（新 → 旧） */
    fun screenshots(context: Context, gamePath: String): List<File> =
        screenshotDir(context, gamePath)
            .listFiles { f -> f.isFile && f.extension.equals("png", true) }
            ?.sortedByDescending { it.lastModified() }
            ?: emptyList()

    /** 有截图的游戏（按上次启动排序） */
    fun gamesWithScreenshots(context: Context): List<GameMeta> =
        load(context).values
            .filter { screenshots(context, it.path).isNotEmpty() }
            .sortedByDescending { it.lastPlayed }
}

/**
 * 系统截图联动：在游戏前台时定期扫描系统截图目录（MIUI 三指下滑等），
 * 把新出现的截图复制进当前游戏的截图库。
 */
object SystemShotLinker {
    private val handler = android.os.Handler(android.os.Looper.getMainLooper())
    private val seen = mutableSetOf<String>()
    private var running = false

    private val dirs: List<File> by lazy {
        listOf(
            android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_SCREENSHOTS),
            File(android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_DCIM), "Screenshots"),
            File(android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_PICTURES), "Screenshots"),
        )
    }

    /** 周期扫描；onImported 在主线程回调（本次导入张数） */
    @Synchronized
    fun start(context: Context, onImported: (Int) -> Unit) {
        if (running) return
        running = true
        val app = context.applicationContext
        val task = object : Runnable {
            override fun run() {
                if (!running) return
                val path = LibraryStore.currentGamePath
                if (path != null) {
                    val n = importNew(app, path)
                    if (n > 0) onImported(n)
                }
                handler.postDelayed(this, 3000)
            }
        }
        handler.post(task)
    }

    @Synchronized
    fun stop() {
        running = false
        handler.removeCallbacksAndMessages(null)
    }

    private fun importNew(context: Context, gamePath: String): Int {
        var imported = 0
        for (dir in dirs) {
            val files = dir.listFiles { f ->
                f.isFile && f.extension.lowercase() in setOf("png", "jpg", "jpeg")
            } ?: continue
            for (f in files.sortedByDescending { it.lastModified() }.take(5)) {
                val key = "${f.absolutePath}@${f.lastModified()}"
                if (!seen.add(key)) continue
                // 只收最近 5 分钟内的截图，避免把历史截图整体搬进库里
                if (System.currentTimeMillis() - f.lastModified() > 5 * 60_000) continue
                val dst = LibraryStore.newScreenshotFile(context, gamePath)
                try {
                    f.inputStream().use { input -> dst.outputStream().use { input.copyTo(it) } }
                    imported++
                } catch (t: Throwable) {
                    seen.remove(key)
                }
            }
        }
        if (seen.size > 1000) seen.clear()
        return imported
    }
}
