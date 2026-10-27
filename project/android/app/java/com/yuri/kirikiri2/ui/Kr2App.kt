package com.yuri.kirikiri2.ui

import android.net.Uri
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.VideogameAsset
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.HelpOutline
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Divider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDrawerState
import androidx.compose.material3.DrawerValue
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.yuri.kirikiri2.data.LibraryStore
import com.yuri.kirikiri2.data.RecentGame
import com.yuri.kirikiri2.data.RecentStore
import com.yuri.kirikiri2.data.SettingsStore
import com.yuri.kirikiri2.ui.screen.BrowserScreen
import com.yuri.kirikiri2.ui.screen.GameSettingsScreen
import com.yuri.kirikiri2.ui.screen.GalleryScreen
import com.yuri.kirikiri2.ui.screen.HelpScreen
import com.yuri.kirikiri2.ui.screen.LibraryScreen
import com.yuri.kirikiri2.ui.screen.RepackScreen
import com.yuri.kirikiri2.ui.screen.SettingsScreen
import com.yuri.kirikiri2.ui.theme.Kr2Theme
import kotlinx.coroutines.launch
import java.io.File

/**
 * 新的原生外壳：Material 3 侧边抽屉 + 三个主界面。
 * 对应原实现里 cocos 那套 NaviBar / Body / BottomBar 三段式骨架。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun Kr2App(
    store: SettingsStore,
    startRoute: String = "launcher",
    storageGranted: Boolean = true,
    onRequestStorage: () -> Unit = {},
    onLaunch: (File) -> Unit,
    onXp3Repack: (File) -> Unit = {},
    returnToLibraryTick: Int = 0
) {
    val context = LocalContext.current
    val navController: NavHostController = rememberNavController()
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    var showStorageDialog by remember { mutableStateOf(true) }

    var recents by remember { mutableStateOf(RecentStore.load(context)) }
    var dataVersion by remember { mutableStateOf(0) }
    var namingFile by remember { mutableStateOf<File?>(null) }
    var namingText by remember { mutableStateOf("") }

    // 所有启动入口统一走这里：首次进库的游戏先弹命名框（默认用所在文件夹名）
    fun startGame(file: File) {
        val meta = LibraryStore.meta(context, file.absolutePath)
        if (meta.name == null) {
            namingFile = file
            namingText = LibraryStore.defaultName(file.absolutePath)
        } else {
            onLaunch(file)
        }
    }
    // 标题从后备栈的当前路由派生，而不是独立状态：Activity 重建时 NavHost
    // 的返回栈由 rememberSaveable 恢复，独立 title 会重置成默认值，出现
    // 「地址栏显示 A、页面内容是 B」的失配（退出游戏回到外壳时出现过）
    val backStackEntry by navController.currentBackStackEntryAsState()
    val route = backStackEntry?.destination?.route
    val title = when {
        route == null -> if (startRoute == "help") "帮助" else "游戏库"
        route.startsWith("gamesettings") ->
            "${LibraryStore.displayName(context, backStackEntry?.arguments?.getString("path") ?: "")} · 独立设置"
        route.startsWith("gallery") &&
                !backStackEntry?.arguments?.getString("pick").isNullOrEmpty() -> "截图库 · 选封面"
        else -> routeTitle(route) ?: if (startRoute == "help") "帮助" else "游戏库"
    }

    fun goto(route: String) {
        navController.navigate(route) { launchSingleTop = true }
        scope.launch { drawerState.close() }
    }

    // 从游戏退出返回外壳：清空返回栈统一跳回游戏库页（LauncherActivity 在
    // onResume 里递增 returnToLibraryTick 触发）
    LaunchedEffect(returnToLibraryTick) {
        if (returnToLibraryTick > 0) {
            navController.navigate("launcher") {
                popUpTo(navController.graph.findStartDestination().id) { inclusive = true }
                launchSingleTop = true
            }
        }
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet {
                Text(
                    "AzureKiri2",
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.padding(16.dp)
                )
                Divider()
                NavigationDrawerItem(
                    label = { Text("游戏库") },
                    selected = false,
                    icon = { Icon(Icons.Default.VideogameAsset, null) },
                    onClick = { goto("launcher") }
                )
                NavigationDrawerItem(
                    label = { Text("截图库") },
                    selected = false,
                    icon = { Icon(Icons.Default.PhotoLibrary, null) },
                    onClick = { goto("gallery") }
                )
                NavigationDrawerItem(
                    label = { Text("浏览文件") },
                    selected = false,
                    icon = { Icon(Icons.Default.Folder, null) },
                    onClick = { goto("browser") }
                )
                NavigationDrawerItem(
                    label = { Text("转换/解密XP3封包") },
                    selected = false,
                    icon = { Icon(Icons.Default.Build, null) },
                    onClick = { goto("repack") }
                )
                NavigationDrawerItem(
                    label = { Text("设置") },
                    selected = false,
                    icon = { Icon(Icons.Default.Settings, null) },
                    onClick = { goto("settings") }
                )
                NavigationDrawerItem(
                    label = { Text("帮助") },
                    selected = false,
                    icon = { Icon(Icons.Default.HelpOutline, null) },
                    onClick = { goto("help") }
                )
            }
        }
    ) {
        Scaffold(
            snackbarHost = { SnackbarHost(snackbarHostState) },
            topBar = {
                TopAppBar(
                    title = { Text(title) },
                    navigationIcon = {
                        IconButton(onClick = { scope.launch { drawerState.open() } }) {
                            Icon(Icons.Default.Menu, contentDescription = "菜单")
                        }
                    }
                )
            }
        ) { padding ->
            NavHost(
                navController = navController,
                startDestination = startRoute,
                modifier = Modifier.padding(padding)
            ) {
                composable("launcher") {
                    LibraryScreen(
                        dataVersion = dataVersion,
                        onLaunch = { game ->
                            RecentStore.add(context, File(game.path))
                            LibraryStore.restore(context, game.path)
                            recents = RecentStore.load(context)
                            dataVersion++
                            startGame(File(game.path))
                        },
                        onRemove = { game ->
                            RecentStore.remove(context, game.path)
                            LibraryStore.remove(context, game.path)
                            recents = RecentStore.load(context)
                            dataVersion++
                            scope.launch {
                                val result = snackbarHostState.showSnackbar(
                                    message = "已移除 ${game.name}",
                                    actionLabel = "撤销"
                                )
                                if (result == SnackbarResult.ActionPerformed) {
                                    RecentStore.add(context, File(game.path))
                                    LibraryStore.restore(context, game.path)
                                    recents = RecentStore.load(context)
                                    dataVersion++
                                }
                            }
                        },
                        onOpenSettings = { game: RecentGame ->
                            navController.navigate("gamesettings?path=${Uri.encode(game.path)}") {
                                launchSingleTop = true
                            }
                        },
                        onOpenGallery = { gamePath ->
                            navController.navigate("gallery?pick=${Uri.encode(gamePath)}") {
                                launchSingleTop = true
                            }
                        },
                        onBrowse = { goto("browser") }
                    )
                }
                composable(
                    route = "gallery?pick={pick}",
                    arguments = listOf(navArgument("pick") { type = NavType.StringType; defaultValue = "" })
                ) { entry ->
                    val pick = entry.arguments?.getString("pick").takeUnless { it.isNullOrEmpty() }
                    GalleryScreen(
                        pickGamePath = pick,
                        onPicked = { file ->
                            pick?.let { path ->
                                file.inputStream().use { input ->
                                    LibraryStore.setCover(context, path, input)
                                }
                            }
                            dataVersion++
                            navController.popBackStack()
                        }
                    )
                }
                composable("browser") {
                    BrowserScreen(onLaunch = { file ->
                        RecentStore.add(context, file)
                        LibraryStore.restore(context, file.absolutePath)
                        recents = RecentStore.load(context)
                        dataVersion++
                        startGame(file)
                    })
                }
                composable("repack") {
                    RepackScreen(onRepack = onXp3Repack)
                }
                composable("settings") { SettingsScreen(store = store) }
                composable(
                    route = "gamesettings?path={path}",
                    arguments = listOf(navArgument("path") { type = NavType.StringType; defaultValue = "" })
                ) { entry ->
                    GameSettingsScreen(
                        // Navigation 会自动解码 query 参数，这里不能再次 decode
                        gamePath = entry.arguments?.getString("path") ?: ""
                    )
                }
                composable("help") { HelpScreen() }
            }
        }
    }

    // 首次启动命名：默认读游戏所在文件夹名，可改
    namingFile?.let { file ->
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { namingFile = null; onLaunch(file) },
            title = { Text("添加到游戏库") },
            text = {
                Column {
                    Text(
                        "为游戏起个名字（默认取所在文件夹名）：",
                        style = MaterialTheme.typography.bodySmall
                    )
                    androidx.compose.material3.OutlinedTextField(
                        value = namingText,
                        onValueChange = { namingText = it },
                        singleLine = true
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    LibraryStore.setName(context, file.absolutePath, namingText)
                    dataVersion++
                    namingFile = null
                    onLaunch(file)
                }) { Text("保存并启动") }
            },
            dismissButton = {
                TextButton(onClick = { namingFile = null; onLaunch(file) }) { Text("直接启动") }
            }
        )
    }

    // P5 分区存储适配：未授权时弹 M3 对话框引导到系统设置（每次可关闭，下次进入还会提示直到授权）
    if (!storageGranted && showStorageDialog) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { showStorageDialog = false },
            title = { Text("需要文件访问权限") },
            text = {
                Text("吉里吉里模拟器需要读取存储卡上的游戏文件（.xp3 或游戏目录）。" +
                        "请在接下来的系统页面中允许「访问所有文件」。")
            },
            confirmButton = {
                androidx.compose.material3.TextButton(
                    onClick = {
                        showStorageDialog = false
                        onRequestStorage()
                    }
                ) { Text("去授权") }
            },
            dismissButton = {
                androidx.compose.material3.TextButton(
                    onClick = { showStorageDialog = false }
                ) { Text("暂不") }
            }
        )
    }
}

/** 固定路由对应的地址栏标题；带参数的页面（gamesettings/gallery）单独处理 */
private fun routeTitle(route: String): String? = when (route) {
    "launcher" -> "游戏库"
    "gallery" -> "截图库"
    "browser" -> "浏览文件"
    "repack" -> "转换/解密XP3封包"
    "settings" -> "设置"
    "help" -> "帮助"
    else -> null
}

/** 供 Activity 复用：把主题与内容组合在一起 */
@Composable
fun Kr2Content(store: SettingsStore, darkTheme: Boolean, onLaunch: (File) -> Unit) {
    Kr2Theme(darkTheme = darkTheme, dynamicColor = store.dynamicColor) {
        Kr2App(store = store, onLaunch = onLaunch)
    }
}
