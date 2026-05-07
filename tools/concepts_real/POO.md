# POO — real concept catalog

## Compilers — Native, Interpreted, JIT
Three compilation models on the portability/performance axis: native produces architecture-specific machine code, interpreted produces byte-code that needs an interpreter, JIT compiles byte-code to native at runtime (source: poo_c1).

## Linker and library linking modes
Linker merges object files into an executable; libraries can be linked statically (code embedded), dynamically (loaded at exec time by OS), or delayed (loaded only when first function is needed) (source: poo_c1).

## OS architecture (kernel/user mode)
Layered view of how compiled programs interact with operating system services (source: poo_c1).

## C++ history and revisions
Evolution of the C++ standard from C++98 through modern revisions, motivating language additions (source: poo_c1).

## From C to C++
Differences and migration concepts moving from procedural C into class-based C++ (source: poo_c1).

## Classes — data members and methods
A class groups data members (state) and methods (behavior) under access modifiers (public/private/protected) (source: poo_c1).

## Pointers vs References
A reference is an implicit pointer that must be initialized, cannot be reassigned, cannot be NULL, supports no arithmetic, and cannot be cast to another reference type — same generated assembly though (source: poo_c2).

## Method overloading
Multiple methods with the same name distinguished by parameter list; resolution happens at compile time (source: poo_c2).

## NULL pointer
Sentinel value for "points to nothing"; checking required before dereferencing — references avoid this entirely (source: poo_c2).

## "const" specifier
Marks variables, parameters, member functions, or pointed-to data as immutable; const member functions promise not to modify the object (source: poo_c2).

## "friend" specifier
Grants a non-member function or another class access to private/protected members of a class (source: poo_c2).

## Initialization lists (brace initialization)
"{ }" syntax to initialize variables, arrays (compiler can deduce size, fills rest with default), matrices (only first dimension can be deduced), and pointer-allocated arrays via `new int[3]{1,2,3}` (source: poo_c3).

## Constructors
A type-less function with the class's name called when the class is instantiated; can be overloaded, can be private, cannot be static or constant (source: poo_c3).

## Default constructor
Constructor with no parameters; called implicitly when no other constructor matches (source: poo_c3).

## Constructor for const & reference data members
A class containing const or reference members must have a constructor that initializes those members through an initialization list (source: poo_c3).

## Delegating constructor
A constructor that calls another constructor of the same class to share initialization logic (source: poo_c3).

## Initialization lists for classes
Member-initialization syntax `Class() : member1(v1), member2(v2) {}` invoking each member's constructor in declaration order (source: poo_c3).

## Value Types
Categories of expressions/objects (lvalue, rvalue, xvalue, etc.) governing what can be moved or referenced (source: poo_c3).

## Copy & Move Constructors
Copy constructor duplicates an object; move constructor transfers ownership of resources from a temporary using rvalue references (source: poo_c3).

## Constraints (constrains)
Compile-time conditions on template arguments using concepts/SFINAE-like mechanisms (source: poo_c3).

## Destructor
Single, parameterless function `~ClassName()` called when an object's lifetime ends; typically frees memory acquired in the constructor (source: poo_c4).

## C/C++ operators
Arithmetic, logical, bitwise, comparison, assignment operators usable on built-in types (source: poo_c4).

## Operator overloading for classes
Defining operators (+, -, =, [], (), ==, <<, etc.) as member or friend functions to give them meaning for user types (source: poo_c4).

## Operations with Objects
Object copy, assignment, comparison, and lifetime semantics when classes are used as values (source: poo_c4).

## Inheritance — base and derived classes
Process that transfers properties (members and methods) from a base class to a derived class which may extend with new members (source: poo_c5).

## Simple vs Multiple Inheritance
Simple: `class X: <access> base { }`. Multiple: same syntax with comma-separated base list; default access modifier when omitted is private (source: poo_c5).

## Virtual methods
Methods that participate in dynamic dispatch via the vtable, allowing derived overrides to be called through a base pointer (source: poo_c5).

## How virtual methods are modeled by the C++ compiler
Vtable / vptr mechanism: each class with virtuals has a function-pointer table; objects carry a hidden pointer to it (source: poo_c5).

## Covariance
A virtual method in a derived class may return a more-derived pointer/reference type than the base method's return type (source: poo_c5).

