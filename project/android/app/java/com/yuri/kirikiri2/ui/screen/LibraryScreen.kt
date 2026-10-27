package com.yuri.kirikiri2.ui.screen

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.compose.foundation.clickable
import java.text.Collator
import android.text.format.DateUtils
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.Photo
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.RemoveCircleOutline
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.yuri.kirikiri2.data.GameMeta
import com.yuri.kirikiri2.data.LibraryStore
import com.yuri.kirikiri2.data.RecentGame
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

/** 首页 · 游戏库（Steam 式封面网格）：排序 = 上次游玩 / 首字母 / 游玩总时长。 */
enum class LibrarySort(val label: String) {
    LAST_PLAYED("按上次游玩时间"),
    NAME("按首字母"),
    PLAYTIME("按游玩总时长"),
}

/** 游戏库条目 = 最近启动记录 + 游玩元数据 */
data class LibraryEntry(val game: RecentGame, val meta: GameMeta, val name: String)

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun LibraryScreen(
    dataVersion: Int,
    onLaunch: (RecentGame) -> Unit,
    onRemove: (RecentGame) -> Unit,
    onOpenSettings: (RecentGame) -> Unit,
    onOpenGallery: (gamePath: String) -> Unit,
    onBrowse: () -> Unit,
) {
    val context = LocalContext.current
    var version by remember { mutableStateOf(0) }
    var sort by rememberSaveable { mutableStateOf(LibrarySort.LAST_PLAYED) }
    var sortMenuOpen by remember { mutableStateOf(false) }
    var menuGame by remember { mutableStateOf<RecentGame?>(null) }
    var coverGame by remember { mutableStateOf<RecentGame?>(null) }
    var renameGame by remember { mutableStateOf<RecentGame?>(null) }
    var renameText by remember { mutableStateOf("") }
    var pendingAlbumGame by remember { mutableStateOf<RecentGame?>(null) }

    fun reload() { version++ }

    // 相册选图（Photo Picker，无需存储权限）
    val albumPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        val game = pendingAlbumGame
        pendingAlbumGame = null
        if (uri == null || game == null) return@rememberLauncherForActivityResult
        val ok = context.contentResolver.openInputStream(uri)?.let { input ->
            LibraryStore.setCover(context, game.path, input)
        } ?: false
        if (ok) reload()
    }

    val recents = remember(dataVersion, version) { com.yuri.kirikiri2.data.RecentStore.load(context) }
    val metas = remember(dataVersion, version) { LibraryStore.load(context) }

    val entries = remember(recents, metas, sort) {
        val list = buildList {
            val seen = mutableSetOf<String>()
            recents.forEach {
                seen.add(it.path)
                add(
                    LibraryEntry(
                        it,
                        metas[LibraryStore.gameId(it.path)] ?: GameMeta(it.path),
                        LibraryStore.displayName(context, it.path)
                    )
                )
            }
            // 有游玩记录但已从最近列表移除的游戏，也保留在库里（已从库中移除的除外）
            metas.values.filter { it.path !in seen && it.lastPlayed > 0 && !it.removed }.forEach { m ->
                val f = java.io.File(m.path)
                add(
                    LibraryEntry(
                        RecentGame(m.path, f.name, f.parent ?: "", m.lastPlayed),
                        m,
                        m.name ?: LibraryStore.defaultName(m.path)
                    )
                )
            }
        }
        when (sort) {
            LibrarySort.LAST_PLAYED ->
                list.sortedByDescending { maxOf(it.meta.lastPlayed, it.game.time) }
            LibrarySort.NAME ->
                list.sortedWith(compareBy(Collator.getInstance(Locale.SIMPLIFIED_CHINESE)) { it.name })
            LibrarySort.PLAYTIME ->
                list.sortedByDescending { it.meta.playtimeMs }
        }
    }

    Column(Modifier.fillMaxSize()) {
        // Steam 式标题行：数量 + 排序方式
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "我的游戏（${entries.size}）",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.weight(1f)
            )
            Text("排序方式：", style = MaterialTheme.typography.bodySmall)
            Box {
                TextButton(onClick = { sortMenuOpen = true }) {
                    Text(sort.label, style = MaterialTheme.typography.bodyMedium)
                    androidx.compose.material3.Icon(Icons.Default.ArrowDropDown, null)
                }
                DropdownMenu(expanded = sortMenuOpen, onDismissRequest = { sortMenuOpen = false }) {
                    LibrarySort.values().forEach { s ->
                        DropdownMenuItem(
                            text = { Text(s.label) },
                            onClick = {
                                sort = s
                                sortMenuOpen = false
                            }
                        )
                    }
                }
            }
        }

        if (entries.isEmpty()) {
            EmptyLibrary(onBrowse = onBrowse)
        } else {
            LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = 128.dp),
                modifier = Modifier.fillMaxSize(),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(
                    start = 16.dp, end = 16.dp, bottom = 24.dp
                ),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(entries, key = { it.game.path }) { entry ->
                    GameCard(
                        entry = entry,
                        onClick = { onLaunch(entry.game) },
                        onLongClick = { menuGame = entry.game }
                    )
                }
            }
        }
    }

    // 长按菜单：更换封面 / 查看截图 / 独立设置 / 移除
    menuGame?.let { game ->
        ModalBottomSheet(onDismissRequest = { menuGame = null }) {
            Text(
                game.name,
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
            )
            ListItem(
                headlineContent = { Text("更换封面") },
                leadingContent = { androidx.compose.material3.Icon(Icons.Default.Photo, null) },
                modifier = Modifier.clickable {
                    menuGame = null
                    coverGame = game
                }
            )
            ListItem(
                headlineContent = { Text("重命名") },
                leadingContent = { androidx.compose.material3.Icon(Icons.Default.Edit, null) },
                modifier = Modifier.clickable {
                    menuGame = null
                    renameText = LibraryStore.displayName(context, game.path)
                    renameGame = game
                }
            )
            ListItem(
                headlineContent = { Text("打开截图库") },
                leadingContent = { androidx.compose.material3.Icon(Icons.Default.PhotoLibrary, null) },
                modifier = Modifier.clickable {
                    menuGame = null
                    onOpenGallery(game.path)
                }
            )
            ListItem(
                headlineContent = { Text("独立设置") },
                leadingContent = { androidx.compose.material3.Icon(Icons.Default.Settings, null) },
                modifier = Modifier.clickable {
                    menuGame = null
                    onOpenSettings(game)
                }
            )
            ListItem(
                headlineContent = { Text("从库中移除") },
                leadingContent = { androidx.compose.material3.Icon(Icons.Default.RemoveCircleOutline, null) },
                modifier = Modifier.clickable {
                    menuGame = null
                    onRemove(game)
                }
            )
            Spacer(Modifier.height(24.dp))
        }
    }

    // 更换封面：截图库 / 相册 两个入口
    coverGame?.let { game ->
        ModalBottomSheet(onDismissRequest = { coverGame = null }) {
            Text(
                "更换封面 · ${game.name}",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
            )
            ListItem(
                headlineContent = { Text("从游戏截图库选择") },
                leadingContent = { androidx.compose.material3.Icon(Icons.Default.PhotoLibrary, null) },
                modifier = Modifier.clickable {
                    coverGame = null
                    onOpenGallery(game.path)
                }
            )
            ListItem(
                headlineContent = { Text("从手机相册选择") },
                leadingContent = { androidx.compose.material3.Icon(Icons.Default.PhotoCamera, null) },
                modifier = Modifier.clickable {
                    pendingAlbumGame = game
                    coverGame = null
                    albumPicker.launch(
                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                    )
                }
            )
            Spacer(Modifier.height(24.dp))
        }
    }

    // 重命名
    renameGame?.let { game ->
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { renameGame = null },
            title = { Text("重命名") },
            text = {
                androidx.compose.material3.OutlinedTextField(
                    value = renameText,
                    onValueChange = { renameText = it },
                    singleLine = true,
                    label = { Text("游戏名称") }
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    LibraryStore.setName(context, game.path, renameText)
                    renameGame = null
                    reload()
                }) { Text("保存") }
            },
            dismissButton = {
                TextButton(onClick = { renameGame = null }) { Text("取消") }
            }
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun GameCard(entry: LibraryEntry, onClick: () -> Unit, onLongClick: () -> Unit) {
    Column {
        Box(
            Modifier
                .fillMaxWidth()
                .aspectRatio(0.72f)
                .clip(RoundedCornerShape(12.dp))
                .combinedClickable(onClick = onClick, onLongClick = onLongClick)
        ) {
            val cover = rememberCoverBitmap(entry.meta.coverPath)
            if (cover != null) {
                Image(
                    bitmap = cover.asImageBitmap(),
                    contentDescription = entry.name,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                // 无封面：渐变底 + 名称首字符
                Box(
                    Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                listOf(
                                    MaterialTheme.colorScheme.surfaceVariant,
                                    MaterialTheme.colorScheme.surface
                                )
                            )
                        )
                ) {
                    Text(
                        entry.name.firstOrNull()?.toString() ?: "？",
                        style = MaterialTheme.typography.displayMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.align(Alignment.Center)
                    )
                }
            }
            Surface(
                color = Color.Black.copy(alpha = 0.6f),
                shape = RoundedCornerShape(6.dp),
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(6.dp)
            ) {
                Text(
                    lastPlayedText(maxOf(entry.meta.lastPlayed, entry.game.time)),
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White,
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                )
            }
        }
        Text(
            entry.name,
            style = MaterialTheme.typography.bodyMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 6.dp)
        )
        if (entry.meta.playtimeMs > 0) {
            Text(
                "共 ${playtimeText(entry.meta.playtimeMs)}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1
            )
        }
    }
}

