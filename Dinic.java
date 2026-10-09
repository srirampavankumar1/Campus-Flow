public class Dinic {

    private int vertexCount;
    private int[][] originalCapacity;
    private int[][] residualCapacity;
    private int source;
    private int sink;
    private int[] level;
    private int[] nextEdgePointer;

    public int maximumFlow;
    public int iterations;
    public long executionTimeNanos;

    public Dinic(int vertexCount, int[][] capacity, int source, int sink) {
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

        while (buildLevelGraph()) {
            nextEdgePointer = new int[vertexCount];
            for (int nodeIndex = 0; nodeIndex < vertexCount; nodeIndex++) {
                nextEdgePointer[nodeIndex] = 0;
            }

            while (true) {
                int pushedFlow = sendBlockingFlow(source, 1000000000);
                if (pushedFlow == 0) {
                    break;
                }
                maximumFlow = maximumFlow + pushedFlow;
            }

            iterations++;
        }

        long endTime = System.nanoTime();
        executionTimeNanos = endTime - startTime;
    }

    private boolean buildLevelGraph() {
        level = new int[vertexCount];
        for (int nodeIndex = 0; nodeIndex < vertexCount; nodeIndex++) {
            level[nodeIndex] = -1;
        }

        int[] queue = new int[vertexCount];
        int front = 0;
        int rear = 0;

        level[source] = 0;
        queue[rear] = source;
        rear++;

        while (front < rear) {
            int currentNode = queue[front];
            front++;

            for (int nextNode = 0; nextNode < vertexCount; nextNode++) {
                if (level[nextNode] == -1 && residualCapacity[currentNode][nextNode] > 0) {
                    level[nextNode] = level[currentNode] + 1;
                    queue[rear] = nextNode;
                    rear++;
                }
            }
        }

        return level[sink] != -1;
    }

    private int sendBlockingFlow(int currentNode, int pushedCapacity) {
        if (currentNode == sink) {
            return pushedCapacity;
        }

        while (nextEdgePointer[currentNode] < vertexCount) {
            int nextNode = nextEdgePointer[currentNode];

            if (level[nextNode] == level[currentNode] + 1 && residualCapacity[currentNode][nextNode] > 0) {
                int limitedCapacity = pushedCapacity;
                if (residualCapacity[currentNode][nextNode] < limitedCapacity) {
                    limitedCapacity = residualCapacity[currentNode][nextNode];
                }

                int actualFlow = sendBlockingFlow(nextNode, limitedCapacity);

                if (actualFlow > 0) {
                    residualCapacity[currentNode][nextNode] = residualCapacity[currentNode][nextNode] - actualFlow;
                    residualCapacity[nextNode][currentNode] = residualCapacity[nextNode][currentNode] + actualFlow;
                    return actualFlow;
                }
            }

            nextEdgePointer[currentNode]++;
        }

        return 0;
    }

    public int[][] getResidualCapacity() {
        return residualCapacity;
    }

    public int getFlowOnEdge(int fromNode, int toNode) {
        return originalCapacity[fromNode][toNode] - residualCapacity[fromNode][toNode];
    }
}
