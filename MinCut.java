public class MinCut {

    private int vertexCount;
    private int[][] residualCapacity;
    private int source;

    public boolean[] reachableFromSource;

    public MinCut(int vertexCount, int[][] residualCapacity, int source) {
        this.vertexCount = vertexCount;
        this.residualCapacity = residualCapacity;
        this.source = source;
    }

    public void runMinCut() {
        reachableFromSource = new boolean[vertexCount];

        int[] queue = new int[vertexCount];
        int front = 0;
        int rear = 0;

        reachableFromSource[source] = true;
        queue[rear] = source;
        rear++;

        while (front < rear) {
            int currentNode = queue[front];
            front++;

            for (int nextNode = 0; nextNode < vertexCount; nextNode++) {
                if (!reachableFromSource[nextNode] && residualCapacity[currentNode][nextNode] > 0) {
                    reachableFromSource[nextNode] = true;
                    queue[rear] = nextNode;
                    rear++;
                }
            }
        }
    }

    public String buildCutEdgeReport(int[][] originalCapacity, String[] nodeNames) {
        StringBuilder report = new StringBuilder();
        boolean anyCutEdgeFound = false;

        for (int fromNode = 0; fromNode < vertexCount; fromNode++) {
            for (int toNode = 0; toNode < vertexCount; toNode++) {
                if (reachableFromSource[fromNode] && !reachableFromSource[toNode] && originalCapacity[fromNode][toNode] > 0) {
                    report.append(nodeNames[fromNode]);
                    report.append(" -> ");
                    report.append(nodeNames[toNode]);
                    report.append(" (saturated, capacity ");
                    report.append(originalCapacity[fromNode][toNode]);
                    report.append(")\n");
                    anyCutEdgeFound = true;
                }
            }
        }

        if (!anyCutEdgeFound) {
            report.append("No saturated cut edges. Every compatible allocation was completed.\n");
        }

        return report.toString();
    }
}
