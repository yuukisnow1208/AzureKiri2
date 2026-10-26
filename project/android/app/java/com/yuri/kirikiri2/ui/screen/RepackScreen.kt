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
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material3.Button
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
import java.io.File

/**
 * XP3 封包转换/解密：独立导航页。
 *
 * 与浏览文件平级：只浏览目录（不列文件），选中目录后点底部按钮，
 * 把目录交给引擎弹出 cocos 侧的转换确认 / 封包列表 / 进度 UI。
 */
@Composable
fun RepackScreen(
    onRepack: (File) -> Unit,
    modifier: Modifier = Modifier
) {
    var current by remember {
        mutableStateOf(Environment.getExternalStorageDirectory()?.absolutePath ?: "/")
    }
    var dirs by remember(current) { mutableStateOf(listDirs(current)) }

    Column(modifier = modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = {
                    val parent = File(current).parent
                    if (parent != null) { current = parent; dirs = listDirs(parent) }
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
                modifier = Modifier.weight(1f).padding(end = 8.dp)
            )
        }

        if (dirs.isEmpty()) {
            Column(
                modifier = Modifier.fillMaxWidth().weight(1f),
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
            LazyColumn(modifier = Modifier.fillMaxWidth().weight(1f)) {
                items(dirs) { dir ->
                    DirRow(dir = dir, onClick = {
                        current = dir.absolutePath
                        dirs = listDirs(current)
                    })
                }
            }
        }

        // 底部操作区：对当前目录执行转换
        Column(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                "将对上面目录中的 XP3 封包执行转换/解密",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Button(
                onClick = { onRepack(File(current)) },
                modifier = Modifier.padding(top = 8.dp)
            ) {
                Icon(Icons.Default.Build, contentDescription = null)
                Text(
                    "转换/解密此目录的XP3封包",
                    modifier = Modifier.padding(start = 8.dp)
                )
            }
        }
    }
}

/** 只列子目录，转换作用对象是目录而非单个文件 */
private fun listDirs(path: String): List<File> {
    val dir = File(path)
    val list = dir.listFiles()?.filter { it.isDirectory } ?: return emptyList()
    return list.sortedBy { it.name.lowercase() }
}

@Composable
private fun DirRow(dir: File, onClick: () -> Unit) {
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
            color = MaterialTheme.colorScheme.surfaceVariant
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    Icons.Default.Folder,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        Text(
            dir.name,
            modifier = Modifier.weight(1f).padding(horizontal = 16.dp),
            style = MaterialTheme.typography.bodyMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}
