public class EdmondsKarp {

    private int vertexCount;
    private int[][] originalCapacity;
    private int[][] residualCapacity;
    private int source;
    private int sink;

    public int maximumFlow;
    public int iterations;
    public long executionTimeNanos;

    public EdmondsKarp(int vertexCount, int[][] capacity, int source, int sink) {
        this.vertexCount = vertexCount;
        this.originalCapacity = capacity;
        this.source = source;
        this.sink = sink;
    }

    public void runMaxFlow() {
        long startTime = System.nanoTime();

        residualCapacity = new int[vertexCount][vertexCount];
        for (int rowIndex = 0; rowIndex < vertexCount; rowIndex++) {
            for (int columnIndex = 0; columnIndex < vertexCount; columnIndex++) {
                residualCapacity[rowIndex][columnIndex] = originalCapacity[rowIndex][columnIndex];
            }
        }

        maximumFlow = 0;
        iterations = 0;

        while (true) {
            int[] parent = new int[vertexCount];
            boolean[] visited = new boolean[vertexCount];
            for (int nodeIndex = 0; nodeIndex < vertexCount; nodeIndex++) {
                parent[nodeIndex] = -1;
            }

            int[] queue = new int[vertexCount];
            int front = 0;
            int rear = 0;

            queue[rear] = source;
            rear++;
            visited[source] = true;

            while (front < rear) {
                int currentNode = queue[front];
                front++;

                for (int nextNode = 0; nextNode < vertexCount; nextNode++) {
                    if (!visited[nextNode] && residualCapacity[currentNode][nextNode] > 0) {
                        visited[nextNode] = true;
                        parent[nextNode] = currentNode;
                        queue[rear] = nextNode;
                        rear++;
                    }
                }
            }

            if (!visited[sink]) {
                break;
            }

            int bottleneckFlow = 1000000000;
            int traceNode = sink;
            while (traceNode != source) {
                int previousNode = parent[traceNode];
                if (residualCapacity[previousNode][traceNode] < bottleneckFlow) {
                    bottleneckFlow = residualCapacity[previousNode][traceNode];
                }
                traceNode = previousNode;
            }

            traceNode = sink;
            while (traceNode != source) {
                int previousNode = parent[traceNode];
                residualCapacity[previousNode][traceNode] = residualCapacity[previousNode][traceNode] - bottleneckFlow;
                residualCapacity[traceNode][previousNode] = residualCapacity[traceNode][previousNode] + bottleneckFlow;
                traceNode = previousNode;
            }

            maximumFlow = maximumFlow + bottleneckFlow;
            iterations++;
        }

        long endTime = System.nanoTime();
        executionTimeNanos = endTime - startTime;
    }

    public int[][] getResidualCapacity() {
        return residualCapacity;
    }

    public int getFlowOnEdge(int fromNode, int toNode) {
        return originalCapacity[fromNode][toNode] - residualCapacity[fromNode][toNode];
    }
}
