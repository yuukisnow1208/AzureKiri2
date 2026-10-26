package com.yuri.kirikiri2.ui

import android.app.Activity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.Lifecycle

/** Java 侧可用的回调接口（fun interface 使 Java lambda 可以直接实现） */
fun interface Kr2DialogCallback {
    fun onResult(buttonIndex: Int, inputText: String?)
}

/**
 * 引擎弹窗（TVPShowSimpleMessageBox / 输入框）的数据层。
 * 对应 KR2Activity.ShowMessageBox / ShowInputBox 的 M3 替代实现。
 *
 * - 引擎线程在 cond var 上阻塞等待 onMessageBoxOK，因此队列不为空时必须始终有一个
 *   可见的对话框，且每显示一次必须恰好回调一次。
 * - 所有 push/pump/finish 均在主线程执行（ShowMessageBox 经 msgHandler.post 进来，
 *   按钮点击是 Compose UI 事件）。
 */
object Kr2DialogState {
    class Request(
        val title: String,
        val text: String,
        val buttons: List<String>,
        /** 非空表示带输入框，值为初始文本 */
        val input: String?,
        val callback: Kr2DialogCallback,
    )

    internal val queue = ArrayDeque<Request>()
    internal var current by mutableStateOf<Request?>(null)
    internal var onVisibleChanged: (() -> Unit)? = null

    internal fun push(req: Request) {
        queue.addLast(req)
        if (current == null) pump()
    }

    private fun pump() {
        current = if (queue.isEmpty()) null else queue.removeFirst()
        onVisibleChanged?.invoke()
    }

    internal fun finish(index: Int, text: String?) {
        val req = current ?: return
        pump() // 先让下一个弹窗顶上（若有），再唤醒阻塞的引擎线程
        req.callback.onResult(index, text)
    }

    val isShowing: Boolean get() = current != null
}

/**
 * 把 M3 对话框浮层挂到引擎 Activity 的 decor view 上。
 * KR2Activity 是 SDL/cocos Activity（非 ComponentActivity），
 * 复用 ComposeHost 的 ComposeLifecycleOwner。
 */
object DialogLayer {
    private var attached = false
    private var view: ComposeView? = null
    private val owner = ComposeLifecycleOwner()

    /** 由 KR2Activity.ShowMessageBox / ShowInputBox 调用（主线程） */
    @JvmStatic
    fun showMessage(
        activity: Activity,
        title: String,
        text: String,
        buttons: Array<String>,
        inputText: String?,
        callback: Kr2DialogCallback,
    ) {
        Kr2DialogState.push(Kr2DialogState.Request(title, text, buttons.toList(), inputText, callback))
        ensureAttached(activity)
    }

    @Synchronized
    private fun ensureAttached(activity: Activity) {
        if (attached) return
        attached = true
        owner.onCreate(null)
        owner.handle(Lifecycle.Event.ON_START)
        owner.handle(Lifecycle.Event.ON_RESUME)
        val v = createComposeView(activity, owner) { Kr2DialogHost() }
        // Compose 重组器读的是窗口级 owner（decorView），必须先装再挂视图
        installComposeWindowOwners(activity, owner)
        // 无弹窗时完全让出触摸，否则会挡住游戏画面
        v.visibility = if (Kr2DialogState.isShowing) View.VISIBLE else View.GONE
        Kr2DialogState.onVisibleChanged = {
            val vv = view
            if (vv != null) vv.visibility = if (Kr2DialogState.isShowing) View.VISIBLE else View.GONE
        }
        (activity.window.decorView as ViewGroup).addView(
            v,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
        )
        view = v
    }
}

@Composable
fun Kr2DialogHost() {
    val req = Kr2DialogState.current ?: return
    var input by remember(req) { mutableStateOf(req.input ?: "") }
    val focusRequester = remember { FocusRequester() }

    AlertDialog(
        onDismissRequest = { /* 引擎对话框不可点外取消 */ },
        properties = DialogProperties(
            dismissOnBackPress = false,
            dismissOnClickOutside = false,
        ),
        title = {
            Text(req.title, style = MaterialTheme.typography.headlineSmall)
        },
        text = {
            Column {
                Text(
                    req.text,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (req.input != null) {
                    Spacer(Modifier.height(12.dp))
                    OutlinedTextField(
                        value = input,
                        onValueChange = { input = it },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .focusRequester(focusRequester)
                    )
                    LaunchedEffect(req) { focusRequester.requestFocus() }
                }
            }
        },
        confirmButton = {
            val respond = { index: Int ->
                Kr2DialogState.finish(index, if (req.input != null) input else null)
            }
            if (req.buttons.size <= 2) {
                Row {
                    req.buttons.forEachIndexed { i, label ->
                        TextButton(onClick = { respond(i) }) { Text(label) }
                    }
                }
            } else {
                // 三个及以上按钮竖排右对齐，避免挤压
                Column(horizontalAlignment = Alignment.End) {
                    req.buttons.forEachIndexed { i, label ->
                        TextButton(onClick = { respond(i) }) { Text(label) }
                    }
                }
            }
        }
    )
}
