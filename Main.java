import java.io.FileInputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;

public class Main {

    private static final int DEFAULT_PORT = 8080;

    public static void main(String[] args) throws Exception {
        int serverPort = DEFAULT_PORT;
        String portFromEnvironment = System.getenv("PORT");
        if (portFromEnvironment != null) {
            int parsedPort = TextTools.parseNonNegativeNumber(portFromEnvironment);
            if (parsedPort > 0) {
                serverPort = parsedPort;
            }
        }

        ServerSocket serverSocket = new ServerSocket(serverPort);
        System.out.println("CampusFlow running");
        System.out.println("Open http://localhost:" + serverPort);

        while (true) {
            Socket clientSocket = serverSocket.accept();
            CampusFlowRequestHandler handler = new CampusFlowRequestHandler(clientSocket);
            Thread handlerThread = new Thread(handler);
            handlerThread.start();
        }
    }
}

class CampusFlowRequestHandler implements Runnable {

    private static final int REQUEST_BUFFER_SIZE = 65536;

    private Socket clientSocket;

    public CampusFlowRequestHandler(Socket clientSocket) {
        this.clientSocket = clientSocket;
    }

    public void run() {
        try {
            handleConnection();
        } catch (Exception exceptionCaught) {
            System.out.println("Request handling error: " + exceptionCaught.getMessage());
        }
        try {
            clientSocket.close();
        } catch (Exception ignoredException) {
            System.out.println("Socket close failed");
        }
    }

    private void handleConnection() throws Exception {
        InputStream inputStream = clientSocket.getInputStream();
        byte[] buffer = new byte[REQUEST_BUFFER_SIZE];
        int received = 0;
        int headerEnd = -1;
        int contentLength = 0;
        boolean bodyIsChunked = false;

        while (received < buffer.length) {
            int bytesRead = inputStream.read(buffer, received, buffer.length - received);
            if (bytesRead <= 0) {
                break;
            }
            received = received + bytesRead;

            if (headerEnd == -1) {
                headerEnd = findHeaderEnd(buffer, received);
                if (headerEnd != -1) {
                    String headerText = bytesToText(buffer, 0, headerEnd);
                    contentLength = readContentLength(headerText);
                    bodyIsChunked = TextTools.containsIgnoringCase(readHeaderValue(headerText, "transfer-encoding:"), "chunked");
                }
            }

            if (headerEnd != -1 && isBodyComplete(buffer, headerEnd, received, contentLength, bodyIsChunked)) {
                break;
            }
        }

        if (headerEnd == -1) {
            return;
        }

        String headerText = bytesToText(buffer, 0, headerEnd);
        int firstLineEnd = TextTools.indexOfChar(headerText, '\n', 0);
        String requestLine = TextTools.trim(TextTools.cut(headerText, 0, firstLineEnd));
        int firstSpace = TextTools.indexOfChar(requestLine, ' ', 0);
        if (firstSpace == -1) {
            return;
        }
        int secondSpace = TextTools.indexOfChar(requestLine, ' ', firstSpace + 1);
        if (secondSpace == -1) {
            secondSpace = requestLine.length();
        }
        String httpMethod = TextTools.cut(requestLine, 0, firstSpace);
        String requestPath = TextTools.cut(requestLine, firstSpace + 1, secondSpace);
        int queryStart = TextTools.indexOfChar(requestPath, '?', 0);
        if (queryStart != -1) {
            requestPath = TextTools.cut(requestPath, 0, queryStart);
        }

        String requestBody = "";
        if (bodyIsChunked) {
            requestBody = decodeChunkedBody(buffer, headerEnd, received);
        } else {
            int bodyEnd = headerEnd + contentLength;
            if (bodyEnd > received) {
                bodyEnd = received;
            }
            requestBody = bytesToText(buffer, headerEnd, bodyEnd);
        }

        boolean isHomePage = TextTools.sameText(requestPath, "/") || TextTools.sameText(requestPath, "/index.html");

        if (TextTools.sameText(httpMethod, "POST") && TextTools.sameText(requestPath, "/allocate")) {
            TextBuilder resultPage = CampusFlowEngine.processAllocation(requestBody);
            sendResponse(200, "text/html; charset=UTF-8", resultPage);
        } else if (TextTools.sameText(httpMethod, "GET") && isHomePage) {
            sendResponse(200, "text/html; charset=UTF-8", readIndexHtml());
        } else {
            TextBuilder notFoundBody = new TextBuilder();
            notFoundBody.append("Not Found");
            sendResponse(404, "text/plain; charset=UTF-8", notFoundBody);
        }
    }

