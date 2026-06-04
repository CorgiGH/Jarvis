Algorithmic Language
Ştefan Ciobâcă, Dorel Lucanu
Faculty of Computer Science
Alexandru Ioan Cuza University, Iaşi, Romania
stefan.ciobaca@info.uaic.ro, dlucanu@info.uaic.ro

PA 2019/2020

Şt. Ciobâcă, D. Lucanu (FII - UAIC)

Algorithmic Language

PA 2019/2020

1 / 49

Outline

1

Introduction

2

Alk Language
Motivation
Alk by examples

3

Algorithm efficiency: cost functions
Value Size
Time cost of operation evaluation

Şt. Ciobâcă, D. Lucanu (FII - UAIC)

Algorithmic Language

PA 2019/2020

2 / 49

Introduction

Plan

1

Introduction

2

Alk Language
Motivation
Alk by examples

3

Algorithm efficiency: cost functions
Value Size
Time cost of operation evaluation

Şt. Ciobâcă, D. Lucanu (FII - UAIC)

Algorithmic Language

PA 2019/2020

3 / 49

Introduction

What is an algorithm? 1/2
It does not exists a standard definition for the notion of algorithm. Let us
consider several attempts.
Cambridge Dictionary:
”A set of mathematical instructions that must be followed in a fixed order, and that,
especially if given to a computer, will help to calculate an answer to a mathematical
problem.”

Schneider and Gersting 1995 (Invitation for Computer Science):
”An algorithm is a well-ordered collection of unambiguous and effectively computable
operations that when executed produces a result and halts in a finite amount of time.”

Gersting and Schneider 2012 (Invitation for Computer Science, 6nd
edition):
”An algorithm is an ordered sequence of instructions that is guaranteed to solve a
specific problem.”
Şt. Ciobâcă, D. Lucanu (FII - UAIC)

Algorithmic Language

PA 2019/2020

4 / 49

Introduction

What is an algorithm? 2/2

Wikipedia:
”In mathematics and computer science, an algorithm is a step-by-step procedure for
calculations. Algorithms are used for calculation, data processing, and automated
reasoning. An algorithm is an effective method expressed as a finite list of well-defined
instructions for calculating a function. Starting from an initial state and initial input
(perhaps empty), the instructions describe a computation that, when executed, proceeds
through a finite number of well-defined successive states, eventually producing ”output”
and terminating at a final ending state. The transition from one state to the next is not
necessarily deterministic; some algorithms, known as randomized algorithms, incorporate
random input.”

Şt. Ciobâcă, D. Lucanu (FII - UAIC)

Algorithmic Language

PA 2019/2020

5 / 49

Introduction

Compare it with that of mathematical function

Cambridge Dictionary:
a quantity whose value depends on another value and changes with that
value: x is a function of y .
Wikipedia:
Intuitively, a function is a process that associates each element of a set X ,
to a single element of a set Y .
Formally, a function f from a set X to a set Y is defined by a set G of
ordered pairs (x, y ) such that x ∈ X , y ∈ Y , and every element of X is the
first component of exactly one ordered pair in G .
Both of them mainly say the same thing.

Şt. Ciobâcă, D. Lucanu (FII - UAIC)

Algorithmic Language

PA 2019/2020

6 / 49

Introduction

Basic ingredients for algorithm definition
All the above definitions share the following items:
Data/information and its processing. The general formal description
of this is that of computation model, which consists of:
memory/store/state/configuration – how the data is represented
instructions/commands consisting of
syntax - how to write instructions
semantics – how a configuration is changed when the instruction is
executed, the notion of computation (execution) (from an initial state
to a final state)

An algorithm must solve a problem given by a pair (input,output),
where the input is represented in the start configuration and the
output in the final configuration.

Şt. Ciobâcă, D. Lucanu (FII - UAIC)

Algorithmic Language

PA 2019/2020

7 / 49

Introduction

How to describe an algorithm?
There are various ways to describe an algorithm:
informal: natural language
formal
mathematical notation (Turing machines, lambda-calculus,
computational recursive functions)
programming languages (high-level, low-level), provided that the
language has formal syntax and semantics

