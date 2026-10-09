public class MinCostMaxFlow {

    private int vertexCount;
    private int[][] originalCapacity;
    private int[][] residualCapacity;
    private int[][] residualCost;
    private int source;
    private int sink;
    private static final int INFINITY_DISTANCE = 1000000;

    public int maximumFlow;
    public int totalCost;
    public int iterations;
    public long executionTimeNanos;

    public MinCostMaxFlow(int vertexCount, int[][] capacity, int[][] cost, int source, int sink) {
        this.vertexCount = vertexCount;
        this.originalCapacity = capacity;
        this.source = source;
        this.sink = sink;

        residualCapacity = new int[vertexCount][vertexCount];
        residualCost = new int[vertexCount][vertexCount];

        for (int rowIndex = 0; rowIndex < vertexCount; rowIndex++) {
            for (int columnIndex = 0; columnIndex < vertexCount; columnIndex++) {
                residualCapacity[rowIndex][columnIndex] = capacity[rowIndex][columnIndex];
                if (capacity[rowIndex][columnIndex] > 0) {
                    residualCost[rowIndex][columnIndex] = cost[rowIndex][columnIndex];
                    residualCost[columnIndex][rowIndex] = 0 - cost[rowIndex][columnIndex];
                }
            }
        }
    }

    public void runMinCostMaxFlow() {
        long startTime = System.nanoTime();

        maximumFlow = 0;
        totalCost = 0;
        iterations = 0;

        while (true) {
            int[] distance = new int[vertexCount];
            int[] parent = new int[vertexCount];
            for (int nodeIndex = 0; nodeIndex < vertexCount; nodeIndex++) {
                distance[nodeIndex] = INFINITY_DISTANCE;
                parent[nodeIndex] = -1;
            }
            distance[source] = 0;

            for (int relaxRound = 0; relaxRound < vertexCount - 1; relaxRound++) {
                for (int fromNode = 0; fromNode < vertexCount; fromNode++) {
                    if (distance[fromNode] == INFINITY_DISTANCE) {
                        continue;
                    }
                    for (int toNode = 0; toNode < vertexCount; toNode++) {
                        if (residualCapacity[fromNode][toNode] > 0) {
                            int candidateDistance = distance[fromNode] + residualCost[fromNode][toNode];
                            if (candidateDistance < distance[toNode]) {
                                distance[toNode] = candidateDistance;
                                parent[toNode] = fromNode;
                            }
                        }
                    }
                }
            }

            if (distance[sink] == INFINITY_DISTANCE) {
                break;
            }

            int bottleneckFlow = Integer.MAX_VALUE;
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
            totalCost = totalCost + (bottleneckFlow * distance[sink]);
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
