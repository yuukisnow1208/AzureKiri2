/*
 * kr2_bridge_jni.cpp
 *
 * Kotlin 侧 com.yuri.kirikiri2.bridge.Kr2Bridge ↔ 引擎的桥接实现。
 *
 * 设计原则：**不重写业务规则**，只做「读写同一份数据」。
 *   - 最近启动记录：与旧 UI 共用 <pref>/.preference/recentpath.xml（<RecentPathList><Item Path="..."/>）
 *   - 偏好设置：与旧 UI 共用 <pref>/.preference/GlobalPreference.xml（<GlobalPreference><Item key value/>）
 *   - 启动游戏：复用 TVPMainScene::startupFrom()，与文件选择器点击启动完全一致
 *
 * 之所以放在 project/android/app/cpp/ 而不是 src/core/environ/android/：
 * src/core/CMakeLists.txt 用 file(GLOB) 收集 environ/android 下的所有 cpp，
 * 放在那里会被同时编进 krkr2core 静态库与最终 so，产生重复符号。
 * 放在这里由根 CMakeLists 显式列入主 so，JNI 符号必定导出。
 */

#include <jni.h>
#include <android/log.h>

#include <string>
#include <vector>
#include <mutex>
#include <functional>
#include <algorithm>
#include <atomic>
#include <cstdio>

#include "platform/android/jni/JniHelper.h"
#include "cocos2d.h"
#include "cocos2d/MainScene.h"
#include "ConfigManager/GlobalConfigManager.h"
#include "Application.h"
#include "WindowIntf.h"
#include "Platform.h"
#include "ui/InGameMenuForm.h"
#include "ui/XP3RepackForm.h"
#include "MenuItemImpl.h"
#include "tjsGlobalStringMap.h"
#include "tinyxml2/tinyxml2.h"

// 注意：必须在引擎头之后包含。Platform.h 里有名为 st_atime/st_mtime 的结构体成员，
// 与 <sys/stat.h> 的同名宏冲突，先包含 sys/stat.h 会让 Platform.h 解析失败。
#include <sys/stat.h>
#include <sys/types.h>

// 这两个函数在引擎里只于 GameMainMenu.cpp 内部声明，这里补上外部声明
iTJSDispatch2* TVPGetMenuDispatch(tTVInteger hWnd);
tTJSNI_Window *TVPGetActiveWindow();

#define KR2_LOG(...) __android_log_print(ANDROID_LOG_INFO, "KR2Bridge", __VA_ARGS__)
#define KR2_WARN(...) __android_log_print(ANDROID_LOG_WARN, "KR2Bridge", __VA_ARGS__)

// 由 src/core/environ/android/AndroidUtils.cpp 提供：把回调投递到 cocos 线程
extern void Android_PushEvents(const std::function<void()> &func);

// ---------------------------------------------------------------------------
// 全局状态
// ---------------------------------------------------------------------------
static std::mutex gMutex;
static std::string gDataDir;     // 应用 filesDir，由 Kotlin 侧 nativeSetDataDir 传入
static std::string gPendingPath; // 待启动路径：MainActivity 起来后由引擎消费
static std::string gPendingRepackDir; // 待执行的 XP3 封包转换目录：引擎起来后由 AppDelegate 消费

static std::string lockFreePrefDir();

// ---------------------------------------------------------------------------
// 路径换算
// ---------------------------------------------------------------------------
static std::string prefDir() {
	std::lock_guard<std::mutex> lk(gMutex);
	return lockFreePrefDir();
}

/** 与 TVPGetInternalPreferencePath() 等价：<filesDir>/.preference/ */
static std::string lockFreePrefDir() {
	if (!gDataDir.empty()) {
		return gDataDir + "/.preference/";
	}
	// 引擎已初始化时的兜底（依赖 cocos FileUtils，未初始化会返回空）
	extern const std::string &TVPGetInternalPreferencePath();
	if (TVPMainScene::GetInstance()) {
		return TVPGetInternalPreferencePath();
	}
	return std::string();
}