    private int findHeaderEnd(byte[] buffer, int received) {
        for (int index = 0; index + 3 < received; index++) {
            if (buffer[index] == '\r' && buffer[index + 1] == '\n' && buffer[index + 2] == '\r' && buffer[index + 3] == '\n') {
                return index + 4;
            }
        }
        return -1;
    }

    private String bytesToText(byte[] buffer, int start, int end) {
        TextBuilder text = new TextBuilder();
        for (int index = start; index < end; index++) {
            text.append((char) (buffer[index] & 0xFF));
        }
        return text.toText();
    }

    private String readHeaderValue(String headerText, String headerNameWithColon) {
        int lineStart = 0;
        while (lineStart < headerText.length()) {
            int lineEnd = TextTools.indexOfChar(headerText, '\n', lineStart);
            if (lineEnd == -1) {
                lineEnd = headerText.length();
            }
            String line = TextTools.cut(headerText, lineStart, lineEnd);
            if (TextTools.startsWithIgnoringCase(line, headerNameWithColon)) {
                return TextTools.trim(TextTools.cut(line, headerNameWithColon.length(), line.length()));
            }
            lineStart = lineEnd + 1;
        }
        return "";
    }

    private int readContentLength(String headerText) {
        return TextTools.parseNonNegativeNumber(readHeaderValue(headerText, "content-length:"));
    }

    private boolean isBodyComplete(byte[] buffer, int headerEnd, int received, int contentLength, boolean bodyIsChunked) {
        if (bodyIsChunked) {
            if (received < headerEnd + 5) {
                return false;
            }
            return buffer[received - 5] == '0' && buffer[received - 4] == '\r' && buffer[received - 3] == '\n'
                && buffer[received - 2] == '\r' && buffer[received - 1] == '\n';
        }
        return received - headerEnd >= contentLength;
    }

    private String decodeChunkedBody(byte[] buffer, int start, int end) {
        TextBuilder body = new TextBuilder();
        int position = start;
        while (position < end) {
            int chunkSize = 0;
            while (position < end && TextTools.hexDigitValue((char) buffer[position]) >= 0) {
                chunkSize = (chunkSize * 16) + TextTools.hexDigitValue((char) buffer[position]);
                position++;
            }
            while (position < end && buffer[position] != '\n') {
                position++;
            }
            position++;
            if (chunkSize == 0) {
                break;
            }
            for (int copied = 0; copied < chunkSize && position < end; copied++) {
                body.append((char) (buffer[position] & 0xFF));
                position++;
            }
            position = position + 2;
        }
        return body.toText();
    }

    private TextBuilder readIndexHtml() throws Exception {
        FileInputStream fileInputStream = new FileInputStream("index.html");
        TextBuilder content = new TextBuilder();
        byte[] chunk = new byte[4096];
        int bytesRead = fileInputStream.read(chunk);
        while (bytesRead > 0) {
            for (int index = 0; index < bytesRead; index++) {
                content.append((char) (chunk[index] & 0xFF));
            }
            bytesRead = fileInputStream.read(chunk);
        }
        fileInputStream.close();
        return content;
    }

    private void sendResponse(int statusCode, String contentType, TextBuilder body) throws Exception {
        byte[] bodyBytes = body.toBytes();

        TextBuilder header = new TextBuilder();
        header.append("HTTP/1.1 ").append(statusCode);
        if (statusCode == 200) {
            header.append(" OK\r\n");
        } else {
            header.append(" Not Found\r\n");
        }
        header.append("Content-Type: ").append(contentType).append("\r\n");
        header.append("Content-Length: ").append(bodyBytes.length).append("\r\n");
        header.append("Connection: close\r\n\r\n");

        OutputStream outputStream = clientSocket.getOutputStream();
        outputStream.write(header.toBytes());
        outputStream.write(bodyBytes);
        outputStream.flush();
    }
}

