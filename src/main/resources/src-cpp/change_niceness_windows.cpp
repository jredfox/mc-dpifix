#include "jni.h"
#include <windows.h>
#include <iostream>
#include <string>
#include <thread>
#include <Shlobj.h>
#include <shellscalingapi.h>

using namespace std;

#pragma comment(lib, "Shell32.lib")

int main(int argc, char* argv[]) 
{
    //Parse Args
    long pid = stol(argv[1]);
    string priority = argv[2];
    for (auto& c : priority) c = toupper(c);
    long sleepTime = 1000;
    if (argc > 3)
        sleepTime = std::stol(argv[3]);

    DWORD priorityClass = NORMAL_PRIORITY_CLASS;
    if (priority == "HIGH") {
        priorityClass = HIGH_PRIORITY_CLASS;
    }
    else if (priority == "NORMAL") {
        priorityClass = NORMAL_PRIORITY_CLASS;
    }
    else if (priority == "ABOVE_NORMAL" || priority == "ABOVENORMAL") {
        priorityClass = ABOVE_NORMAL_PRIORITY_CLASS;
    }
    else if (priority == "REALTIME") {
        priorityClass = REALTIME_PRIORITY_CLASS;
    }

    HANDLE processHandle = OpenProcess(PROCESS_SET_INFORMATION | PROCESS_QUERY_INFORMATION, FALSE, pid);
    if (processHandle == NULL) {
        cerr << "Failed to open process with PID: " << pid << " (Error code: " << GetLastError() << ")" << std::endl;
        return 1;
    }

    if (!SetPriorityClass(processHandle, priorityClass)) {
        cerr << "Failed to set process priority." << std::endl;
        CloseHandle(processHandle);
        return 1;
    }
    CloseHandle(processHandle);
    cout << "Set Process Priority: " << priority << " for PID: " << pid << std::endl;
    Sleep(sleepTime);
}