static bool ensureDir(const std::string &dir) {
	if (dir.empty()) return false;
	struct stat st;
	if (::stat(dir.c_str(), &st) == 0) return true;
	std::string path = dir;
	// 去掉末尾的 '/'
	while (path.size() > 1 && path.back() == '/') path.pop_back();
	size_t pos = path.find('/');
	while (pos != std::string::npos) {
		std::string sub = path.substr(0, pos);
		if (!sub.empty()) ::mkdir(sub.c_str(), 0755);
		pos = path.find('/', pos + 1);
	}
	return ::mkdir(path.c_str(), 0755) == 0 || ::stat(path.c_str(), &st) == 0;
}

static std::string recentXmlPath() {
	std::string d = prefDir();
	if (d.empty()) return std::string();
	return d + "recentpath.xml";
}

static std::string globalPrefXmlPath() {
	std::string d = prefDir();
	if (d.empty()) return std::string();
	return d + "GlobalPreference.xml";
}

// ---------------------------------------------------------------------------
// 最近启动记录（与旧 UI 的 recentpath.xml 完全同构）
// ---------------------------------------------------------------------------
static std::vector<std::string> loadRecentPaths() {
	std::vector<std::string> ret;
	std::string path = recentXmlPath();
	if (path.empty()) return ret;
	tinyxml2::XMLDocument doc;
	if (doc.LoadFile(path.c_str()) != tinyxml2::XML_SUCCESS) return ret;
	tinyxml2::XMLElement *root = doc.RootElement();
	if (!root) return ret;
	for (tinyxml2::XMLElement *item = root->FirstChildElement("Item"); item;
		 item = item->NextSiblingElement("Item")) {
		const char *p = item->Attribute("Path");
		if (p && *p) ret.emplace_back(p);
	}
	return ret;
}

static bool saveRecentPaths(const std::vector<std::string> &paths) {
	std::string path = recentXmlPath();
	if (path.empty()) return false;
	ensureDir(prefDir());

	tinyxml2::XMLDocument doc;
	doc.LinkEndChild(doc.NewDeclaration());
	tinyxml2::XMLElement *root = doc.NewElement("RecentPathList");
	for (const std::string &p : paths) {
		tinyxml2::XMLElement *item = doc.NewElement("Item");
		item->SetAttribute("Path", p.c_str());
		root->LinkEndChild(item);
	}
	doc.LinkEndChild(root);
	return doc.SaveFile(path.c_str()) == tinyxml2::XML_SUCCESS;
}

static const size_t kMaxRecent = 50;

/** 供引擎侧调用：把一条记录提到最前（去重 + 截断） */
bool KR2AddRecentPath(const std::string &path) {
	if (path.empty()) return false;
	std::vector<std::string> list = loadRecentPaths();
	if (!list.empty() && list.front() == path) return true;
	list.erase(std::remove(list.begin(), list.end(), path), list.end());
	list.insert(list.begin(), path);
	if (list.size() > kMaxRecent) list.resize(kMaxRecent);
	return saveRecentPaths(list);
}

/** 供引擎侧/Kotlin 调用：删除一条记录 */
bool KR2RemoveRecentPath(const std::string &path) {
	std::vector<std::string> list = loadRecentPaths();
	auto oldSize = list.size();
	list.erase(std::remove(list.begin(), list.end(), path), list.end());
	if (list.size() == oldSize) return true;
	return saveRecentPaths(list);
}

/**
 * 供引擎侧调用（AndroidUtils.cpp::TVPCheckStartupArg）：
 * 取出并清空「外壳请求启动」的路径。返回 true 表示有待启动项。
 */
bool KR2TakePendingStartupPath(std::string &out) {
	std::lock_guard<std::mutex> lk(gMutex);
	if (gPendingPath.empty()) return false;
	out = gPendingPath;
	gPendingPath.clear();
	return true;
}

