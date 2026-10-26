package com.yuri.kirikiri2.ui.screen

import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Photo
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.yuri.kirikiri2.data.LibraryStore
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Round2 二分：chips + 网格结构（无位图、无 Dialog） */
@Composable
fun GalleryScreen(
    pickGamePath: String?,
    onPicked: (File) -> Unit,
) {
    val context = LocalContext.current
    var version by remember { mutableStateOf(0) }
    var filter by remember { mutableStateOf(pickGamePath ?: "") }
    var detail by remember { mutableStateOf<Pair<File, String>?>(null) }
    var confirmDelete by remember { mutableStateOf<File?>(null) }

    val games = remember(version) { LibraryStore.gamesWithScreenshots(context) }
    val shots = remember(version, filter) {
        val list = if (filter.isEmpty()) {
            games.flatMap { g -> LibraryStore.screenshots(context, g.path).map { it to g.path } }
        } else {
            LibraryStore.screenshots(context, filter).map { it to filter }
        }
        list.sortedByDescending { it.first.lastModified() }
    }
    val gameName: (String) -> String = { path -> LibraryStore.displayName(context, path) }

    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 12.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (pickGamePath == null) {
                AssistChip(onClick = { filter = "" }, label = { Text("全部游戏") })
            }
            games.forEach { g ->
                AssistChip(onClick = { filter = g.path }, label = { Text(gameName(g.path)) })
            }
        }

        if (shots.isEmpty()) {
            Column(
                Modifier
                    .fillMaxSize()
                    .padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                androidx.compose.material3.Icon(
                    androidx.compose.material.icons.Icons.Default.Photo,
                    null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(8.dp)
                )
                Text(
                    if (pickGamePath == null) "还没有截图\n游戏内右侧菜单 → 截图，或直接用系统截屏（如三指下滑）"
                    else "这个游戏还没有截图\n先在游戏里截几张，再回来选封面",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
        LazyVerticalGrid(
            columns = GridCells.Fixed(3),
            modifier = Modifier.fillMaxSize(),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(12.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            items(shots, key = { it.first.absolutePath }) { (file, gamePath) ->
                Box(
                    Modifier
                        .aspectRatio(16f / 10f)
                        .clip(RoundedCornerShape(8.dp))
                        .clickable {
                            if (pickGamePath != null) onPicked(file) else detail = file to gamePath
                        }
                ) {
                    val bmp = rememberCoverBitmap(file.absolutePath)
                    if (bmp != null) {
                        Image(
                            bitmap = bmp.asImageBitmapSafe(),
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                    } else {
                        Surface(
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            modifier = Modifier.fillMaxSize()
                        ) {}
                    }
                    if (pickGamePath == null) {
                        Surface(
                            color = MaterialTheme.colorScheme.scrim.copy(alpha = 0.55f),
                            shape = RoundedCornerShape(4.dp),
                            modifier = Modifier
                                .align(Alignment.BottomStart)
                                .padding(4.dp)
                        ) {
                            Text(
                                gameName(gamePath),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onPrimary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                            )
                        }
                    }
                }
            }
        }
        }

        // 大图查看 / 设为封面 / 删除
        detail?.let { (file, gamePath) ->
            Dialog(onDismissRequest = { detail = null }) {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surface,
                    tonalElevation = 6.dp,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(Modifier.padding(12.dp)) {
                        val bmp = rememberCoverBitmap(file.absolutePath)
                        if (bmp != null) {
                            Image(
                                bitmap = bmp.asImageBitmapSafe(),
                                contentDescription = null,
                                contentScale = ContentScale.FillWidth,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                            )
                        }
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "${gameName(gamePath)} · " +
                                    SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
                                        .format(Date(file.lastModified())),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End
                        ) {
                            TextButton(onClick = {
                                file.inputStream().use { input ->
                                    LibraryStore.setCover(context, gamePath, input)
                                }
                                detail = null
                                version++
                            }) { Text("设为封面") }
                            TextButton(onClick = { confirmDelete = file }) { Text("删除") }
                            TextButton(onClick = { detail = null }) { Text("关闭") }
                        }
                    }
                }
            }
        }

        confirmDelete?.let { file ->
            androidx.compose.material3.AlertDialog(
                onDismissRequest = { confirmDelete = null },
                title = { Text("删除截图？") },
                text = { Text("该截图将从游戏截图库中移除，且无法撤销。") },
                confirmButton = {
                    Button(onClick = {
                        file.delete()
                        confirmDelete = null
                        detail = null
                        version++
                    }) { Text("删除") }
                },
                dismissButton = {
                    OutlinedButton(onClick = { confirmDelete = null }) { Text("取消") }
                }
            )
        }
    }
}

/** asImageBitmap 的空安全包装 */
private fun android.graphics.Bitmap.asImageBitmapSafe() = asImageBitmap()