semiformal
pseudo-code, combines a formal notation with a informal one (but
which can be replaced by the formal one)
graphical notation (control flow graph, UML activity diagrams, state
machines)

Şt. Ciobâcă, D. Lucanu (FII - UAIC)

Algorithmic Language

PA 2019/2020

8 / 49

Introduction

Is formalisation needed? 1/2

The next short story is intended to motivate why a formal definition for
algorithms is needed.
Before the 20th century only intuitive definitions for algorithm were
used.
In 1900, at the Congress of the mathematicians from Paris, David
Hilbert formulated 23 problems as ”challenges of the new century”.
The 10th problem asked for ”finding a process that determines
whether an integer polynomial has an integer root”.
Hilbert didn’t pronounce the term of algorithm!

Şt. Ciobâcă, D. Lucanu (FII - UAIC)

Algorithmic Language

PA 2019/2020

9 / 49

Introduction

Is formalisation needed? 2/2

Hibert’s 10th problem is non-solvable/non-computable i.e., there is no
an algorithm that having a polynomial p as input, decides wether p
has an integer root or not.
This fact cannot be proved having only the intuitive notion of
algorithm.
To prove that there is no algorithm that solve this problem, we need a
formal definition for algorithm!

Şt. Ciobâcă, D. Lucanu (FII - UAIC)

Algorithmic Language

PA 2019/2020

10 / 49

Introduction

Concept of algorithm, formally
The formal definition for an algorithm was introduced using various
computational models:
1933, Kurt Gödel, with Jacques Herbrand: general (partial)
(computational) recursive functions
Alonso Church, 1936: λ-calculus
Alan Turing, 1936: Turing machines
...
All these models are computationally equivalent.
Having a formal definition for the algorithm, we may know if a problem is
computable or non-computable. In 1970 Yuri Matijasevic showed that the
Hibert’s 10th problem is non-computable (using previous work done by
Martin Davis, Hilary Putnam, and Julia Robinson). The main results are
obtained by a collaborative work.
Şt. Ciobâcă, D. Lucanu (FII - UAIC)

Algorithmic Language

PA 2019/2020

11 / 49

Introduction

A λ-calculus flavour
The language:
x
λx.M (abstraction)
M N (application)
Booleans:
true , λa.λb.a
false , λa.λb.b
Integers:
0 , λf .λx.x (equivalent to false)
1 , λf .λx.f x
2 , λf .λx.f (f x)
...
succ = λn.λf .λx.f (n f x)

Sursa: https://en.wikipedia.org/wiki/Alonzo Church

Operational Semantics:
(λx.M)N ⇒ M[N/x] (β-reduction)
Şt. Ciobâcă, D. Lucanu (FII - UAIC)

Algorithmic Language

PA 2019/2020

12 / 49

Introduction

An idea of Turing Machine 1/3

Source: https://www.iwm.org.uk/history/how-alan-turing-cracked-the-enigma-code
https://www.decodedscience.org/what-is-universal-turing-machine/12081
Şt. Ciobâcă, D. Lucanu (FII - UAIC)

Algorithmic Language

PA 2019/2020

13 / 49

Introduction

An idea of Turing Machine 2/3
Tape
...

...
Read/write head
Program

Instruction: hq, s, q 0 , s 0 , di, where
q, q 0 ∈ Q (= a finite set of states)
s, s 0 ∈ Σ (= finite alphabet)
d ∈ {L, R, N} (= moving directions)
Configuration: h internal-state, tape-content i, where tape-content is of the form
h left-symbols-list, read/write-symbol, right-symbols-list i.
Semantics: 1. hq, s, q 0 , s 0 , di can be applied in the current configuration if
internal-state = q, read/write-symbol = s;
2. after hq, s, q 0 , s 0 , di is successfully applied, the symbol s is replaced by s 0 , and
read/write head is moved according to d in the current configuration;
3. the configuration obtained becomes the current one.
Şt. Ciobâcă, D. Lucanu (FII - UAIC)

Algorithmic Language

PA 2019/2020

14 / 49

Introduction