/**
 * 供引擎侧调用（AppDelegate.cpp 启动流程）：
 * 取出并清空「外壳请求转换」的 XP3 封包目录。返回 true 表示有请求。
 */
bool KR2TakePendingRepackDir(std::string &out) {
	std::lock_guard<std::mutex> lk(gMutex);
	if (gPendingRepackDir.empty()) return false;
	out = gPendingRepackDir;
	gPendingRepackDir.clear();
	return true;
}

// ---------------------------------------------------------------------------
// 偏好设置：直接读写引擎的 GlobalPreference.xml（同文件同 schema）
// ---------------------------------------------------------------------------
static bool prefGet(const std::string &key, std::string &out) {
	std::string path = globalPrefXmlPath();
	if (path.empty()) return false;
	tinyxml2::XMLDocument doc;
	if (doc.LoadFile(path.c_str()) != tinyxml2::XML_SUCCESS) return false;
	tinyxml2::XMLElement *root = doc.RootElement();
	if (!root) return false;
	for (tinyxml2::XMLElement *item = root->FirstChildElement("Item"); item;
		 item = item->NextSiblingElement("Item")) {
		const char *k = item->Attribute("key");
		if (k && key == k) {
			const char *v = item->Attribute("value");
			out = v ? v : "";
			return true;
		}
	}
	return false;
}

static bool prefSet(const std::string &key, const std::string &value) {
	std::string path = globalPrefXmlPath();
	if (path.empty()) return false;
	ensureDir(prefDir());

	// 先读回现有内容（引擎可能在别处改过），再合并写回，避免覆盖
	std::vector<std::pair<std::string, std::string> > items;
	tinyxml2::XMLDocument doc;
	if (doc.LoadFile(path.c_str()) == tinyxml2::XML_SUCCESS) {
		tinyxml2::XMLElement *root = doc.RootElement();
		if (root) {
			for (tinyxml2::XMLElement *it = root->FirstChildElement("Item"); it;
				 it = it->NextSiblingElement("Item")) {
				const char *k = it->Attribute("key");
				const char *v = it->Attribute("value");
				if (k) items.emplace_back(k, v ? v : "");
			}
		}
	}

	bool replaced = false;
	for (auto &it : items) {
		if (it.first == key) { it.second = value; replaced = true; }
	}
	if (!replaced) items.emplace_back(key, value);

	tinyxml2::XMLDocument out;
	out.LinkEndChild(out.NewDeclaration());
	tinyxml2::XMLElement *root = out.NewElement("GlobalPreference");
	for (auto &it : items) {
		tinyxml2::XMLElement *e = out.NewElement("Item");
		e->SetAttribute("key", it.first.c_str());
		e->SetAttribute("value", it.second.c_str());
		root->LinkEndChild(e);
	}
	out.LinkEndChild(root);
	return out.SaveFile(path.c_str()) == tinyxml2::XML_SUCCESS;
}

// ---------------------------------------------------------------------------
// 游戏内菜单（P4）：由 Compose 浮层替代 cocos 的 TVPGameMainMenu
// ---------------------------------------------------------------------------

/** 由 MainScene.cpp 读取：true 时不再创建 cocos 那套游戏内菜单 */
static std::atomic<bool> gNativeGameMenu{false};

bool KR2UseNativeGameMenu() {
	return gNativeGameMenu.load();
}

