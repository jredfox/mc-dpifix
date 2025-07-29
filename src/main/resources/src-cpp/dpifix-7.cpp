#include "pch.h"
#include "jni.h"
#include <windows.h>
#include <shellscalingapi.h>
#include <iostream>

#pragma comment(lib, "Shcore.lib")


extern "C" __declspec(dllexport) void fixDPI() {
    SetProcessDPIAware();//Windows-7 Friendly Version
}

extern "C" __declspec(dllexport) void setHigh() {
    SetPriorityClass(GetCurrentProcess(), HIGH_PRIORITY_CLASS);
}

extern "C" JNIEXPORT void JNICALL Java_jredfox_DpiFix_fixDPI(JNIEnv*, jobject) {
    std::cout << "Setting DPI awareness " << std::endl;
    fixDPI();
}

extern "C" JNIEXPORT void JNICALL Java_jredfox_DpiFix_setHighPriority(JNIEnv*, jobject) {
    std::cout << "Setting High Priority " << std::endl;
    setHigh();
}