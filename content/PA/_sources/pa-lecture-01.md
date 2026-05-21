# PA — Curs 1: Algorithmic Language

Source: Ștefan Ciobâcă, Dorel Lucanu — "Algorithmic Language", Faculty of Computer
Science, Alexandru Ioan Cuza University, Iași, Romania. PA 2019/2020.
File: PA_Y1/Curs/curs_2020-2021/Curs 1 PA.pdf

Extracted verbatim from the lecture slides.

## Outline

1. Introduction
2. Alk Language — Motivation; Alk by examples
3. Algorithm efficiency: cost functions — Value Size; Time cost of operation evaluation

## Introduction

### What is an algorithm? 1/2

It does not exists a standard definition for the notion of algorithm. Let us
consider several attempts.

Cambridge Dictionary:
"A set of mathematical instructions that must be followed in a fixed order, and that,
especially if given to a computer, will help to calculate an answer to a mathematical
problem."

Schneider and Gersting 1995 (Invitation for Computer Science):
"An algorithm is a well-ordered collection of unambiguous and effectively computable
operations that when executed produces a result and halts in a finite amount of time."

Gersting and Schneider 2012 (Invitation for Computer Science, 6nd edition):
"An algorithm is an ordered sequence of instructions that is guaranteed to solve a
specific problem."

### What is an algorithm? 2/2

Wikipedia:
"In mathematics and computer science, an algorithm is a step-by-step procedure for
calculations. Algorithms are used for calculation, data processing, and automated
reasoning. An algorithm is an effective method expressed as a finite list of well-defined
instructions for calculating a function. Starting from an initial state and initial input
(perhaps empty), the instructions describe a computation that, when executed, proceeds
through a finite number of well-defined successive states, eventually producing "output"
and terminating at a final ending state. The transition from one state to the next is not
necessarily deterministic; some algorithms, known as randomized algorithms, incorporate
random input."

### Compare it with that of mathematical function

Cambridge Dictionary:
a quantity whose value depends on another value and changes with that
value: x is a function of y.

Wikipedia:
Intuitively, a function is a process that associates each element of a set X,
to a single element of a set Y.
Formally, a function f from a set X to a set Y is defined by a set G of
ordered pairs (x, y) such that x in X, y in Y, and every element of X is the
first component of exactly one ordered pair in G.

Both of them mainly say the same thing.

### Basic ingredients for algorithm definition

All the above definitions share the following items:

- Data/information and its processing. The general formal description
  of this is that of computation model, which consists of:
  - memory/store/state/configuration — how the data is represented
  - instructions/commands consisting of
    syntax - how to write instructions
    semantics — how a configuration is changed when the instruction is
    executed, the notion of computation (execution) (from an initial state
    to a final state)
- An algorithm must solve a problem given by a pair (input,output),
  where the input is represented in the start configuration and the
  output in the final configuration.

### How to describe an algorithm?

There are various ways to describe an algorithm:

- informal: natural language
- formal
  - mathematical notation (Turing machines, lambda-calculus,
    computational recursive functions)
  - programming languages (high-level, low-level), provided that the
    language has formal syntax and semantics
- semiformal
  - pseudo-code, combines a formal notation with a informal one (but
    which can be replaced by the formal one)
  - graphical notation (control flow graph, UML activity diagrams, state
    machines)

### Is formalisation needed? 1/2

The next short story is intended to motivate why a formal definition for
algorithms is needed.

- Before the 20th century only intuitive definitions for algorithm were
  used.
- In 1900, at the Congress of the mathematicians from Paris, David
  Hilbert formulated 23 problems as "challenges of the new century".
- The 10th problem asked for "finding a process that determines
  whether an integer polynomial has an integer root".
- Hilbert didn't pronounce the term of algorithm!

### Is formalisation needed? 2/2

- Hibert's 10th problem is non-solvable/non-computable i.e., there is no
  an algorithm that having a polynomial p as input, decides wether p
  has an integer root or not.
- This fact cannot be proved having only the intuitive notion of
  algorithm.
- To prove that there is no algorithm that solve this problem, we need a
  formal definition for algorithm!

### Concept of algorithm, formally

The formal definition for an algorithm was introduced using various
computational models:

- 1933, Kurt Godel, with Jacques Herbrand: general (partial)
  (computational) recursive functions
- Alonso Church, 1936: lambda-calculus
- Alan Turing, 1936: Turing machines
- ...

All these models are computationally equivalent.
Having a formal definition for the algorithm, we may know if a problem is
computable or non-computable. In 1970 Yuri Matijasevic showed that the
Hibert's 10th problem is non-computable (using previous work done by
Martin Davis, Hilary Putnam, and Julia Robinson). The main results are
obtained by a collaborative work.

### A lambda-calculus flavour

The language:
x
lambda x.M (abstraction)
M N (application)

Booleans:
true = lambda a.lambda b.a
false = lambda a.lambda b.b

Integers:
0 = lambda f.lambda x.x (equivalent to false)
1 = lambda f.lambda x.f x
2 = lambda f.lambda x.f (f x)
...
succ = lambda n.lambda f.lambda x.f (n f x)

Operational Semantics:
(lambda x.M)N => M[N/x] (beta-reduction)

### An idea of Turing Machine 2/3

Instruction: <q,s,q',s',d>, where
q, q' in Q (= a finite set of states)
s, s' in Sigma (= finite alphabet)
d in {L, R, N} (= moving directions)

Configuration: < internal-state, tape-content >, where tape-content is of the form
< left-symbols-list, read/write-symbol, right-symbols-list >.