Turing Machine 3/3
Even if the Turing machine model is a theoretical one, it was built a
prototype of it:

Source:By GabrielF - Own work, CC BY-SA 3.0, https://commons.wikimedia.org/w/index.php?curid=26270095

Şt. Ciobâcă, D. Lucanu (FII - UAIC)

Algorithmic Language

PA 2019/2020

15 / 49

Introduction

Church-Turing Thesis

Turing Thesis:
LCMs [Logical Computing Machines: Turing’s expression for Turing machines]
can do anything that could be described as ”rule of thumb” or ”purely
mechanical”. (Turing 1948)
Church Thesis:
Real-world calculation can be done using the lambda calculus, which is equivalent
to using general recursive functions. (Church 1935, 1936)
Kleene (1967) introduced the term of Church-Turing Thesis:
”So Turing’s and Church’s theses are equivalent. We shall usually refer to them
both as Church’s thesis, or in connection with that one of its . . . versions which
deals with Turing machines as the Church-Turing thesis.”

Şt. Ciobâcă, D. Lucanu (FII - UAIC)

Algorithmic Language

PA 2019/2020

16 / 49

Introduction

The level of formalisation

What is the most suitable language for representing the algorithms?
Turing machines, lambda-calcululus, recursive functions: easy
mathematical definitions, hard to use in practice
programming languages: easy(?) to use in practice, hard to use in
proofs
the most simple language equivalent to Turing machines: counting
machines
a structured version : while programs

Şt. Ciobâcă, D. Lucanu (FII - UAIC)

Algorithmic Language

PA 2019/2020

17 / 49

Introduction

Typical steps for the development of an algorithm

1

Problem definition

2

Development of a model

3

Specification of the algorithm (requires an algorithm language)

4

Designing an algorithm (requires an algorithm language)

5

Checking the correctness of the algorithm (by hand)

6

Analysis of algorithm (by hand)

7

Implementation of algorithm (requires a programming language
language)

8

Program testing

Şt. Ciobâcă, D. Lucanu (FII - UAIC)

Algorithmic Language

PA 2019/2020

18 / 49

Alk Language

Plan

1

Introduction

2

Alk Language
Motivation
Alk by examples

3

Algorithm efficiency: cost functions
Value Size
Time cost of operation evaluation

Şt. Ciobâcă, D. Lucanu (FII - UAIC)

Algorithmic Language

PA 2019/2020

19 / 49

Alk Language

Motivation

Plan

1

Introduction

2

Alk Language
Motivation
Alk by examples

3

Algorithm efficiency: cost functions
Value Size
Time cost of operation evaluation

Şt. Ciobâcă, D. Lucanu (FII - UAIC)

Algorithmic Language

PA 2019/2020

20 / 49

Alk Language

Motivation

The goal
The main goal is to have a language that is:
simple to be easily understood;
expressive enough (Turing complete);
abstract (no implementation details, focus only algorithm thinking);
to supply a rigorous computation model suitable to analyse algorithms
(correctness, efficiency, . . . );
executable (the algorithm can be directly tested at different stages of
development, without additional tasks) ;
input and output are given as abstract data types (no implementation
details or additional programs for reading the input or writing the
output).
The Alk language was developed to meet these requirements.
Şt. Ciobâcă, D. Lucanu (FII - UAIC)

Algorithmic Language

PA 2019/2020

21 / 49

Alk Language

Motivation

Typical steps for the development of an algorithm with Alk

1

Problem definition

2

Development of a model

3

Specification of the algorithm (Alk)

4

Designing an algorithm (Alk)

5

Algorithm testing (Alk)

6

Checking the correctness of the algorithm (Alk, work in progress)

7

Analysis of algorithm (Alk, work in progress)

Şt. Ciobâcă, D. Lucanu (FII - UAIC)

Algorithmic Language

PA 2019/2020

22 / 49

Alk Language

Alk by examples

Plan

1

Introduction

2

Alk Language
Motivation
Alk by examples

3

Algorithm efficiency: cost functions
Value Size
Time cost of operation evaluation

Şt. Ciobâcă, D. Lucanu (FII - UAIC)

