#include <iostream>
#include <cstdlib>
#include <sys/resource.h>
#include <errno.h>
#include <string>

using namespace std;

int main(int argc, char* argv[]) {
    // Check for correct number of arguments
    if (argc != 3) {
        bool hasHelp = argc == 2 && (strcmp(argv[1], "-h") == 0 || strcmp(argv[1], "-H") == 0 || strcmp(argv[1], "--help") == 0);
        std::cerr << "Usage: " << argv[0] << " <niceness> <PID>" << std::endl;
        return hasHelp ? 0 : 1;
    }

    // Parse niceness and PID from arguments
    int niceness = std::strtol(argv[1], NULL, 10);
    pid_t pid = std::strtol(argv[2], NULL, 10);

    // Change the niceness of the specified process
    int result = setpriority(PRIO_PROCESS, pid, niceness);

    // Check for errors
    if (result == -1) {
        std::cerr << "Error changing niceness: " << strerror(errno) << std::endl;
        return 1;
    }

    std::cout << "Successfully changed niceness of process " << pid << " to " << niceness << std::endl;
    return 0;
}
