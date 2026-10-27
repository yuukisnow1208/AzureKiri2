#include "ncbind/ncbind.hpp"
//extern void InitPlugin_CSVParser();

void TVPLoadInternalPlugins()
{
    ncbAutoRegister::AllRegist();
	ncbAutoRegister::LoadModule(TJS_W("xp3filter.dll"));
    // gdiplus.dll（GdiPlus.Appearance / layerExDraw / layerExSave 兼容）默认激活：
    // M2 系游戏脚本无条件 `typeof GdiPlus` 访问全局成员，不激活会直接抛
    // "Member GdiPlus does not exist" 导致游戏无法启动
    ncbAutoRegister::LoadModule(TJS_W("gdiplus.dll"));
    // scriptsEx.dll（wamsoft scriptsEx 兼容：getObjectKeys/foreach/clone 等
    // Scripts 类静态方法），M2 系游戏必需
    ncbAutoRegister::LoadModule(TJS_W("scriptsEx.dll"));
    // windowEx.dll（wamsoft windowEx 兼容：Window.getNotificationNum /
    // setMessageHook / registerExEvent / ncHitTest、System.getCursorPos 等）。
    // krkrz 时代 KAG3 脚本的游戏在 krkr2 路径下会无条件调用这些 API
    // （custom.tjs: "Window.getNotificationNum("SYSKEYDOWN")"），
    // zeas2 版 1.3.9 内置了 windowEx，缺它则报
    // "Member getNotificationNum does not exist" 无法启动
    ncbAutoRegister::LoadModule(TJS_W("windowEx.dll"));
    //InitPlugin_CSVParser();
}

void TVPUnloadInternalPlugins()
{
    ncbAutoRegister::AllUnregist();
}

bool TVPLoadInternalPlugin(const ttstr &_name)
{
	return ncbAutoRegister::LoadModule(TVPExtractStorageName(_name));
}