CampusFlow
Smart College Resource Allocation System

PROJECT PURPOSE
CampusFlow models college resource allocation as a graph flow-network problem.
The working scenario is Student to Mentor allocation: each student receives at
most one mentor, each mentor has limited seats, and the system allocates the
maximum number of students while respecting capacity, compatibility and
preference order. The same model can be reused for electives, labs, classrooms,
project guides and placement slots.

ALGORITHMS (all written by hand, no collections, no graph libraries)
Bipartite Matching   - augmenting path matching between students and mentor seats
Edmonds-Karp         - BFS based maximum flow
Max-Flow / Min-Cut   - residual graph reachability to explain bottlenecks
Min-Cost Max-Flow    - Bellman-Ford successive shortest paths, preference aware
Dinic                - level graph and blocking flow, compared with Edmonds-Karp

FILES
index.html             complete frontend, HTML and CSS only
Main.java              socket server, HTTP request parsing, orchestration, result page
TextTools.java         own text builder, number parsing, URL decoding, string helpers
BipartiteMatching.java
EdmondsKarp.java
MinCut.java
MinCostMaxFlow.java
Dinic.java
Dockerfile.vercel      container definition used to run the Java server on Vercel

COMPILATION
javac *.java

EXECUTION
java Main

BROWSER ADDRESS
http://localhost:8080

DEPLOYMENT
Vercel builds Dockerfile.vercel and runs the same Java server as a container
function. The PORT environment variable selects the listening port.
