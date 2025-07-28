#include "pch.h"
#include "jni.h"
#include <windows.h>
#include <shellscalingapi.h>
#include <iostream>

//#ifndef PCH_H
//#include <winsdkver.h>
//#define _WIN32_WINNT 0x0603
//#include <sdkddkver.h>
//#define PCH_H

// /MT flag Project --> C/C++ --> Code Generation --> Runtime Library

#pragma comment(lib, "Shcore.lib")

extern "C" __declspec(dllexport) void fixDPI() {
	SetProcessDpiAwareness(PROCESS_PER_MONITOR_DPI_AWARE);
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
