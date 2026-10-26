cmake_minimum_required(VERSION 3.6)
project(krkr2yuri)

set(CMAKE_CXX_STANDARD 11)

# 注意：native 优化级别由 project/android/app/build.gradle 的 externalNativeBuild
# arguments 强制为 -O0（CMAKE_*_FLAGS_RELWITHDEBINFO / RELEASE）。
# 原因：-O1/-O2 会触发 tjs2 的未定义行为，游戏启动保存数据
# （Dictionary.saveStruct → tTVPTextWriteStream::Write）时 SIGSEGV；
# -O0（与上游 Yuri 官方 CI 的 assembleDebug 产物一致）无此问题。
set(KRKR2CORE_PATH ${CMAKE_CURRENT_SOURCE_DIR}/src/core)
set(KRKR2PLUGIN_PATH ${CMAKE_CURRENT_SOURCE_DIR}/src/plugins)
set(COCOS2DX_PATH ${CMAKE_CURRENT_SOURCE_DIR}/thirdparty/port/cocos2d-x)

# build library
add_subdirectory(${KRKR2CORE_PATH})
add_subdirectory(${KRKR2PLUGIN_PATH})

# build main
if(CMAKE_SYSTEM_NAME MATCHES "Android")
    add_library(${PROJECT_NAME} SHARED 
        project/android/app/cpp/krkr2_android.cpp
        project/android/app/cpp/kr2_bridge_jni.cpp
    )
elseif(CMAKE_SYSTEM_NAME MATCHES "Linux")
    message("${CMAKE_SYSTEM_NAME} not support yet")
elseif(CMAKE_SYSTEM_NAME MATCHES "Windows")
    message("${CMAKE_SYSTEM_NAME} not support yet")
else()
    message("${CMAKE_SYSTEM_NAME} not support yet")
endif()
target_compile_options(${PROJECT_NAME} PUBLIC
    -fPIE
)
target_include_directories(${PROJECT_NAME} PUBLIC
    src/cocos
    ${KRKR2CORE_PATH}/environ/cocos2d
    ${PORTBUILD_PATH}/include
    ${PORTBUILD_PATH}/include/breakpad
    ${COCOS2DX_PATH}/cocos
    ${COCOS2DX_PATH}/cocos/audio/include
)
target_link_directories(${PROJECT_NAME} PUBLIC
    ${PORTBUILD_PATH}/lib
)
target_link_libraries(${PROJECT_NAME} PUBLIC
    -Wl,-Bstatic
    -Wl,--whole-archive # cpp_android_spec for jni function, this is important to add whole libraries !
    cpp_android_spec krkr2plugin
    -Wl,--no-whole-archive
    krkr2core
    # IID_IUnknown / IID_IStream 等标准 GUID 在引擎 tvpsnd.cpp 与 p7zip 头
    # （INITGUID 模式，见 src/core/environ/XP3RepackGuids.cpp）中各有一份定义，
    # 值完全相同，允许重复、以先链接者为准
    -Wl,--allow-multiple-definition
)