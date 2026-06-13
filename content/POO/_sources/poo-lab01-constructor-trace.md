# POO Lab 01 Source Extract — Constructor/Destructor Trace

**Origin:** `C:\Users\User\Desktop\Second brain\Labs\POO\poo_lab01.html` (local file, POO laboratory spec, UAIC 2025-2026)
**Topic:** Constructor/destructor execution order trace — Sensor class

---

## Lab specification (fragment)

```
1. Recap simple C library functions
Write a program in C-Language that open the file "in.txt", and prints the sum of the numbers that are found on each line of the file "in.txt". To open the file use fopen API. Write your own function that converts a string to a number (similar cu atoi API). To print something to the screen, use the printf API.
Example: let's consider the following "ini.txt" file:
123
198698
5009
983279
The program will print to the screen 1187109.
```

## Sensor constructor trace exercise

Given the following C++ program:

```cpp
#include <cstdio>
#include <iostream>

class Sensor {
public:
    Sensor(const char* name) {
        printf("CTOR  -> %s\n", name);
    }
};

Sensor globalSensor("global");

int main() {
    printf("--- entering main ---\n");

    Sensor local("local");
    Sensor* heap = new Sensor("heap");
    Sensor arr[2] = { "arr[0]", "arr[1]" };
    Sensor* ptr;
    std::cout << sizeof(Sensor);
    delete heap;
    return 0;
}
```

Expected output when compiled with g++ and run:
```
CTOR  -> global
--- entering main ---
CTOR  -> local
CTOR  -> heap
CTOR  -> arr[0]
CTOR  -> arr[1]
1
```

## Explanation
- `globalSensor` is constructed before `main()` is entered (static initialization order).
- In `main()`: `local` is constructed, then `heap` via `new`, then `arr[0]` and `arr[1]`.
- `sizeof(Sensor)` = 1 (empty class has minimum size 1).
- No destructor prints because no user-defined destructor was provided.
