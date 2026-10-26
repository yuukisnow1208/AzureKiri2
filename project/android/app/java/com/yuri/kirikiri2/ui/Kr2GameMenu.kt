package com.yuri.kirikiri2.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.AspectRatio
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Divider
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import org.json.JSONObject

/**
 * TJS 菜单树节点（与 kr2_bridge_jni.cpp 的序列化一一对应）。
 * 渲染语义与 cocos 的 ui/InGameMenuForm.cpp 保持一致：
 * "-" 为分隔线；有子项 = 子菜单；Group/Radio = 勾选后收起整棵菜单；
 * 仅 Checked = 勾选框；普通项 = 点击后收起。
 */
data class Kr2MenuNode(
    val index: Int,
    val caption: String,
    val isSeparator: Boolean = false,
    val hasChildren: Boolean = false,
    val isGroup: Boolean = false,
    val checkable: Boolean = false,
    val checked: Boolean = false,
    val children: List<Kr2MenuNode> = emptyList(),
)

fun parseKr2Menu(json: String): Kr2MenuNode? = try {
    val o = JSONObject(json)
    fun fromJson(j: JSONObject): Kr2MenuNode {
        val grp = j.optBoolean("grp", false)
        val checked = j.optBoolean("checked", false)
        return Kr2MenuNode(
            index = j.optInt("i", -1),
            caption = j.optString("c", ""),
            isSeparator = j.optBoolean("sep", false),
            hasChildren = j.optBoolean("sub", false),
            isGroup = grp,
            checkable = grp || checked,
            checked = checked,
            children = j.optJSONArray("children")?.let { a ->
                (0 until a.length()).mapNotNull { k -> a.optJSONObject(k)?.let(::fromJson) }
            } ?: emptyList(),
        )
    }
    fromJson(o)
} catch (t: Throwable) {
    null
}

private val NoIndication = MutableInteractionSource()

/**
 * 游戏内菜单（M3）：替代 cocos 的 TVPInGameMenuForm。
 * 子菜单在面板内导航（与 cocos 的 pushUIForm 一致），遮罩点击整体关闭。
 */
@Composable
fun Kr2GameMenuDialog(
    root: Kr2MenuNode,
    onItemClick: (Kr2MenuNode) -> Unit,
    onDismiss: () -> Unit,
) {
    val stack = remember { mutableStateListOf(root) }
    val checkedOverride = remember { mutableStateMapOf<Int, Boolean>() }
    val current = stack.last()

    fun handleItem(node: Kr2MenuNode) {
        when {
            node.hasChildren -> stack.add(node)
            node.isGroup -> { onItemClick(node); onDismiss() }
            else -> {
                onItemClick(node)
                if (node.checkable) {
                    checkedOverride[node.index] = !(checkedOverride[node.index] ?: node.checked)
                } else {
                    onDismiss()
                }
            }
        }
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.32f))
            .clickable(NoIndication, null) { onDismiss() }
    ) {
        Surface(
            modifier = Modifier
                .align(Alignment.Center)
                .fillMaxWidth(0.86f)
                .widthIn(max = 360.dp)
                .clickable(NoIndication, null) { },
            shape = RoundedCornerShape(28.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp,
            shadowElevation = 8.dp,
        ) {
            Column {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .height(56.dp)
                        .padding(horizontal = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(onClick = {
                        if (stack.size > 1) stack.removeAt(stack.lastIndex) else onDismiss()
                    }) {
                        Icon(
                            if (stack.size > 1) Icons.Filled.ArrowBack else Icons.Filled.Close,
                            contentDescription = if (stack.size > 1) "返回" else "关闭",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Text(
                        current.caption,
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Divider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))

                Column(
                    Modifier
                        .verticalScroll(rememberScrollState())
                        .padding(vertical = 8.dp)
                ) {
                    current.children.forEach { node ->
                        val displayChecked = checkedOverride[node.index] ?: node.checked
                        MenuRow(node, displayChecked) { handleItem(node) }
                    }
                }
            }
        }
    }
}

@Composable
private fun MenuRow(node: Kr2MenuNode, checked: Boolean, onClick: () -> Unit) {
    if (node.isSeparator) {
        Divider(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f),
        )
        return
    }
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .clickable(NoIndication, null) { onClick() }
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            node.caption,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier
                .weight(1f)
                .padding(vertical = 12.dp),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        when {
            node.hasChildren -> Icon(
                Icons.Filled.ChevronRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            node.checkable -> Checkbox(checked = checked, onCheckedChange = { onClick() })
        }
    }
}

/**
 * 窗口管理（M3）：替代 cocos 的 TVPWindowManagerOverlay 按钮。
 * 动作语义不变：画面填充切换 / 上一个窗口 / 下一个窗口 / 完成。
 */
@Composable
fun Kr2WindowMgrBar(state: Int, onAction: (Int) -> Unit) {
    Surface(
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 3.dp,
        shadowElevation = 4.dp,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            FilledTonalIconButton(onClick = { onAction(2) }) {
                Icon(Icons.Filled.AspectRatio, contentDescription = "切换画面填充")
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                FilledIconButton(
                    onClick = { onAction(0) },
                    enabled = state and 0b1 != 0,
                ) {
                    Icon(Icons.Filled.ChevronLeft, contentDescription = "上一个窗口")
                }
                FilledIconButton(
                    onClick = { onAction(1) },
                    enabled = state and 0b10 != 0,
                ) {
                    Icon(Icons.Filled.ChevronRight, contentDescription = "下一个窗口")
                }
            }
            FilledIconButton(onClick = { onAction(3) }) {
                Icon(Icons.Filled.Check, contentDescription = "完成")
            }
        }
    }
}