Algorithmic Language

PA 2019/2020

23 / 49

Alk Language

Alk by examples

Algorithms on integers 1/3
The following Alk program includes an algorithm computing the factorial
and the call of this algorithm for a given integer a:
fact (n) {
f = 1;
f o r ( i = 2 ; i <= n ; ++i )
f ∗= i ;
return f ;
}
b = fact (a );

In order to run this algorithm, you need to supply the initial state and,
after its execution you get the final state (option ”-m” is needed for that):
$ a l k i −a f a c t . a l k − i ” a|−>21” −m
a |−> 21
b |−> 51090942171709440000
Şt. Ciobâcă, D. Lucanu (FII - UAIC)

Algorithmic Language

PA 2019/2020

24 / 49

Alk Language

Alk by examples

Algorithms on integers 2/3

Compare it to the equivalent C++ program:
#i n c l u d e <i o s t r e a m >
int fact ( int n) {
int f = 1;
f o r ( i n t i = 2 ; i <= n ; ++i )
f ∗= i ;
return f ;
}
i n t main ( ) {
int a ;
s t d : : c o u t << ” a = ” ;
s t d : : c i n >> a ;
int b = fact (a );
s t d : : c o u t << ”b = ” << b << s t d : : e n d l ;
return 0;
}

Şt. Ciobâcă, D. Lucanu (FII - UAIC)

Algorithmic Language

PA 2019/2020

25 / 49

Alk Language

Alk by examples

Algorithms on integers 3/3

The output for the C++ program is different:
$ g++ f a c t . cpp −o f a c t . e x e
$ ./ f a c t . exe
a = 21
b = −1195114496

Why?

Şt. Ciobâcă, D. Lucanu (FII - UAIC)

Algorithmic Language

PA 2019/2020

26 / 49

Alk Language

Alk by examples

Array values in Alk

// a r r a y s o f i n t e g e r s
a1 = [ 1 , 2 , 3 , 4 , 5 ] ;
// t h e a b o v e i s
a2 = [ 1 . . 5 ] ;

e q u i v a l e n t to :

// t h e s p e c i f i c a t i o n f o r v a l u e s i s q u i t e r i c h :
a3 = [ x from [ 1 . . 5 ] | x % 2 == 0 ] ;
a4 = [ 3∗ x | x from [ 1 . . 5 ] ] ;

The output:
$ a l k i −a a r r a y s . a l k −m
a1 |−> [ 1 , 2 , 3 , 4 , 5 ]
a2 |−> [ 1 , 2 , 3 , 4 , 5 ]
a3 |−> [ 2 , 4 ]
a4 |−> [ 3 , 6 , 9 , 1 2 , 1 5 ]

Şt. Ciobâcă, D. Lucanu (FII - UAIC)

Algorithmic Language

PA 2019/2020

27 / 49

Alk Language

Alk by examples

Lists in Alk

l 1 = < 1 , 2 , 3 , 4 , 5 >;
l 2 = e m p t y L i s t ; // e q u i v . t o l 2 = <>;
f o r ( i =1; i <6; ++i )
l 2 . pushBack ( i ) ;
l3 = l2 ;
f o r ( i =0; i < l 3 . s i z e ( ) ; ++i )
i f ( l 3 . a t ( i )%2 == 0 ) {
l3 . i n s e r t ( i , l3 . at ( i ) ) ;
++i ;
}

The output:
$ a l k i −a l i s t s . a l k −m
l 1 |−> < 1 , 2 , 3 , 4 , 5 >
l 2 |−> < 1 , 2 , 3 , 4 , 5 >
l 3 |−> < 1 , 2 , 2 , 3 , 4 , 4 , 5 >
i |−> 7

Şt. Ciobâcă, D. Lucanu (FII - UAIC)

Algorithmic Language

PA 2019/2020

28 / 49

Alk Language

Alk by examples

Structures in Alk

p1 = {name −> ”A”

x −> 2 . 0 y −> 7 . 2 3 } ;