class CampusFlowEngine {

    private static final int STUDENT_COUNT = 6;
    private static final int MENTOR_COUNT = 3;
    private static final int MAX_PREFERENCES = 3;

    public static TextBuilder processAllocation(String requestBody) {
        String[] studentNames = new String[STUDENT_COUNT];
        String[][] studentPreferences = new String[STUDENT_COUNT][MAX_PREFERENCES];

        for (int studentIndex = 0; studentIndex < STUDENT_COUNT; studentIndex++) {
            int studentNumber = studentIndex + 1;
            String rawName = TextTools.trim(TextTools.findFormValue(requestBody, TextTools.fieldName('s', studentNumber, "_name")));
            if (rawName.length() > 0) {
                studentNames[studentIndex] = rawName;
            } else {
                studentNames[studentIndex] = TextTools.labelWithNumber('S', studentNumber);
            }
            for (int preferenceIndex = 0; preferenceIndex < MAX_PREFERENCES; preferenceIndex++) {
                String fieldName = TextTools.fieldName('s', studentNumber, "_pref" + (preferenceIndex + 1));
                studentPreferences[studentIndex][preferenceIndex] = TextTools.trim(TextTools.findFormValue(requestBody, fieldName));
            }
        }

        String[] mentorNames = new String[MENTOR_COUNT];
        int[] mentorCapacities = new int[MENTOR_COUNT];

        for (int mentorIndex = 0; mentorIndex < MENTOR_COUNT; mentorIndex++) {
            int mentorNumber = mentorIndex + 1;
            String rawName = TextTools.trim(TextTools.findFormValue(requestBody, TextTools.fieldName('m', mentorNumber, "_name")));
            if (rawName.length() > 0) {
                mentorNames[mentorIndex] = rawName;
            } else {
                mentorNames[mentorIndex] = TextTools.labelWithNumber('M', mentorNumber);
            }
            String rawCapacity = TextTools.trim(TextTools.findFormValue(requestBody, TextTools.fieldName('m', mentorNumber, "_cap")));
            mentorCapacities[mentorIndex] = TextTools.parseNonNegativeNumber(rawCapacity);
        }

        String algorithmChoice = TextTools.trim(TextTools.findFormValue(requestBody, "algorithm"));

        int sourceNode = 0;
        int sinkNode = 1 + STUDENT_COUNT + MENTOR_COUNT;
        int vertexCount = sinkNode + 1;

        int[][] capacity = new int[vertexCount][vertexCount];
        int[][] cost = new int[vertexCount][vertexCount];
        boolean[][] compatible = new boolean[STUDENT_COUNT][MENTOR_COUNT];

        for (int studentIndex = 0; studentIndex < STUDENT_COUNT; studentIndex++) {
            int studentNode = 1 + studentIndex;
            capacity[sourceNode][studentNode] = 1;

            for (int preferenceIndex = 0; preferenceIndex < MAX_PREFERENCES; preferenceIndex++) {
                String preferredMentorName = studentPreferences[studentIndex][preferenceIndex];
                if (preferredMentorName.length() == 0) {
                    continue;
                }
                for (int mentorIndex = 0; mentorIndex < MENTOR_COUNT; mentorIndex++) {
                    if (TextTools.sameTextIgnoringCase(mentorNames[mentorIndex], preferredMentorName)) {
                        int mentorNode = 1 + STUDENT_COUNT + mentorIndex;
                        if (capacity[studentNode][mentorNode] == 0) {
                            capacity[studentNode][mentorNode] = 1;
                            cost[studentNode][mentorNode] = preferenceIndex + 1;
                            compatible[studentIndex][mentorIndex] = true;
                        }
                    }
                }
            }
        }

        for (int mentorIndex = 0; mentorIndex < MENTOR_COUNT; mentorIndex++) {
            int mentorNode = 1 + STUDENT_COUNT + mentorIndex;
            capacity[mentorNode][sinkNode] = mentorCapacities[mentorIndex];
        }

        String[] nodeNames = new String[vertexCount];
        nodeNames[sourceNode] = "SOURCE";
        for (int studentIndex = 0; studentIndex < STUDENT_COUNT; studentIndex++) {
            nodeNames[1 + studentIndex] = studentNames[studentIndex];
        }
        for (int mentorIndex = 0; mentorIndex < MENTOR_COUNT; mentorIndex++) {
            nodeNames[1 + STUDENT_COUNT + mentorIndex] = mentorNames[mentorIndex];
        }
        nodeNames[sinkNode] = "SINK";

        BipartiteMatching bipartiteMatching = new BipartiteMatching(STUDENT_COUNT, MENTOR_COUNT, compatible, mentorCapacities);
        bipartiteMatching.runMatching();

        EdmondsKarp edmondsKarp = new EdmondsKarp(vertexCount, capacity, sourceNode, sinkNode);
        edmondsKarp.runMaxFlow();

        Dinic dinic = new Dinic(vertexCount, capacity, sourceNode, sinkNode);
        dinic.runMaxFlow();

        MinCostMaxFlow minCostMaxFlow = new MinCostMaxFlow(vertexCount, capacity, cost, sourceNode, sinkNode);
        minCostMaxFlow.runMinCostMaxFlow();

        int[][] primaryResidual;
        int primaryMaxFlow;
        long primaryExecutionNanos;
        int primaryIterations;
        String primaryAlgorithmName;
        String primaryComplexity;

        if (TextTools.sameText(algorithmChoice, "dinic")) {
            primaryResidual = dinic.getResidualCapacity();
            primaryMaxFlow = dinic.maximumFlow;
            primaryExecutionNanos = dinic.executionTimeNanos;
            primaryIterations = dinic.iterations;
            primaryAlgorithmName = "Dinic";
            primaryComplexity = "O(V^2 * E) general bound";
        } else if (TextTools.sameText(algorithmChoice, "mcmf")) {
            primaryResidual = minCostMaxFlow.getResidualCapacity();
            primaryMaxFlow = minCostMaxFlow.maximumFlow;
            primaryExecutionNanos = minCostMaxFlow.executionTimeNanos;
            primaryIterations = minCostMaxFlow.iterations;
            primaryAlgorithmName = "Preference Optimized (Min-Cost Max-Flow)";
            primaryComplexity = "O(V * E^2) using Bellman-Ford";
        } else {
            primaryResidual = edmondsKarp.getResidualCapacity();
            primaryMaxFlow = edmondsKarp.maximumFlow;
            primaryExecutionNanos = edmondsKarp.executionTimeNanos;
            primaryIterations = edmondsKarp.iterations;
            primaryAlgorithmName = "Edmonds-Karp";
            primaryComplexity = "O(V * E^2)";
        }

        int[] studentAssignedMentorIndex = new int[STUDENT_COUNT];
        for (int studentIndex = 0; studentIndex < STUDENT_COUNT; studentIndex++) {
            studentAssignedMentorIndex[studentIndex] = -1;
        }

        for (int studentIndex = 0; studentIndex < STUDENT_COUNT; studentIndex++) {
            int studentNode = 1 + studentIndex;
            for (int mentorIndex = 0; mentorIndex < MENTOR_COUNT; mentorIndex++) {
                int mentorNode = 1 + STUDENT_COUNT + mentorIndex;
                if (capacity[studentNode][mentorNode] > 0) {
                    int flowValue = capacity[studentNode][mentorNode] - primaryResidual[studentNode][mentorNode];
                    if (flowValue > 0) {
                        studentAssignedMentorIndex[studentIndex] = mentorIndex;
                    }
                }
            }
        }

        int[] mentorAssignedCount = new int[MENTOR_COUNT];
        for (int studentIndex = 0; studentIndex < STUDENT_COUNT; studentIndex++) {
            if (studentAssignedMentorIndex[studentIndex] != -1) {
                mentorAssignedCount[studentAssignedMentorIndex[studentIndex]]++;
            }
        }

        MinCut minCut = new MinCut(vertexCount, primaryResidual, sourceNode);
        minCut.runMinCut();
        TextBuilder cutReport = minCut.buildCutEdgeReport(capacity, nodeNames);

        return ResultPageBuilder.build(
            studentNames, mentorNames, mentorCapacities,
            compatible, studentAssignedMentorIndex, mentorAssignedCount,
            primaryAlgorithmName, primaryComplexity, primaryMaxFlow, primaryExecutionNanos, primaryIterations,
            cutReport, bipartiteMatching.totalMatched,
            edmondsKarp.maximumFlow, dinic.maximumFlow,
            minCostMaxFlow.maximumFlow, minCostMaxFlow.totalCost, cost
        );
    }
}