## Abstract classes (Interfaces)
Classes containing at least one pure virtual function (`= 0`); cannot be instantiated, used to define interfaces (source: poo_c5).

## Memory alignment in case of inheritance
Layout of base subobject(s) and derived members in memory; vptr placement and padding rules (source: poo_c5).

## Casts
Conversion rules between class hierarchies — derived-to-base is implicit; base-to-derived requires explicit cast; overloaded cast operators bypass these rules (source: poo_c6).

## Macros
Preprocessor `#define` text-substitution rules; no type checking, no scope (source: poo_c6).

## Macros vs Inline
Inline functions provide type checking and scope while keeping the call-site expansion benefit of macros (source: poo_c6).

## Literals
User-defined literal suffixes (operator"") allowing custom literals like `42_km` (source: poo_c6).

## Templates — function and class
Generic programming: `template<class T>` lets the same code work for many types via compile-time instantiation (source: poo_c6).

## Template specialization
Providing a custom implementation of a template for a specific type or set of types (source: poo_c6).

## Compile-time assertion checking (static_assert)
`static_assert` evaluates a condition during compilation and emits an error if false (source: poo_c6).

## Standard Template Library (STL)
A set of templates: containers (class templates holding objects), iterators (pointer-like template iteration), algorithms, adaptors, allocators (source: poo_c7).

## Sequence containers
vector, array, list, forward_list, deque — store elements in linear order (source: poo_c7).

## Container adaptors — stack, queue, priority_queue
Classes that wrap a sequence container to expose only specific access patterns (source: poo_c7).

## I/O Streams
`<iostream>` cin/cout/cerr/clog, file streams, manipulators for typed input/output (source: poo_c7).

## std::string
Managed character sequence with copy/concat/find/substring/iterator support (source: poo_c7).

## Associative containers — pair / map
`pair<T1,T2>` holds two values; `map<Key,Value,Compare>` stores key/value pairs where the key is constant and accessed in sorted order (source: poo_c8).

## Ordered vs Unordered associative containers
set/multiset/map/multimap (sorted, log n) vs unordered_* (hash-based, average O(1)) (source: poo_c8).

## Smart Pointers
`unique_ptr`, `shared_ptr`, `weak_ptr` — RAII wrappers that automate delete and manage ownership semantics (source: poo_c8).

## Constant expressions (constexpr)
Code the compiler can evaluate before compilation; `const` variables are easy, `constexpr` extends this to functions (source: poo_c9).

## Range-based for loop (for-each)
`for (auto& x : container)` syntax that iterates over any range with begin/end (source: poo_c9).

## Type inference (auto / decltype)
Compiler deduces variable type from initializer (auto) or expression type (decltype) (source: poo_c9).

## Structured binding (destructuring)
`auto [a,b] = pair_or_struct;` decomposes a tuple-like object into named variables (source: poo_c9).

## Static Polymorphism (CRTP)
Curiously Recurring Template Pattern: `class Derived : public Base<Derived>` enables compile-time dispatch with no vtable cost (source: poo_c9).

## Plain Old Data (POD)
Types with C-compatible memory layout — trivially copyable, standard-layout, no virtual functions (source: poo_c9).

## Lambda expressions
`[capture](params) -> ret { body }` anonymous closures: implicit conversion to function pointer (when stateless), usable with STL algorithms (source: poo_c10).

## Lambda capture modes
By value `[=]`, by reference `[&]`, mixed, or explicit per-variable; mutable lambdas can modify captured-by-value state (source: poo_c10).

## Generic lambdas
`[](auto x){}` lambdas templated on parameter types (C++14+) (source: poo_c10).

## Initialized lambda capture
`[x = expr]` introduces a named capture initialized from an expression at capture time (source: poo_c10).

## Lab 01 — GIT basics + C/C++ recap
Set up GitHub repo with TA as collaborator, practice commit/clone/push/pull/status/add/log/config; recap fopen-based file sum, scanf/printf word sort, fill-the-blanks isPrime program (source: poo_lab01).

## Lab 02..10 — progressive C++ exercises
Lab sheets covering classes, inheritance, operator overloading, templates, STL containers, smart pointers, lambdas — with extra (_extra) variants for additional practice (source: poo_lab01..10).
