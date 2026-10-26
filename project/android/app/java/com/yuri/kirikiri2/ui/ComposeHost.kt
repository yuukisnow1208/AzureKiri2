package com.yuri.kirikiri2.ui

import android.app.Activity
import android.content.Context
import android.os.Bundle
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.ComposeView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.findViewTreeLifecycleOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner

// 注意：lifecycle 2.6+ 中 ViewTreeXxx 是文件门面（顶层扩展函数），不能 import 门面类本身。
// JVM 名为 set/get 的扩展函数在 Kotlin 侧的真实名字是
// setViewTreeLifecycleOwner / setViewTreeViewModelStoreOwner / setViewTreeSavedStateRegistryOwner
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner

/**
 * 给「非 ComponentActivity」的宿主（这里是 Cocos2dxActivity → KR2Activity → MainActivity）
 * 手动补上 Compose 需要的三个 ViewTree owner。
 *
 * 引擎的 Activity 必须继承 cocos 的基类，没法改成 ComponentActivity，
 * 所以这里手写一个最小 LifecycleOwner + SavedStateRegistryOwner + ViewModelStoreOwner，
 * 由 Activity 的生命周期回调驱动。
 */
class ComposeLifecycleOwner :
    LifecycleOwner, SavedStateRegistryOwner, ViewModelStoreOwner {

    private val lifecycleRegistry = LifecycleRegistry(this)
    private val savedStateController = SavedStateRegistryController.create(this)
    private val store = ViewModelStore()

    override val lifecycle: Lifecycle get() = lifecycleRegistry
    override val savedStateRegistry: SavedStateRegistry get() = savedStateController.savedStateRegistry
    override val viewModelStore: ViewModelStore get() = store

    fun onCreate(savedState: Bundle?) {
        savedStateController.performRestore(savedState)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_CREATE)
    }

    fun handle(event: Lifecycle.Event) {
        lifecycleRegistry.handleLifecycleEvent(event)
    }
}

/**
 * 创建一个 ComposeView 并挂好 Compose 运行所需的 owner。
 * 调用方负责把它 add 到视图层级里。
 */
fun createComposeView(
    context: Context,
    owner: ComposeLifecycleOwner,
    content: @Composable () -> Unit
): ComposeView {
    val view = ComposeView(context)
    view.setViewTreeLifecycleOwner(owner)
    view.setViewTreeSavedStateRegistryOwner(owner)
    view.setViewTreeViewModelStoreOwner(owner)
    view.setContent(content)
    return view
}

/**
 * 把 Compose 所需的三个 owner 安装到「窗口级」decorView 上。
 *
 * ComposeView attach 时创建窗口重组器（windowRecomposer）读取的是
 * decorView 上的 ViewTreeLifecycleOwner，而不是 ComposeView 自己的——
 * 宿主 KR2Activity 继承自 cocos 引擎基类（非 ComponentActivity），
 * 系统不会自动设置，缺失时直接抛
 * "IllegalStateException: ViewTreeLifecycleOwner not found from DecorView"。
 *
 * 幂等：decorView 上已有 owner（如菜单浮层先装过）时保留原 owner，
 * 后续挂载点共用同一窗口重组器。
 */
fun installComposeWindowOwners(activity: Activity, owner: ComposeLifecycleOwner) {
    val decor = activity.window.decorView
    if (decor.findViewTreeLifecycleOwner() == null) {
        decor.setViewTreeLifecycleOwner(owner)
        decor.setViewTreeViewModelStoreOwner(owner)
        decor.setViewTreeSavedStateRegistryOwner(owner)
    }
}
