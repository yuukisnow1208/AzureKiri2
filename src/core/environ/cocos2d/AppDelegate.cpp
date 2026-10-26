#include "AppDelegate.h"
#include "MainScene.h"
#include "ui/MainFileSelectorForm.h"
#include "ui/extension/UIExtension.h"
#include "cocostudio/FlatBuffersSerialize.h"
#include "ConfigManager/LocaleConfigManager.h"
#include "ConfigManager/GlobalConfigManager.h"
#include "Application.h"
#include "Platform.h"
#include "ui/MessageBox.h"
#include "ui/GlobalPreferenceForm.h"
#include "CustomFileUtils.h"

USING_NS_CC;

cocos2d::FileUtils *TVPCreateCustomFileUtils();
extern "C" void SDL_SetMainReady(void);
extern std::thread::id TVPMainThreadID;
static Size designResolutionSize(960, 640);
bool TVPCheckStartupArg();
cocos2d::FileUtils *TVPCreateCustomFileUtils();
// 由 project/android/app/cpp/kr2_bridge_jni.cpp 提供：
// 取出 Jetpack Compose 外壳请求的「转换/解密XP3封包」目录
bool KR2TakePendingRepackDir(std::string &out);
void TVPProcessXP3Repack(const std::string &dir);

void TVPAppDelegate::applicationWillEnterForeground() {
	::Application->OnActivate();
	Director::getInstance()->startAnimation();
}

void TVPAppDelegate::applicationDidEnterBackground() {
	::Application->OnDeactivate();
	Director::getInstance()->stopAnimation();
}

bool TVPAppDelegate::applicationDidFinishLaunching() {
	SDL_SetMainReady();
	TVPMainThreadID = std::this_thread::get_id();
	cocos2d::log("applicationDidFinishLaunching");
	// initialize director
	FileUtils::setDelegate(TVPCreateCustomFileUtils());
	auto director = Director::getInstance();
	auto glview = director->getOpenGLView();
	if (!glview) {
		glview = GLViewImpl::create("kirikiri2 frame");
		director->setOpenGLView(glview);
#if CC_PLATFORM_WIN32 == CC_TARGET_PLATFORM
		glview->setFrameSize(960, 640);
#endif
	}
	// Set the design resolution
	Size screenSize = glview->getFrameSize();
	if (screenSize.width < screenSize.height) {
		std::swap(screenSize.width, screenSize.height);
	}
	Size designSize = designResolutionSize;
	designSize.height = designSize.width * screenSize.height / screenSize.width;
	glview->setDesignResolutionSize(designSize.width, designSize.height, ResolutionPolicy::SHOW_ALL);

	Size frameSize = glview->getFrameSize();

	std::vector<std::string> searchPath;

	// In this demo, we select resource according to the frame's height.
	// If the resource size is different from design resolution size, you need to set contentScaleFactor.
	// We use the ratio of resource's height to the height of design resolution,
	// this can make sure that the resource's height could fit for the height of design resolution.
	searchPath.emplace_back("res");

	TVPSkinManager::getInstance()->InitSkin();

	// set searching path
	FileUtils::getInstance()->setSearchPaths(searchPath);

	// turn on display FPS
	director->setDisplayStats(false);

	// set FPS. the default value is 1.0/60 if you don't call this
	director->setAnimationInterval(1.0f / 60);

	TVPInitUIExtension();

	// initialize something
	LocaleConfigManager::GetInstance()->Initialize(TVPGetCurrentLanguage());
	// create a scene. it's an autorelease object
	TVPMainScene *scene = TVPMainScene::CreateInstance();

	// run
	director->runWithScene(scene);

	//director->getConsole()->listenOnTCP(16006);

	scene->scheduleOnce([](float dt){
		TVPMainScene::GetInstance()->unschedule("launch");
		TVPGlobalPreferenceForm::Initialize();
		if (!TVPCheckStartupArg()) {
			TVPMainScene::GetInstance()->pushUIForm(TVPMainFileSelectorForm::create());
			// 外壳（Compose 新 UI）请求的 XP3 封包转换：先压入文件选择器作底，
			// 再执行转换流程（无 xp3filter 的确认框 / 封包列表 / 进度条会叠在其上），
			// 与旧版在文件选择器菜单里触发时的表现一致
			std::string repackDir;
			if (KR2TakePendingRepackDir(repackDir)) {
				TVPProcessXP3Repack(repackDir);
			}
		}
	}, 0, "launch");

	return true;
}

void TVPAppDelegate::initGLContextAttrs() {
	GLContextAttrs glContextAttrs = {
		8, 8, 8, 8, 24, 8
	};
	GLView::setGLContextAttrs(glContextAttrs);
}

void TVPAppDelegate::applicationScreenSizeChanged(int newWidth, int newHeight)
{
// 	auto director = Director::getInstance();
// 	director->getOpenGLView()->setFrameSize(newWidth, newHeight);
}

void TVPOpenPatchLibUrl() {
	cocos2d::Application::getInstance()->openURL("https://zeas2.github.io/Kirikiroid2_patch/patch");
}
