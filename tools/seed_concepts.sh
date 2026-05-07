#!/usr/bin/env bash
# One-shot concept-catalog seeder. Writes one markdown file per subject with
# `## <concept>` headings. ConceptCatalog walks these to build the catalog.
set -euo pipefail

ROOT="/opt/jarvis/data/archival"

cat > "$ROOT/POO/concepts.md" <<'EOF'
# POO — Object-Oriented Programming

## Classes and objects
## Encapsulation
## Inheritance
## Polymorphism
## Abstract classes
## Interfaces
## Generics
## Collections framework
## Iterators
## Streams API
## Lambda expressions
## Functional interfaces
## Exception handling
## Try-with-resources
## File IO
## Serialization
## Threads
## Synchronization
## Concurrent collections
## JDBC
## Reflection
## Annotations
## Inner classes
## Enums
## Records
EOF

cat > "$ROOT/PA/concepts.md" <<'EOF'
# PA — Algorithm Design

## Asymptotic complexity
## Master theorem
## Divide and conquer
## Merge sort
## Quick sort
## Binary search
## Dynamic programming
## Memoization
## Knapsack problem
## Longest common subsequence
## Edit distance
## Greedy algorithms
## Activity selection
## Huffman coding
## Backtracking
## N-queens
## Graph traversal
## BFS
## DFS
## Topological sort
## Shortest paths
## Dijkstra
## Bellman-Ford
## Floyd-Warshall
## Minimum spanning tree
## Kruskal
## Prim
## Network flow
## Ford-Fulkerson
## NP-completeness
## Reductions
## Approximation algorithms
EOF

cat > "$ROOT/ALO/concepts.md" <<'EOF'
# ALO — Linear Algebra & Optimization

## Vectors
## Vector spaces
## Linear independence
## Basis
## Dimension
## Linear transformations
## Matrices
## Matrix operations
## Determinants
## Inverse matrix
## Linear systems
## Gaussian elimination
## LU decomposition
## Rank
## Null space
## Eigenvalues
## Eigenvectors
## Diagonalization
## Singular value decomposition
## Inner product
## Orthogonality
## Gram-Schmidt
## QR decomposition
## Quadratic forms
## Convex sets
## Convex functions
## Gradient
## Hessian
## Gradient descent
## Constrained optimization
## Lagrange multipliers
## Linear programming
## Simplex method
## Duality
EOF

cat > "$ROOT/PS/concepts.md" <<'EOF'
# PS — Probability & Statistics

## Sample space
## Events
## Probability axioms
## Conditional probability
## Bayes theorem
## Independence
## Random variables
## Discrete distributions
## Continuous distributions
## Bernoulli
## Binomial
## Poisson
## Geometric
## Uniform
## Normal
## Exponential
## Expected value
## Variance
## Covariance
## Correlation
## Joint distributions
## Marginal distributions
## Central limit theorem
## Law of large numbers
## Estimators
## Maximum likelihood
## Method of moments
## Confidence intervals
## Hypothesis testing
## p-value
## Type I and II errors
## Chi-squared test
## t-test
## ANOVA
## Linear regression
## Correlation coefficient
## R programming
## Data visualization
EOF

cat > "$ROOT/SO&RC/concepts.md" <<'EOF'
# SO&RC — Operating Systems and Computer Networks

## Process
## Process state
## Process control block
## Threads
## Context switch
## Scheduling
## Round robin
## Priority scheduling
## Multilevel queue
## Synchronization
## Mutex
## Semaphore
## Monitor
## Deadlock
## Banker algorithm
## Memory management
## Virtual memory
## Paging
## Segmentation
## Page replacement
## LRU
## File system
## Inode
## Directory structure
## Disk scheduling
## RAID
## OSI model
## TCP/IP stack
## Ethernet
## IP addressing
## Subnetting
## Routing
## TCP
## UDP
## Sockets
## DNS
## HTTP
## Network security
## Firewalls
EOF

echo "Seeded concept catalogs:"
find "$ROOT" -name "concepts.md" -exec wc -l {} \;
EOF