Semantics: 1. <q,s,q',s',d> can be applied in the current configuration if
internal-state = q, read/write-symbol = s;
2. after <q,s,q',s',d> is successfully applied, the symbol s is replaced by s', and
read/write head is moved according to d in the current configuration;
3. the configuration obtained becomes the current one.

### Turing Machine 3/3

Even if the Turing machine model is a theoretical one, it was built a
prototype of it.

### Church-Turing Thesis

Turing Thesis:
LCMs [Logical Computing Machines: Turing's expression for Turing machines]
can do anything that could be described as "rule of thumb" or "purely
mechanical". (Turing 1948)

Church Thesis:
Real-world calculation can be done using the lambda calculus, which is equivalent
to using general recursive functions. (Church 1935, 1936)

Kleene (1967) introduced the term of Church-Turing Thesis:
"So Turing's and Church's theses are equivalent. We shall usually refer to them
both as Church's thesis, or in connection with that one of its ... versions which
deals with Turing machines as the Church-Turing thesis."

### The level of formalisation

What is the most suitable language for representing the algorithms?

- Turing machines, lambda-calcululus, recursive functions: easy
  mathematical definitions, hard to use in practice
- programming languages: easy(?) to use in practice, hard to use in
  proofs
- the most simple language equivalent to Turing machines: counting
  machines
- a structured version : while programs

### Typical steps for the development of an algorithm

1. Problem definition
2. Development of a model
3. Specification of the algorithm (requires an algorithm language)
4. Designing an algorithm (requires an algorithm language)
5. Checking the correctness of the algorithm (by hand)
6. Analysis of algorithm (by hand)
7. Implementation of algorithm (requires a programming language language)
8. Program testing

## Alk Language

### The goal

The main goal is to have a language that is:

- simple to be easily understood;
- expressive enough (Turing complete);
- abstract (no implementation details, focus only algorithm thinking);
- to supply a rigorous computation model suitable to analyse algorithms
  (correctness, efficiency, ...);
- executable (the algorithm can be directly tested at different stages of
  development, without additional tasks) ;
- input and output are given as abstract data types (no implementation
  details or additional programs for reading the input or writing the
  output).

The Alk language was developed to meet these requirements.

### Typical steps for the development of an algorithm with Alk

1. Problem definition
2. Development of a model
3. Specification of the algorithm (Alk)
4. Designing an algorithm (Alk)
5. Algorithm testing (Alk)
6. Checking the correctness of the algorithm (Alk, work in progress)
7. Analysis of algorithm (Alk, work in progress)

### Algorithms on integers 1/3

The following Alk program includes an algorithm computing the factorial
and the call of this algorithm for a given integer a:

    fact(n) {
      f = 1;
      for (i = 2; i <= n; ++i)
        f *= i;
      return f;
    }
    b = fact(a);

In order to run this algorithm, you need to supply the initial state and,
after its execution you get the final state (option "-m" is needed for that).

### Algorithms on integers 3/3

The output for the C++ program is different. Why? (An int overflows; the Alk
integer does not.)

### Array values in Alk

Arrays of integers can be written by enumeration or by a range, and the
specification for values is quite rich.

### Lists in Alk

Lists are written between angle brackets and support operations such as
pushBack, size, at, and insert.

### Structures in Alk

Structures map field names to values.

### Trees 2/2

We may represents complex data structures, like trees, directly as values in
Alk.

## Algorithm efficiency: cost functions

### On data types

A data type consists of values (constants) and operations.
Each value is represented using a memory space.
For the values of each data type, the size of representation must be
mentioned.

There are (at least) three ways to define the size of values:

- uniform
- logarithmic
- linear

### Size of scalars

integers:
Int = {..., -2, -1, 0, 1, 2, ...}
uniform dimension
logarithmic dimension
linear dimension

booleans:
Bool = {false, true}

floating point numbers:
Float = rational numbers

### Size of arrays, structures, lists, ...

Arrays: the size of an array is the sum of the sizes of its elements.
bidimensional arrays are arrays of unidimensional arrays,
tridimensional arrays are arrays of bidimensional arrays,
etc.

Structures: the size of a structure is the sum of the sizes of its field values.

Lists: the size of a list is the sum of the sizes of its elements.

### Data type (cont.)

Data type = values + operations

Each operation op has a time cost time(op).
For each operation of any data type the cost time must be mentioned.

There three ways to measure the time (inherited from the value
dimension):

- uniform: uses the uniform dimension of values
- logarithmic: uses the logarithmic dimension of values
- linear: uses the linear dimension of values

### Time cost of array operations

The array operations lookup and update each have a time cost, given for the
uniform, logarithmic and linear measures.

### Time cost of an expression evaluation

The time cost of an expression evaluation is the sum of time costs of the
operations included in the expression.
Obviously, it could be uniform, logarithmic, or linear.

### Time cost of an instruction execution step

It depends on the state and on the executed instruction.

The instruction while (E) S can be replaced with the equivalent one
if (E) {S while (E) S}.

Again, it could be uniform, logarithmic, or linear.

### Time cost of a computation (execution)

A computation (an execution) is a sequence of execution steps.
The time cost of a computation is the sum of the time costs of its
execution steps.

### Size cost of a computation (execution)

The size of a state is the sum of sizes of the values stored in it.
The size of a computation is the maximum over the states of the
computation.

### Computing cost functions with Alk interpreter

Consider the algorithm computing the sum of the first n integers:

    sum = 0;
    for (i = 1; i <= n; ++i)
      sum += i;

We add instructions computing the time cost functions and run the
algorithm in the Alk interpreter to obtain the uniform, logarithmic and
linear time values for a concrete input.