@Composable
private fun EmptyLibrary(onBrowse: () -> Unit) {
    Column(
        Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        androidx.compose.material3.Icon(
            Icons.Default.Folder,
            null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.height(56.dp)
        )
        Spacer(Modifier.height(12.dp))
        Text(
            "还没有游戏\n去文件里挑一个 .xp3 或游戏目录吧",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(16.dp))
        Button(onClick = onBrowse) { Text("浏览文件") }
    }
}

private fun lastPlayedText(ts: Long): String {
    if (ts <= 0) return "未游玩"
    return when {
        DateUtils.isToday(ts) -> "今天"
        isYesterday(ts) -> "昨天"
        inSameYear(ts) -> SimpleDateFormat("M月d日", Locale.getDefault()).format(ts)
        else -> SimpleDateFormat("yyyy年M月d日", Locale.getDefault()).format(ts)
    }
}

private fun isYesterday(ts: Long): Boolean {
    val cal = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, -1) }
    val then = Calendar.getInstance().apply { timeInMillis = ts }
    return cal.get(Calendar.YEAR) == then.get(Calendar.YEAR) &&
            cal.get(Calendar.DAY_OF_YEAR) == then.get(Calendar.DAY_OF_YEAR)
}

private fun inSameYear(ts: Long): Boolean {
    val now = Calendar.getInstance()
    val then = Calendar.getInstance().apply { timeInMillis = ts }
    return now.get(Calendar.YEAR) == then.get(Calendar.YEAR)
}

fun playtimeText(ms: Long): String {
    val minutes = ms / 60000
    return when {
        minutes < 1L -> "不足 1 分钟"
        minutes < 60L -> "$minutes 分钟"
        else -> {
            val h = minutes / 60
            val m = minutes % 60
            if (m == 0L) "$h 小时" else "$h 小时 $m 分"
        }
    }
}

/** 大图按 600px 上限降采样，避免整页封面把内存吃爆 */
@Composable
fun rememberCoverBitmap(path: String?): Bitmap? {
    if (path == null) return null
    return remember(path) {
        try {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(path, bounds)
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return@remember null
            var sample = 1
            while (bounds.outWidth / (sample * 2) >= 600) sample *= 2
            BitmapFactory.decodeFile(path, BitmapFactory.Options().apply { inSampleSize = sample })
        } catch (t: Throwable) {
            null
        }
    }
}