/** 与 src/core/environ/ui/GameMainMenu.cpp 中按钮行为一一对应 */
bool KR2GameMenuAction(int action) {
	TVPMainScene *scene = TVPMainScene::GetInstance();
	if (!scene) return false;

	switch (action) {
	case 0: { // 游戏菜单（TJS 脚本提供的菜单树）→ 仍用现有 InGameMenuForm
		iTJSDispatch2 *menuobj = TVPGetMenuDispatch((tjs_intptr_t)TVPGetActiveWindow());
		if (!menuobj) return false;
		tTJSNI_MenuItem *menu = nullptr;
		menuobj->NativeInstanceSupport(TJS_NIS_GETINSTANCE,
			tTJSNC_MenuItem::ClassID, (iTJSNativeInstance**)&menu);
		if (!menu || menu->GetChildren().empty()) return false;
		Android_PushEvents([menu]() {
			TVPMainScene::GetInstance()->pushUIForm(TVPInGameMenuForm::create("GameMenu", menu));
		});
		return true;
	}
	case 1: // 窗口管理
		Android_PushEvents([]() {
			TVPMainScene::GetInstance()->showWindowManagerOverlay(true);
		});
		return true;
	case 2: // 切换虚拟鼠标 / 触摸
		Android_PushEvents([]() {
			TVPMainScene::GetInstance()->toggleVirtualMouseCursor();
		});
		return !scene->isVirtualMouseMode(); // 切换后的状态
	case 3: { // 输入法
		cocos2d::Size sz = cocos2d::Director::getInstance()->getOpenGLView()->getFrameSize();
		int w = (int)sz.width, h = (int)sz.height;
		Android_PushEvents([w, h]() { TVPShowIME(0, 0, w, h); });
		return true;
	}
	case 4: // 退出当前游戏
		Application->PostUserMessage([]() {
			tTJSNI_Window *win = TVPGetActiveWindow();
			if (win) win->Close();
		});
		return true;
	default:
		return false;
	}
}

// ---------------------------------------------------------------------------
// JSON 小工具
// ---------------------------------------------------------------------------
static void appendJsonString(std::string &out, const std::string &s) {
	out += '"';
	for (unsigned char c : s) {
		switch (c) {
		case '"':  out += "\\\""; break;
		case '\\': out += "\\\\"; break;
		case '\n': out += "\\n";  break;
		case '\r': out += "\\r";  break;
		case '\t': out += "\\t";  break;
		default:
			if (c < 0x20) {
				char buf[8];
				snprintf(buf, sizeof(buf), "\\u%04x", c);
				out += buf;
			} else {
				out += (char)c;
			}
		}
	}
	out += '"';
}

// ---------------------------------------------------------------------------
// 游戏内菜单树（M3）：把 TJS 菜单树序列化成 JSON 交给 Compose 渲染。
// 语义与 ui/InGameMenuForm.cpp 一致：跳过空/"+" 项，"-" 为分隔线，
// 有子项 = 子菜单，Group/Radio = 单选组，Checked = 勾选框。
// ---------------------------------------------------------------------------
static std::vector<tTJSNI_MenuItem*> gMenuNodes; // 前序遍历，与 JSON 中的 "i" 一一对应

static void SerializeMenuChildren(tTJSNI_MenuItem *item, std::string &out) {
	ttstr seperator = TJS::TJSMapGlobalStringMap(TJS_W("-"));
	const auto &children = item->GetChildren();
	bool first = true;
	for (size_t i = 0; i < children.size(); ++i) {
		tTJSNI_MenuItem *sub = static_cast<tTJSNI_MenuItem*>(children.at(i));
		ttstr cap; sub->GetCaption(cap);
		if (cap.IsEmpty() || cap == TJS_W("+")) continue;
		if (!first) out += ',';
		first = false;

		bool isSep = (cap == seperator);
		bool hasSub = !isSep && !sub->GetChildren().empty();
		bool grp = (sub->GetGroup() > 0 || sub->GetRadio());

		out += "{\"i\":";
		out += std::to_string(gMenuNodes.size());
		gMenuNodes.push_back(sub);
		out += ",\"c\":";
		appendJsonString(out, cap.AsStdString());
		out += ",\"sep\":";
		out += isSep ? "true" : "false";
		out += ",\"sub\":";
		out += hasSub ? "true" : "false";
		out += ",\"grp\":";
		out += grp ? "true" : "false";
		out += ",\"checked\":";
		out += sub->GetChecked() ? "true" : "false";
		if (hasSub) {
			out += ",\"children\":[";
			SerializeMenuChildren(sub, out);
			out += ']';
		}
		out += '}';
	}
}

