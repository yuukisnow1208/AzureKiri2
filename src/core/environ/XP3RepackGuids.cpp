/*
 * XP3RepackGuids.cpp
 *
 * 为链接进 libkrkr2yuri.so 的 p7zip 对象提供 IID_* COM GUID 定义。
 *
 * 背景：预编译 lib7za.a 中这些 GUID 原本由控制台入口 Main.cpp.o 定义，
 * 但 Main.cpp.o 与引擎 tvpsnd.cpp 的 IID_IUnknown 重复符号冲突，
 * 且会被 XP3ArchiveRepack 的模板弱符号引用拖进链接，已从档案中剔除
 * （见 src/core/CMakeLists.txt 的 7za 注释，备份 lib7za.a.bak）。
 *
 * 本文件用 INITGUID 让 DEFINE_GUID 产生「定义」而非「声明」，
 * 一次性实例化所有 7zip 接口 GUID；未用到的会被 --gc-sections 裁掉。
 * IID_IUnknown / IID_IStream / IID_ISequentialStream 由 tvpsnd.cpp 提供，
 * 因此这里不能包含 MyInitGuid.h（它也会定义 IID_IUnknown）。
 */

#define INITGUID
#include "p7zip/CPP/Common/MyGuidDef.h"

// 与 XP3ArchiveRepack.cpp 相同：ICoder.h / IArchive.h 的 Func_* typedef
// 需要 WINAPI 宏（非 Windows 平台由本文件显式补上空白展开）
#ifndef WINAPI
#define WINAPI
#endif

#include "p7zip/CPP/7zip/IStream.h"
#include "p7zip/CPP/7zip/IProgress.h"
#include "p7zip/CPP/7zip/IPassword.h"
#include "p7zip/CPP/7zip/ICoder.h"
#include "p7zip/CPP/7zip/Archive/IArchive.h"