class ResultPageBuilder {

    public static TextBuilder build(
        String[] studentNames, String[] mentorNames, int[] mentorCapacities,
        boolean[][] compatible, int[] studentAssignedMentorIndex, int[] mentorAssignedCount,
        String primaryAlgorithmName, String primaryComplexity, int primaryMaxFlow, long primaryExecutionNanos, int primaryIterations,
        TextBuilder cutReport, int bipartiteMatchedCount,
        int edmondsKarpMaxFlow, int dinicMaxFlow,
        int minCostMaxFlowValue, int minCostTotalCost, int[][] cost
    ) {
        int studentCount = studentNames.length;
        int mentorCount = mentorNames.length;

        int totalMentorCapacity = 0;
        for (int mentorIndex = 0; mentorIndex < mentorCount; mentorIndex++) {
            totalMentorCapacity = totalMentorCapacity + mentorCapacities[mentorIndex];
        }

        int allocatedCount = 0;
        for (int studentIndex = 0; studentIndex < studentCount; studentIndex++) {
            if (studentAssignedMentorIndex[studentIndex] != -1) {
                allocatedCount++;
            }
        }
        int unallocatedCount = studentCount - allocatedCount;

        int utilizationPercent = 0;
        if (totalMentorCapacity > 0) {
            utilizationPercent = (allocatedCount * 100) / totalMentorCapacity;
        }

        TextBuilder page = new TextBuilder();
        page.append("<!DOCTYPE html>\n<html lang=\"en\">\n<head>\n<meta charset=\"UTF-8\">\n<title>CampusFlow Result</title>\n");
        page.append("<style>\n");
        page.append(":root { --navy:#10213f; --navy-soft:#1c3766; --blue-accent:#2f6fed; --blue-pale:#eaf1ff; --border-color:#dbe3f0; --text-main:#1b2436; --text-muted:#5b6b85; --bg-page:#f5f7fb; --white:#ffffff; --green:#1f8a4c; --red:#c0392b; }\n");
        page.append("* { box-sizing: border-box; }\n");
        page.append("body { margin:0; padding:0; background:var(--bg-page); color:var(--text-main); font-family:-apple-system, Segoe UI, Arial, sans-serif; line-height:1.5; }\n");
        page.append("header.top-header { background:var(--navy); color:var(--white); padding:32px 24px; text-align:center; }\n");
        page.append("header.top-header h1 { margin:0; font-size:34px; letter-spacing:1px; }\n");
        page.append("header.top-header p { margin:8px 0 0 0; color:#c7d4ef; font-size:15px; }\n");
        page.append("main { max-width:1080px; margin:0 auto; padding:24px; }\n");
        page.append("section.card { background:var(--white); border:1px solid var(--border-color); border-radius:10px; padding:24px; margin-bottom:24px; }\n");
        page.append("section.card h2 { margin-top:0; color:var(--navy); font-size:20px; border-bottom:2px solid var(--blue-pale); padding-bottom:10px; }\n");
        page.append("table { width:100%; border-collapse:collapse; margin:12px 0; }\n");
        page.append("table th, table td { border:1px solid var(--border-color); padding:8px 10px; text-align:left; font-size:14px; }\n");
        page.append("table th { background:var(--blue-pale); color:var(--navy); }\n");
        page.append(".summary-grid { display:grid; grid-template-columns:repeat(4, 1fr); gap:14px; }\n");
        page.append(".summary-tile { background:var(--blue-pale); border-radius:8px; padding:14px; text-align:center; }\n");
        page.append(".summary-tile .value { font-size:24px; font-weight:bold; color:var(--navy); }\n");
        page.append(".summary-tile .label { font-size:12px; color:var(--text-muted); text-transform:uppercase; letter-spacing:1px; }\n");
        page.append(".status-allocated { color:var(--green); font-weight:bold; }\n");
        page.append(".status-unallocated { color:var(--red); font-weight:bold; }\n");
        page.append(".flow-diagram { text-align:center; font-size:15px; color:var(--navy-soft); font-weight:bold; padding:18px; background:var(--blue-pale); border-radius:8px; letter-spacing:1px; }\n");
        page.append("pre.report-block { background:var(--bg-page); border:1px solid var(--border-color); border-radius:6px; padding:14px; font-size:13px; white-space:pre-wrap; }\n");
        page.append(".back-link { display:inline-block; margin-top:6px; background:var(--blue-accent); color:var(--white); padding:10px 20px; border-radius:6px; text-decoration:none; }\n");
        page.append("@media (max-width:720px) { .summary-grid { grid-template-columns:1fr 1fr; } }\n");
        page.append("</style>\n</head>\n<body>\n");

        page.append("<header class=\"top-header\">\n<h1>CampusFlow</h1>\n<p>Allocation Result</p>\n</header>\n<main>\n");

        page.append("<section class=\"card\">\n<h2>Allocation Results</h2>\n<div class=\"summary-grid\">\n");
        appendNumberTile(page, studentCount, "Students");
        appendNumberTile(page, mentorCount, "Mentors");
        appendNumberTile(page, totalMentorCapacity, "Total Mentor Capacity");
        appendNumberTile(page, allocatedCount, "Allocated");
        appendNumberTile(page, unallocatedCount, "Unallocated");
        appendNumberTile(page, primaryMaxFlow, "Maximum Flow");
        appendTextTile(page, primaryAlgorithmName, "Algorithm");
        page.append("<div class=\"summary-tile\"><div class=\"value\">").append(utilizationPercent).append("%</div><div class=\"label\">Capacity Utilization</div></div>\n");
        page.append("</div>\n</section>\n");

        page.append("<section class=\"card\">\n<h2>Allocation Table</h2>\n<table>\n");
        page.append("<tr><th>Student</th><th>Assigned Mentor</th><th>Preference Rank</th><th>Status</th></tr>\n");
        for (int studentIndex = 0; studentIndex < studentCount; studentIndex++) {
            int assignedMentorIndex = studentAssignedMentorIndex[studentIndex];
            page.append("<tr><td>").appendEscaped(studentNames[studentIndex]).append("</td>");
            if (assignedMentorIndex != -1) {
                int studentNode = 1 + studentIndex;
                int mentorNode = 1 + studentCount + assignedMentorIndex;
                page.append("<td>").appendEscaped(mentorNames[assignedMentorIndex]).append("</td>");
                page.append("<td>").append(cost[studentNode][mentorNode]).append("</td>");
                page.append("<td class=\"status-allocated\">Allocated</td>");
            } else {
                page.append("<td>-</td><td>-</td><td class=\"status-unallocated\">Unallocated</td>");
            }
            page.append("</tr>\n");
        }
        page.append("</table>\n</section>\n");

        page.append("<section class=\"card\">\n<h2>Bottleneck Analysis</h2>\n<table>\n");
        page.append("<tr><th>Mentor</th><th>Seats Used</th><th>Status</th></tr>\n");
        for (int mentorIndex = 0; mentorIndex < mentorCount; mentorIndex++) {
            page.append("<tr><td>").appendEscaped(mentorNames[mentorIndex]).append("</td><td>");
            page.append(mentorAssignedCount[mentorIndex]).append(" / ").append(mentorCapacities[mentorIndex]);
            if (mentorAssignedCount[mentorIndex] >= mentorCapacities[mentorIndex]) {
                page.append("</td><td>Saturated</td></tr>\n");
            } else {
                page.append("</td><td>Available</td></tr>\n");
            }
        }
        page.append("</table>\n");
        page.append("<pre class=\"report-block\">").appendEscaped(cutReport.toText()).append("</pre>\n");

        for (int studentIndex = 0; studentIndex < studentCount; studentIndex++) {
            if (studentAssignedMentorIndex[studentIndex] != -1) {
                continue;
            }
            boolean hasCompatibleMentor = false;
            boolean allCompatibleFull = true;
            for (int mentorIndex = 0; mentorIndex < mentorCount; mentorIndex++) {
                if (compatible[studentIndex][mentorIndex]) {
                    hasCompatibleMentor = true;
                    if (mentorAssignedCount[mentorIndex] < mentorCapacities[mentorIndex]) {
                        allCompatibleFull = false;
                    }
                }
            }
            page.append("<p>").appendEscaped(studentNames[studentIndex]);
            if (!hasCompatibleMentor) {
                page.append(" could not be allocated because it has no compatible mentor.</p>\n");
            } else if (allCompatibleFull) {
                page.append(" could not be allocated because every compatible mentor reached maximum capacity.</p>\n");
            } else {
                page.append(" could not be allocated because the maximum flow algorithm found a better overall assignment for other students.</p>\n");
            }
        }
        page.append("</section>\n");

        page.append("<section class=\"card\">\n<h2>Algorithm Statistics</h2>\n<table>\n");
        appendTextRow(page, "Algorithm Used", primaryAlgorithmName);
        appendNumberRow(page, "Maximum Flow", primaryMaxFlow);
        page.append("<tr><td>Execution Time</td><td>").appendMilliseconds(primaryExecutionNanos).append("</td></tr>\n");
        appendNumberRow(page, "Iterations", primaryIterations);
        appendTextRow(page, "Time Complexity", primaryComplexity);
        page.append("<tr><td>Bipartite Matching Baseline</td><td>").append(bipartiteMatchedCount).append(" matched</td></tr>\n");
        appendNumberRow(page, "Edmonds-Karp Max Flow", edmondsKarpMaxFlow);
        appendNumberRow(page, "Dinic Max Flow", dinicMaxFlow);
        if (edmondsKarpMaxFlow == dinicMaxFlow) {
            appendTextRow(page, "Edmonds-Karp / Dinic Agreement", "Match");
        } else {
            appendTextRow(page, "Edmonds-Karp / Dinic Agreement", "Mismatch");
        }
        appendNumberRow(page, "Min-Cost Max-Flow Allocation", minCostMaxFlowValue);
        appendNumberRow(page, "Min-Cost Max-Flow Total Preference Cost", minCostTotalCost);
        page.append("</table>\n</section>\n");

        page.append("<section class=\"card\">\n<h2>Graph Model Recap</h2>\n");
        page.append("<div class=\"flow-diagram\">SOURCE &rarr; STUDENTS &rarr; MENTORS &rarr; SINK</div>\n");
        page.append("<p>Source-to-student capacity = 1. Student-to-mentor capacity = 1 for compatible pairs. Mentor-to-sink capacity = mentor seats. Maximum flow = ");
        page.append(primaryMaxFlow).append(" successful assignments.</p>\n");
        page.append("</section>\n");

        page.append("<a class=\"back-link\" href=\"/\">Back to CampusFlow</a>\n");
        page.append("</main>\n</body>\n</html>\n");

        return page;
    }

    private static void appendNumberTile(TextBuilder page, int value, String label) {
        page.append("<div class=\"summary-tile\"><div class=\"value\">").append(value).append("</div><div class=\"label\">").append(label).append("</div></div>\n");
    }

    private static void appendTextTile(TextBuilder page, String value, String label) {
        page.append("<div class=\"summary-tile\"><div class=\"value\">").appendEscaped(value).append("</div><div class=\"label\">").append(label).append("</div></div>\n");
    }

    private static void appendTextRow(TextBuilder page, String label, String value) {
        page.append("<tr><td>").append(label).append("</td><td>").appendEscaped(value).append("</td></tr>\n");
    }

    private static void appendNumberRow(TextBuilder page, String label, int value) {
        page.append("<tr><td>").append(label).append("</td><td>").append(value).append("</td></tr>\n");
    }
}