static std::string fileNameOf(const std::string &path) {
	size_t pos = path.find_last_of('/');
	if (pos == std::string::npos) return path;
	if (pos + 1 >= path.size()) return std::string();
	return path.substr(pos + 1);
}

static std::string parentOf(const std::string &path) {
	size_t pos = path.find_last_of('/');
	if (pos == std::string::npos || pos == 0) return std::string("/");
	return path.substr(0, pos);
}

// ---------------------------------------------------------------------------
// JNI 实现
// 注意：Kotlin object 里的 external fun 生成的是 **实例方法**（见 javap 输出），
// 因此第二个参数是 jobject 而非 jclass。
// ---------------------------------------------------------------------------
static std::string jstrToStd(JNIEnv *env, jstring s) {
	if (!s) return std::string();
	const char *p = env->GetStringUTFChars(s, nullptr);
	if (!p) return std::string();
	std::string ret(p);
	env->ReleaseStringUTFChars(s, p);
	return ret;
}

static jstring stdToJstr(JNIEnv *env, const std::string &s) {
	return env->NewStringUTF(s.c_str());
}

extern "C" {

// ---------------------------------------------------------------------------
// 游戏内菜单树（M3）：TJS 菜单树 → JSON 交给 Compose 渲染（点击按索引回调 OnClick）
// ---------------------------------------------------------------------------
JNIEXPORT jstring JNICALL
Java_com_yuri_kirikiri2_bridge_Kr2Bridge_nativeGameMenuJson(JNIEnv *env, jobject thiz) {
	gMenuNodes.clear();
	tTJSNI_Window *win = TVPGetActiveWindow();
	if (!win) return nullptr;
	iTJSDispatch2 *menuobj = TVPGetMenuDispatch((tjs_intptr_t)win);
	if (!menuobj) return nullptr;
	tTJSNI_MenuItem *menu = nullptr;
	menuobj->NativeInstanceSupport(TJS_NIS_GETINSTANCE,
		tTJSNC_MenuItem::ClassID, (iTJSNativeInstance**)&menu);
	if (!menu || menu->GetChildren().empty()) return nullptr;

	std::string out = "{\"c\":\"GameMenu\",\"children\":[";
	SerializeMenuChildren(menu, out);
	out += "]}";
	if (gMenuNodes.empty()) return nullptr;
	return env->NewStringUTF(out.c_str());
}

JNIEXPORT jboolean JNICALL
Java_com_yuri_kirikiri2_bridge_Kr2Bridge_nativeGameMenuItemClick(JNIEnv *env, jobject thiz, jint index) {
	if (index < 0 || index >= (jint)gMenuNodes.size()) return JNI_FALSE;
	tTJSNI_MenuItem *node = gMenuNodes[index];
	if (!node) return JNI_FALSE;
	Android_PushEvents([node](){ node->OnClick(); });
	return JNI_TRUE;
}

// ---------------------------------------------------------------------------
// 窗口管理（M3）：动作实现在 TVPMainScene（原 TVPWindowManagerOverlay 按钮逻辑），
// UI 由 Compose 顶栏渲染。action: 0=上一窗口 1=下一窗口 2=画面填充 3=完成
// ---------------------------------------------------------------------------
JNIEXPORT jboolean JNICALL
Java_com_yuri_kirikiri2_bridge_Kr2Bridge_nativeWindowManagerAction(JNIEnv *env, jobject thiz, jint action) {
	TVPMainScene *scene = TVPMainScene::GetInstance();
	if (!scene) return JNI_FALSE;
	switch (action) {
	case 0: Android_PushEvents([](){ TVPMainScene::GetInstance()->windowManagerPrev(); }); return JNI_TRUE;
	case 1: Android_PushEvents([](){ TVPMainScene::GetInstance()->windowManagerNext(); }); return JNI_TRUE;
	case 2: Android_PushEvents([](){ TVPMainScene::GetInstance()->windowManagerToggleFill(); }); return JNI_TRUE;
	case 3: Android_PushEvents([](){ TVPMainScene::GetInstance()->showWindowManagerOverlay(false); }); return JNI_TRUE;
	default: return JNI_FALSE;
	}
}

/** bit0=有上一窗口 bit1=有下一窗口 bit2=窗口管理进行中 */
JNIEXPORT jint JNICALL
Java_com_yuri_kirikiri2_bridge_Kr2Bridge_nativeWindowManagerState(JNIEnv *env, jobject thiz) {
	TVPMainScene *scene = TVPMainScene::GetInstance();
	if (!scene) return 0;
	int state = scene->isWindowManagerActive() ? 4 : 0;
	if (scene->windowManagerHasPrev()) state |= 1;
	if (scene->windowManagerHasNext()) state |= 2;
	return state;
}

JNIEXPORT void JNICALL
Java_com_yuri_kirikiri2_bridge_Kr2Bridge_nativeSetDataDir(JNIEnv *env, jobject thiz, jstring dir) {
	std::string d = jstrToStd(env, dir);
	std::lock_guard<std::mutex> lk(gMutex);
	gDataDir = d;
	KR2_LOG("dataDir = %s", d.c_str());
}

JNIEXPORT jboolean JNICALL
Java_com_yuri_kirikiri2_bridge_Kr2Bridge_nativePing(JNIEnv *env, jobject thiz) {
	return JNI_TRUE;
}

/**
 * 记录「要启动这个路径」，并写入最近列表。
 * 返回 true 表示引擎已在运行、已就地投递启动事件；
 * 返回 false 表示引擎尚未起来，需要 Kotlin 拉起 MainActivity（待启动路径已存好）。
 */
JNIEXPORT jboolean JNICALL
Java_com_yuri_kirikiri2_bridge_Kr2Bridge_nativeLaunch(JNIEnv *env, jobject thiz, jstring path) {
	std::string p = jstrToStd(env, path);
	if (p.empty()) return JNI_FALSE;

	KR2AddRecentPath(p);

	TVPMainScene *scene = TVPMainScene::GetInstance();
	if (scene) {
		Android_PushEvents([scene, p]() {
			if (scene->startupFrom(p)) {
				KR2_LOG("hot startup ok: %s", p.c_str());
			} else {
				KR2_WARN("hot startup failed: %s", p.c_str());
			}
		});
		KR2_LOG("pending(exec) %s", p.c_str());
		return JNI_TRUE;
	}

	{
		std::lock_guard<std::mutex> lk(gMutex);
		gPendingPath = p;
	}
	KR2_LOG("pending(queued) %s", p.c_str());
	return JNI_FALSE;
}

/** 最近启动记录，JSON：[{"path","name","parent","time"}, ...]（越靠前越新） */
JNIEXPORT jstring JNICALL
Java_com_yuri_kirikiri2_bridge_Kr2Bridge_nativeRecentList(JNIEnv *env, jobject thiz) {
	std::vector<std::string> list = loadRecentPaths();
	std::string json = "[";
	for (size_t i = 0; i < list.size(); ++i) {
		if (i) json += ",";
		json += "{\"path\":";
		appendJsonString(json, list[i]);
		json += ",\"name\":";
		appendJsonString(json, fileNameOf(list[i]));
		json += ",\"parent\":";
		appendJsonString(json, parentOf(list[i]));
		json += ",\"time\":0}";
	}
	json += "]";
	return stdToJstr(env, json);
}

JNIEXPORT jboolean JNICALL
Java_com_yuri_kirikiri2_bridge_Kr2Bridge_nativeAddRecent(JNIEnv *env, jobject thiz, jstring path) {
	return KR2AddRecentPath(jstrToStd(env, path)) ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT jboolean JNICALL
Java_com_yuri_kirikiri2_bridge_Kr2Bridge_nativeRemoveRecent(JNIEnv *env, jobject thiz, jstring path) {
	return KR2RemoveRecentPath(jstrToStd(env, path)) ? JNI_TRUE : JNI_FALSE;
}

/**
 * 请求对指定目录执行「转换/解密XP3封包」（复用引擎自带的 XP3RepackForm 流程：
 * 无 xp3filter 确认框 → 封包列表 → 进度条，全部沿用 cocos 界面，不重写业务规则）。
 * 返回 true  = 引擎已在运行，转换界面已直接弹出，外壳无需再做任何事；
 * 返回 false = 引擎未起来，目录已暂存；外壳拉起 MainActivity 后
 *              AppDelegate 启动流程会自动消费并弹出转换界面。
 */
JNIEXPORT jboolean JNICALL
Java_com_yuri_kirikiri2_bridge_Kr2Bridge_nativeRequestXp3Repack(JNIEnv *env, jobject thiz, jstring dir) {
	std::string d = jstrToStd(env, dir);
	if (d.empty()) return JNI_FALSE;

	TVPMainScene *scene = TVPMainScene::GetInstance();
	if (scene) {
		Android_PushEvents([d]() {
			TVPProcessXP3Repack(d);
		});
		KR2_LOG("repack(exec) %s", d.c_str());
		return JNI_TRUE;
	}

	{
		std::lock_guard<std::mutex> lk(gMutex);
		gPendingRepackDir = d;
	}
	KR2_LOG("repack(queued) %s", d.c_str());
	return JNI_FALSE;
}

/** 打开 / 关闭 Compose 游戏内菜单（关闭则回到 cocos 那套，行为与原来一致） */
JNIEXPORT void JNICALL
Java_com_yuri_kirikiri2_bridge_Kr2Bridge_nativeSetNativeGameMenu(JNIEnv *env, jobject thiz, jboolean on) {
	gNativeGameMenu.store(on == JNI_TRUE);
	KR2_LOG("native game menu = %d", (int)(on == JNI_TRUE));
}

/**
 * 触发游戏内菜单动作：
 * 0=游戏菜单 1=窗口管理 2=切换鼠标/触摸 3=输入法 4=退出
 */
JNIEXPORT jboolean JNICALL
Java_com_yuri_kirikiri2_bridge_Kr2Bridge_nativeGameMenuAction(JNIEnv *env, jobject thiz, jint action) {
	return KR2GameMenuAction((int)action) ? JNI_TRUE : JNI_FALSE;
}

/** 当前是否处于虚拟鼠标模式 */
JNIEXPORT jboolean JNICALL
Java_com_yuri_kirikiri2_bridge_Kr2Bridge_nativeIsVirtualMouse(JNIEnv *env, jobject thiz) {
	TVPMainScene *scene = TVPMainScene::GetInstance();
	if (!scene) return JNI_FALSE;
	return scene->isVirtualMouseMode() ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT jstring JNICALL
Java_com_yuri_kirikiri2_bridge_Kr2Bridge_nativeGetPref(JNIEnv *env, jobject thiz, jstring key) {
	std::string k = jstrToStd(env, key);
	std::string v;
	if (!prefGet(k, v)) return nullptr; // 未设置 → Kotlin 侧用默认值
	return stdToJstr(env, v);
}

JNIEXPORT jboolean JNICALL
Java_com_yuri_kirikiri2_bridge_Kr2Bridge_nativeSetPref(JNIEnv *env, jobject thiz, jstring key, jstring value) {
	std::string k = jstrToStd(env, key);
	std::string v = jstrToStd(env, value);
	return prefSet(k, v) ? JNI_TRUE : JNI_FALSE;
}

} // extern "C"
