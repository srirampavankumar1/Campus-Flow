CampusFlow
Smart College Resource Allocation System

PROJECT PURPOSE
CampusFlow models college resource allocation as a graph flow-network problem.
The working scenario is Student to Mentor allocation: each student can receive
at most one mentor, each mentor has a limited seat capacity, and the system
allocates the maximum possible number of students while respecting capacity,
compatibility and preference order. The same graph model can be reused for
electives, labs, classrooms, project guides and placement slots.

ALGORITHMS
Bipartite Matching   - baseline augmenting path matching between students and mentor seats
Edmonds-Karp         - BFS based maximum flow
Max-Flow / Min-Cut   - residual graph reachability to explain bottlenecks
Min-Cost Max-Flow    - Bellman-Ford based successive shortest paths, preference aware
Dinic                - level graph and blocking flow, compared against Edmonds-Karp

FILES
index.html           - complete frontend (HTML and CSS only, no JavaScript)
Main.java            - starts the server, serves index.html, parses form input, orchestrates algorithms
BipartiteMatching.java
EdmondsKarp.java
MinCut.java
MinCostMaxFlow.java
Dinic.java
README.txt

COMPILATION
javac *.java

EXECUTION
java Main

BROWSER ADDRESS
http://localhost:8080

USAGE
Open the browser address, review or edit the sample students, mentors,
capacities and preferences, pick an algorithm, and click Run Allocation.
