package com.yuri.kirikiri2.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Keyboard
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Mouse
import androidx.compose.material.icons.filled.TouchApp
import androidx.compose.material.icons.filled.Window
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp

/**
 * 游戏内菜单浮层：替代 cocos 那套 TVPGameMainMenu（2014 年的切图 + FadeIn 动画）。
 *
 * 语义与原来保持一致——常驻一个小把手在屏幕右侧，点开是一个可收起的动作面板；
 * 只是换成 Material 3 的形状、配色与弹簧动效。
 *
 * 浮层本身不拦截游戏画面：折叠态只有一个把手占位，展开时才吃掉该区域的触摸。
 */
@Composable
fun GameMenuOverlay(
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    virtualMouse: Boolean,
    onAction: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    Box(modifier = modifier.fillMaxHeight(), contentAlignment = Alignment.CenterEnd) {
        AnimatedVisibility(
            visible = expanded,
            enter = slideInHorizontally(animationSpec = spring(stiffness = Spring.StiffnessMedium)) { it } + fadeIn(),
            exit = slideOutHorizontally(animationSpec = spring(stiffness = Spring.StiffnessMedium)) { it } + fadeOut()
        ) {
            Surface(
                shape = RoundedCornerShape(topStart = 28.dp, bottomStart = 28.dp),
                // M3 1.1.x 还没有 surfaceContainerHigh，用 surface + 高度色阶实现同样的层次
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 6.dp,
                shadowElevation = 6.dp
            ) {
                Column(
                    modifier = Modifier
                        .widthIn(min = 168.dp)
                        .padding(vertical = 12.dp)
                ) {
                    MenuAction(
                        icon = Icons.Default.Menu,
                        label = "游戏菜单",
                        onClick = { onAction(0); onExpandedChange(false) }
                    )
                    MenuAction(
                        icon = Icons.Default.Window,
                        label = "窗口管理",
                        onClick = { onAction(1); onExpandedChange(false) }
                    )
                    MenuAction(
                        icon = if (virtualMouse) Icons.Default.Mouse else Icons.Default.TouchApp,
                        label = if (virtualMouse) "触摸模式" else "鼠标模式",
                        onClick = { onAction(2) }
                    )
                    MenuAction(
                        icon = Icons.Default.Keyboard,
                        label = "输入法",
                        onClick = { onAction(3); onExpandedChange(false) }
                    )
                    MenuAction(
                        icon = Icons.Default.PhotoCamera,
                        label = "截图",
                        onClick = { onAction(5); onExpandedChange(false) }
                    )
                    MenuAction(
                        icon = Icons.Default.Close,
                        label = "退出游戏",
                        onClick = { onAction(4); onExpandedChange(false) }
                    )
                }
            }
        }

        if (!expanded) {
            // 折叠态：右侧半透明胶囊把手
            Box(
                modifier = Modifier
                    .size(width = 28.dp, height = 88.dp)
                    .clip(RoundedCornerShape(topStart = 14.dp, bottomStart = 14.dp))
                    .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.65f))
                    .clickable { onExpandedChange(true) },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Default.Menu,
                    contentDescription = "打开游戏菜单",
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

@Composable
private fun MenuAction(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 12.dp)
            .width(152.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Icon(
            icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(22.dp)
        )
        Text(label, style = MaterialTheme.typography.titleMedium)
    }
    Spacer(Modifier.height(4.dp))
}
