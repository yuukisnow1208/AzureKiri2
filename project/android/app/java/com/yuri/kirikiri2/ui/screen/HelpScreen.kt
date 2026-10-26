package com.yuri.kirikiri2.ui.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.TouchApp
import androidx.compose.material.icons.filled.Tune

private data class HelpSection(
    val title: String,
    val icon: ImageVector,
    val points: List<String>
)

// 内容事实来源：
// - 游戏识别：MainFileSelectorForm::CheckDir（目录含 startup.tjs）/ TVPCheckArchive==1（.xp3）
// - 加密生态：作者 Kirikiroid2_patch 仓库的 xp3filter.tjs（见 kr2-next/重制路线.md 已知风险）
// - 独立设置：IndividualPreferenceForm::CreatePreferenceAt（写入游戏数据目录）
private val helpSections = listOf(
    HelpSection(
        "如何启动游戏", Icons.Default.FolderOpen, listOf(
            "点击「浏览文件」，进入游戏所在目录。",
            "文件夹内含 startup.tjs 时，点该文件夹即可启动；也可以直接点选 .xp3 数据包启动。",
            "启动成功后会自动加入「最近启动」，下次从主页直达。"
        )
    ),
    HelpSection(
        "加密游戏", Icons.Default.Extension, listOf(
            "部分商业游戏的数据包（.xp3）经过加密，直接启动会报错或黑屏。",
            "需要将对应游戏的解密脚本 xp3filter.tjs 放入游戏数据目录后重新启动。",
            "解密脚本由社区与作者维护（Kirikiroid2_patch 仓库），本程序不内置。"
        )
    ),
    HelpSection(
        "游戏内操作", Icons.Default.TouchApp, listOf(
            "游戏中使用引擎提供的浮动菜单呼出游戏菜单（存档 / 读档 / 全屏 / 退出等）。",
            "虚拟鼠标、输入法、窗口管理等可在游戏菜单中切换。",
            "游戏内菜单也可在设置中改为原版样式。"
        )
    ),
    HelpSection(
        "设置说明", Icons.Default.Tune, listOf(
            "「设置」中的全局项对所有游戏生效，与原版引擎的 GlobalPreference.xml 共用。",
            "游戏数据目录下可创建独立设置，仅对该游戏生效（原「独立设置」功能）。",
            "最近启动列表与旧版界面共用同一份数据，升级无感迁移。"
        )
    ),
    HelpSection(
        "常见问题", Icons.Default.Info, listOf(
            "游戏数据建议放在本机存储；访问外置存储时按系统提示授权。",
            "出现乱码或字体异常时，可尝试在设置中调整渲染器与字体选项。",
            "崩溃转储（dump）会写入应用私有目录，可用于反馈问题。"
        )
    ),
)

@Composable
fun HelpScreen() {
    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        item {
            Text(
                "Kirikiroid2 使用指南",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(top = 4.dp, bottom = 4.dp)
            )
        }
        items(helpSections) { section ->
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Icon(
                        section.icon,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(section.title, style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(6.dp))
                    section.points.forEach { point ->
                        Text(
                            point,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 4.dp)
                        )
                    }
                }
            }
        }
        item {
            Text(
                "基于吉里吉里2（KiriKiri）引擎的开源移植 · Material 3 重制版",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.outline,
                modifier = Modifier.padding(bottom = 24.dp)
            )
        }
    }
}