p2 . name = ”A” ;
p2 . x = 2 . 0 ;
p2 . y = 7 . 2 3 ;
t r a n s l a t e ( out p , d ) {
p . x += d ;
p . y += d ;
}
p3 = p1 ;
t r a n s l a t e ( p3 , 5 ) ;

The output:
$ a l k i −a s t r u c t s . a l k −m
p1 |−> {name −> ”A” x −> 2 . 0 y −> 7 . 2 3 }
p2 |−> {name −> ”A” x −> 2 . 0 y −> 7 . 2 3 }
p3 |−> {name −> ”A” x −> 7 . 0 y −> 1 2 . 2 3 }

Şt. Ciobâcă, D. Lucanu (FII - UAIC)

Algorithmic Language

PA 2019/2020

29 / 49

Alk Language

Alk by examples

Trees 1/2

We may represents complex data structures, like trees, directly as values in
Alk.
Consider the following tree t:
A

B

C

D

Şt. Ciobâcă, D. Lucanu (FII - UAIC)

Algorithmic Language

PA 2019/2020

30 / 49

Alk Language

Alk by examples

Trees 2/2

The tree t as an Alk value:
t = [ { i n f o −> ”A”
{ i n f o −> ”B”
{ i n f o −> ”C”
{ i n f o −> ”D”
];

c h i l d r e n −> < 1 , 2 >},
c h i l d r e n −> <>},
c h i l d r e n −> < 3 >},
c h i l d r e n −> <>}

// c h i l d e r n o f 2 (B ) :
l = t [2]. children ;
// t h e s e c o n d c h i l d o f 0 (A ) :
c = t [ 0 ] . c h i l d r e n . at ( 1 ) ;

The output:
$ a l k i −a t r e e s . a l k −m
c |−> 2
t |−> [ { c h i l d r e n −> < 1 , 2 > i n f o −> ”A” } , { c h i l d r e n −> < > i n f o −> ”B” } ,
{ c h i l d r e n −> < 3 > i n f o −> ”C” } ,
{ c h i l d r e n −> < > i n f o −> ”D” } ]
l |−> < 3 >

Şt. Ciobâcă, D. Lucanu (FII - UAIC)

Algorithmic Language

PA 2019/2020

31 / 49

Algorithm efficiency: cost functions

Plan

1

Introduction

2

Alk Language
Motivation
Alk by examples

3

Algorithm efficiency: cost functions
Value Size
Time cost of operation evaluation

Şt. Ciobâcă, D. Lucanu (FII - UAIC)

Algorithmic Language

PA 2019/2020

32 / 49

Algorithm efficiency: cost functions

Value Size

Plan

1

Introduction

2

Alk Language
Motivation
Alk by examples

3

Algorithm efficiency: cost functions
Value Size
Time cost of operation evaluation

Şt. Ciobâcă, D. Lucanu (FII - UAIC)

Algorithmic Language

PA 2019/2020

33 / 49

Algorithm efficiency: cost functions

Value Size

On data types

A data type consists of values (constants) and operations.
Each value is represented using a memory space.
For the values of each data type, the size of representation must be
mentioned.
There are (at least) three ways to define the size of values:
uniform: |v |unif
logarithmic: |v |log
linear: |v |lin

Şt. Ciobâcă, D. Lucanu (FII - UAIC)

Algorithmic Language

PA 2019/2020

34 / 49

Algorithm efficiency: cost functions

Value Size

Size of scalars
integers:
Int = {. . . , −2, −1, 0, 1, 2, . . .}
uniform dimension: |n|unif = 1
logarithmic dimension: |n|log = log2 abs(n)
linear dimension: |n|lin = abs(n)

booleans:
Bool = {false, true}
uniform dimension: |b|unif = 1
logarithmic dimension: |b|log = 1
linear dimension: |b|lin = 1

floating point numbers:
Float = rational numbers
uniform dimension: |v |unif = 1
logarithmic dimension: |v |log = log2 (mantisă) + log2 (exponent)
linear dimension: |v |lin = mantisă × 10exponent + exponent + 1

...
Şt. Ciobâcă, D. Lucanu (FII - UAIC)

Algorithmic Language

PA 2019/2020

