package com.yuri.kirikiri2.ui.screen

import android.os.Environment
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.yuri.kirikiri2.data.looksLikeGame
import java.io.File

/**
 * 文件浏览：对应原 C++ 的 TVPBaseFileSelectorForm。
 *
 * 现阶段直接用 Java File API 遍历；后续按路线文档做 SAF / 分区存储适配。
 */
@Composable
fun BrowserScreen(
    onLaunch: (File) -> Unit,
    modifier: Modifier = Modifier
) {
    var current by remember {
        mutableStateOf(Environment.getExternalStorageDirectory()?.absolutePath ?: "/")
    }
    var entries by remember(current) { mutableStateOf(listDir(current)) }

    Column(modifier = modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = {
                    val parent = File(current).parent
                    if (parent != null) { current = parent; entries = listDir(parent) }
                },
                enabled = File(current).parent != null
            ) {
                Icon(Icons.Default.ArrowBack, contentDescription = "上级目录")
            }
            Text(
                current,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
        }

        if (entries.isEmpty()) {
            Column(
                modifier = Modifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    "空目录，或没有读取权限",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            LazyColumn(modifier = Modifier.fillMaxSize()) {
                items(entries) { file ->
                    FileRow(file = file, onClick = {
                        if (file.isDirectory) {
                            current = file.absolutePath
                            entries = listDir(current)
                        } else {
                            onLaunch(file)
                        }
                    })
                }
            }
        }
    }
}

private fun listDir(path: String): List<File> {
    val dir = File(path)
    val list = dir.listFiles()?.toList() ?: return emptyList()
    return list.sortedWith(compareBy({ !it.isDirectory }, { it.name.lowercase() }))
}

@Composable
private fun FileRow(file: File, onClick: () -> Unit) {
    val isGame = file.isDirectory && looksLikeGame(file)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Surface(
            modifier = Modifier.size(40.dp),
            shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp),
            color = if (isGame) MaterialTheme.colorScheme.primaryContainer
            else MaterialTheme.colorScheme.surfaceVariant
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    if (isGame) Icons.Default.PlayArrow else Icons.Default.Folder,
                    contentDescription = null,
                    tint = if (isGame) MaterialTheme.colorScheme.onPrimaryContainer
                    else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        Text(
            file.name,
            modifier = Modifier.weight(1f).padding(horizontal = 16.dp),
            style = MaterialTheme.typography.bodyMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}