35 / 49

Algorithm efficiency: cost functions

Value Size

Size of arrays, structures, lists, . . .
Arrays:
value: a = [a0 , a1 , . . . , an−1 ]
size: |a|d = |a0 |d + |a1 |d + · · · + |an−1 |d , d ∈ {unif, log, lin}
bidimensional arrays are arrays of unidimensional arrays,
tridimensional arrays are arrays of bidimensional arrays,
etc.
Structures:
value: s = {f1 → v1 , . . . , fn → vn }
size: |s|d = |v0 |d + |v1 |d + · · · + |vn−1 |d , d ∈ {unif, log, lin}
Lists:
value: l = hv0 , v1 , . . . , vn−1 i.
size: |l|d = |v0 |d + |v1 |d + · · · + |vn−1 |d , d ∈ {unif, log, lin}
Şt. Ciobâcă, D. Lucanu (FII - UAIC)

Algorithmic Language

PA 2019/2020

36 / 49

Algorithm efficiency: cost functions

Time cost of operation evaluation

Plan

1

Introduction

2

Alk Language
Motivation
Alk by examples

3

Algorithm efficiency: cost functions
Value Size
Time cost of operation evaluation

Şt. Ciobâcă, D. Lucanu (FII - UAIC)

Algorithmic Language

PA 2019/2020

37 / 49

Algorithm efficiency: cost functions

Time cost of operation evaluation

Data type (cont.)

Data type = values + operations
Each operation op has a time cost time(op).
For each operation of any data type the cost time must be mentioned.
There three ways to measure the time (inherited from the value
dimension):
uniform: timeunif (op) – uses the uniform dimension of values
logarithmic: timelog (op) – uses the logarithmic dimension of values
linear: timelin (op) – uses the linear dimension of values

Şt. Ciobâcă, D. Lucanu (FII - UAIC)

Algorithmic Language

PA 2019/2020

38 / 49

Algorithm efficiency: cost functions

Time cost of operation evaluation

Time cost of operations with scalars

Here are several examples for integers:
Operation
a +Int b

timeunif (op)
O(1)

a ∗Int b

O(1)

...

...

Şt. Ciobâcă, D. Lucanu (FII - UAIC)

timelog (op)
O(max(log a, log b))
O(log a · log b)
O(max(log a, log b)1.545 )
...

Algorithmic Language

timelin (op)
O(a + b)
O(a · b)
...

PA 2019/2020

39 / 49

Algorithm efficiency: cost functions

Time cost of operation evaluation

Time cost of array operations
lookup
// σ : . . . a 7→ A . . . i 7→ i0 . . .
x = a[ i ];
// σ 0 : . . . a 7→ A . . . i 7→ i0 . . . x 7→ A.lookup(i0 )
time cost
uniform: O(1)
logaritmic: O(i0 + |A[i0 ]|log (where A = [A[0], . . . , A[i0 ], . . .]))
linear: O(|A[i0 ]|lin )
update:
// σ : . . . a 7→ A . . . i 7→ i0 . . . x 7→ v . . .
a [ i ]= x ;
// σ 0 : . . . a 7→ A.update(i0 , v ) . . . i 7→ i0 . . . x 7→ v . . .
time cost
uniform: O(1)
logaritmic: O(i0 + |v |log (where A = [A[0], . . . , A[i0 ], . . .]))
linear: O(|v |lin )
Şt. Ciobâcă, D. Lucanu (FII - UAIC)

Algorithmic Language

PA 2019/2020

40 / 49

Algorithm efficiency: cost functions

Time cost of operation evaluation

Time cost of structure operations

Similar to arrays.

Şt. Ciobâcă, D. Lucanu (FII - UAIC)

Algorithmic Language

PA 2019/2020

41 / 49

Algorithm efficiency: cost functions

Time cost of operation evaluation

Time cost of list operations

The time cost of the operations over lists depends on the implementation
(arrys, llists, . . . ) (see Data Structure course).

Şt. Ciobâcă, D. Lucanu (FII - UAIC)

Algorithmic Language

PA 2019/2020

42 / 49

Algorithm efficiency: cost functions

Time cost of operation evaluation

Time cost of an expression evaluation

The time cost of an expression evaluation is the sum of time costs of the
operations included in the expression.
Obviously, it could be uniform, logarithmic, or linear.

Şt. Ciobâcă, D. Lucanu (FII - UAIC)

Algorithmic Language

PA 2019/2020

43 / 49

Algorithm efficiency: cost functions

Time cost of operation evaluation

Time cost of an instruction execution step

It depends on the state σ and on the executed instruction.
Examples:
hV = E ; S, σi ⇒ hS, σ 0 i
the execution time is equal to the evaluation time of E in the state σ;
hif (E ) S1 else S2 S, σi ⇒ hSi S, σi
the execution time is equal to the evaluation time of E in the state σ;
instruction while (E ) S S 0 can be replaced with the equivalent one
if (E ) {S while (E ) S} S 0 ;
...
Again, it could be uniform, logarithmic, or linear.

Şt. Ciobâcă, D. Lucanu (FII - UAIC)

Algorithmic Language

PA 2019/2020

44 / 49

Algorithm efficiency: cost functions

Time cost of operation evaluation

Time cost of a computation (execution)

A computation (an execution) is a sequence of execution steps:
τ = hS1 , σ1 i ⇒ hS2 , σ2 i ⇒ hS3 , σ3 i ⇒ . . .
The time cost
P of a computation:
time d (τ ) = i time d (hSi , σi i ⇒ hSi+1 , σi+1 i), d ∈ {unif , log , lin}

Şt. Ciobâcă, D. Lucanu (FII - UAIC)

Algorithmic Language

PA 2019/2020

45 / 49

Algorithm efficiency: cost functions

Time cost of operation evaluation

Size cost of a computation (execution)

The size of a state σ is the sum of sizes of the values stored in σ.
The size of a computation:
size d (τ ) = maxi size d (σi ), d ∈ {unif , log , lin}
where τ = hS1 , σ1 i ⇒ hS2 , σ2 i ⇒ hS3 , σ3 i ⇒ . . .

Şt. Ciobâcă, D. Lucanu (FII - UAIC)

Algorithmic Language

PA 2019/2020

46 / 49

Algorithm efficiency: cost functions

Time cost of operation evaluation

Computing cost functions with Alk interpreter 1/3

Consider the algorithm computing the sum of the first n integers:
sum = 0 ;
f o r ( i = 1 ; i <= n ; ++i )
sum += i ;

Şt. Ciobâcă, D. Lucanu (FII - UAIC)

Algorithmic Language

PA 2019/2020

47 / 49

Algorithm efficiency: cost functions

Time cost of operation evaluation

Computing cost functions with Alk interpreter 2/3
We add instructions computing the time cost functions (note that log(x)
returns logarithm in base e):1
unif time = 0;
loge time = 0;
lin time = 0;
sum = 0 ;
f o r ( i = 1 ; i <= n ; ++i ) {
sum += i ;
u n i f t i m e += 1 ;
l o g e t i m e += l o g ( i ) ;
l i n t i m e += i + sum ;
}
log time = loge time / log (2);
1

// c ha ng e from b a s e e t o 2

Not all operations are taken into account. Which ones are missing?

Şt. Ciobâcă, D. Lucanu (FII - UAIC)

Algorithmic Language

PA 2019/2020

48 / 49

Algorithm efficiency: cost functions

Time cost of operation evaluation

Computing cost functions with Alk interpreter 3/3

Run it:
$ a l k i −a sum−w−t i m e . a l k − i ”n|−>50” −m
u n i f t i m e |−> 50 O(n)
l o g e t i m e |−> 1 4 8 . 4 7 7 7 6 6 9 5 0 6
O(n2 ), why?
l i n t i m e |−> 23375
i |−> 51
sum |−> 1275
n |−> 50
l o g t i m e |−> 2 1 4 . 2 0 8 1 3 8 0 4 9 5 O(n!), why?

Şt. Ciobâcă, D. Lucanu (FII - UAIC)

Algorithmic Language

PA 2019/2020

49 / 49

