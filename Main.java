import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.URLDecoder;

public class Main {

    private static final int STUDENT_COUNT = 6;
    private static final int MENTOR_COUNT = 3;
    private static final int MAX_PREFERENCES = 3;
    private static final int SERVER_PORT = 8080;

    public static void main(String[] args) throws Exception {
        ServerSocket serverSocket = new ServerSocket(SERVER_PORT);
        System.out.println("CampusFlow running");
        System.out.println("Open http://localhost:" + SERVER_PORT);

        while (true) {
            Socket clientSocket = serverSocket.accept();
            CampusFlowRequestHandler handler = new CampusFlowRequestHandler(clientSocket);
            Thread handlerThread = new Thread(handler);
            handlerThread.start();
        }
    }
}

class CampusFlowRequestHandler implements Runnable {

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
        }
    }

    private void handleConnection() throws Exception {
        InputStreamReader inputStreamReader = new InputStreamReader(clientSocket.getInputStream(), "UTF-8");
        BufferedReader reader = new BufferedReader(inputStreamReader);

        String requestLine = reader.readLine();
        if (requestLine == null || requestLine.length() == 0) {
            return;
        }

        String[] requestLineParts = requestLine.split(" ");
        if (requestLineParts.length < 2) {
            return;
        }
        String httpMethod = requestLineParts[0];
        String requestPath = requestLineParts[1];

        int contentLength = 0;
        String headerLine = reader.readLine();
        while (headerLine != null && headerLine.length() > 0) {
            String lowerHeaderLine = headerLine.toLowerCase();
            if (lowerHeaderLine.startsWith("content-length:")) {
                String lengthText = headerLine.substring(headerLine.indexOf(':') + 1).trim();
                contentLength = Integer.parseInt(lengthText);
            }
            headerLine = reader.readLine();
        }

        String requestBody = "";
        if (contentLength > 0) {
            char[] bodyChars = new char[contentLength];
            int totalCharsRead = 0;
            while (totalCharsRead < contentLength) {
                int charsRead = reader.read(bodyChars, totalCharsRead, contentLength - totalCharsRead);
                if (charsRead == -1) {
                    break;
                }
                totalCharsRead = totalCharsRead + charsRead;
            }
            requestBody = new String(bodyChars, 0, totalCharsRead);
        }

        if (httpMethod.equals("POST") && requestPath.equals("/allocate")) {
            String resultHtml = CampusFlowEngine.processAllocation(requestBody);
            sendResponse(200, "text/html; charset=UTF-8", resultHtml);
        } else if (httpMethod.equals("GET") && (requestPath.equals("/") || requestPath.equals("/index.html"))) {
            String pageHtml = readIndexHtml();
            sendResponse(200, "text/html; charset=UTF-8", pageHtml);
        } else {
            sendResponse(404, "text/plain; charset=UTF-8", "Not Found");
        }
    }

    private String readIndexHtml() throws Exception {
        File htmlFile = new File("index.html");
        FileInputStream fileInputStream = new FileInputStream(htmlFile);
        byte[] fileBytes = new byte[(int) htmlFile.length()];
        int totalBytesRead = 0;
        while (totalBytesRead < fileBytes.length) {
            int bytesRead = fileInputStream.read(fileBytes, totalBytesRead, fileBytes.length - totalBytesRead);
            if (bytesRead == -1) {
                break;
            }
            totalBytesRead = totalBytesRead + bytesRead;
        }
        fileInputStream.close();
        return new String(fileBytes, "UTF-8");
    }

    private void sendResponse(int statusCode, String contentType, String body) throws Exception {
        byte[] bodyBytes = body.getBytes("UTF-8");
        String statusText = statusCode == 200 ? "OK" : "Not Found";

        StringBuilder headerBuilder = new StringBuilder();
        headerBuilder.append("HTTP/1.1 ").append(statusCode).append(" ").append(statusText).append("\r\n");
        headerBuilder.append("Content-Type: ").append(contentType).append("\r\n");
        headerBuilder.append("Content-Length: ").append(bodyBytes.length).append("\r\n");
        headerBuilder.append("Connection: close\r\n");
        headerBuilder.append("\r\n");

        OutputStream outputStream = clientSocket.getOutputStream();
        outputStream.write(headerBuilder.toString().getBytes("UTF-8"));
        outputStream.write(bodyBytes);
        outputStream.flush();
    }
}

class CampusFlowEngine {

    private static final int STUDENT_COUNT = 6;
    private static final int MENTOR_COUNT = 3;
    private static final int MAX_PREFERENCES = 3;

    public static String processAllocation(String requestBody) {
        String[] studentNames = new String[STUDENT_COUNT];
        String[][] studentPreferences = new String[STUDENT_COUNT][MAX_PREFERENCES];

        for (int studentIndex = 0; studentIndex < STUDENT_COUNT; studentIndex++) {
            String rawName = extractFormValue(requestBody, "s" + (studentIndex + 1) + "_name").trim();
            studentNames[studentIndex] = rawName.length() > 0 ? rawName : ("S" + (studentIndex + 1));
            for (int preferenceIndex = 0; preferenceIndex < MAX_PREFERENCES; preferenceIndex++) {
                String rawPreference = extractFormValue(requestBody, "s" + (studentIndex + 1) + "_pref" + (preferenceIndex + 1)).trim();
                studentPreferences[studentIndex][preferenceIndex] = rawPreference;
            }
        }

        String[] mentorNames = new String[MENTOR_COUNT];
        int[] mentorCapacities = new int[MENTOR_COUNT];

        for (int mentorIndex = 0; mentorIndex < MENTOR_COUNT; mentorIndex++) {
            String rawName = extractFormValue(requestBody, "m" + (mentorIndex + 1) + "_name").trim();
            mentorNames[mentorIndex] = rawName.length() > 0 ? rawName : ("M" + (mentorIndex + 1));

            String rawCapacity = extractFormValue(requestBody, "m" + (mentorIndex + 1) + "_cap").trim();
            int parsedCapacity = 0;
            try {
                parsedCapacity = Integer.parseInt(rawCapacity);
            } catch (NumberFormatException numberFormatException) {
                parsedCapacity = 0;
            }
            if (parsedCapacity < 0) {
                parsedCapacity = 0;
            }
            mentorCapacities[mentorIndex] = parsedCapacity;
        }

        String algorithmChoice = extractFormValue(requestBody, "algorithm").trim();
        if (algorithmChoice.length() == 0) {
            algorithmChoice = "ek";
        }

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
                    if (mentorNames[mentorIndex].equalsIgnoreCase(preferredMentorName)) {
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

        if (algorithmChoice.equals("dinic")) {
            primaryResidual = dinic.getResidualCapacity();
            primaryMaxFlow = dinic.maximumFlow;
            primaryExecutionNanos = dinic.executionTimeNanos;
            primaryIterations = dinic.iterations;
            primaryAlgorithmName = "Dinic";
            primaryComplexity = "O(V^2 * E) general bound";
        } else if (algorithmChoice.equals("mcmf")) {
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
        String cutReport = minCut.buildCutEdgeReport(capacity, nodeNames);

        return ResultPageBuilder.build(
            studentNames, studentPreferences, mentorNames, mentorCapacities,
            compatible, studentAssignedMentorIndex, mentorAssignedCount,
            primaryAlgorithmName, primaryComplexity, primaryMaxFlow, primaryExecutionNanos, primaryIterations,
            cutReport, bipartiteMatching.totalMatched,
            edmondsKarp.maximumFlow, dinic.maximumFlow,
            minCostMaxFlow.maximumFlow, minCostMaxFlow.totalCost, cost
        );
    }

    private static String extractFormValue(String body, String key) {
        String searchKey = key + "=";
        int startIndex = body.indexOf(searchKey);
        if (startIndex == -1) {
            return "";
        }
        startIndex = startIndex + searchKey.length();
        int endIndex = body.indexOf('&', startIndex);
        if (endIndex == -1) {
            endIndex = body.length();
        }
        String rawValue = body.substring(startIndex, endIndex);
        try {
            return URLDecoder.decode(rawValue, "UTF-8");
        } catch (Exception decodingException) {
            return rawValue;
        }
    }
}

class ResultPageBuilder {

    public static String build(
        String[] studentNames, String[][] studentPreferences, String[] mentorNames, int[] mentorCapacities,
        boolean[][] compatible, int[] studentAssignedMentorIndex, int[] mentorAssignedCount,
        String primaryAlgorithmName, String primaryComplexity, int primaryMaxFlow, long primaryExecutionNanos, int primaryIterations,
        String cutReport, int bipartiteMatchedCount,
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

        double executionMillis = primaryExecutionNanos / 1000000.0;
        boolean flowAlgorithmsMatch = edmondsKarpMaxFlow == dinicMaxFlow;

        StringBuilder page = new StringBuilder();
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
        page.append(".complexity { display:inline-block; margin-top:8px; background:var(--navy); color:var(--white); padding:3px 10px; border-radius:4px; font-size:12px; }\n");
        page.append("@media (max-width:720px) { .summary-grid { grid-template-columns:1fr 1fr; } }\n");
        page.append("</style>\n</head>\n<body>\n");

        page.append("<header class=\"top-header\">\n<h1>CampusFlow</h1>\n<p>Allocation Result</p>\n</header>\n<main>\n");

        page.append("<section class=\"card\">\n<h2>Allocation Results</h2>\n<div class=\"summary-grid\">\n");
        appendSummaryTile(page, String.valueOf(studentCount), "Students");
        appendSummaryTile(page, String.valueOf(mentorCount), "Mentors");
        appendSummaryTile(page, String.valueOf(totalMentorCapacity), "Total Mentor Capacity");
        appendSummaryTile(page, String.valueOf(allocatedCount), "Allocated");
        appendSummaryTile(page, String.valueOf(unallocatedCount), "Unallocated");
        appendSummaryTile(page, String.valueOf(primaryMaxFlow), "Maximum Flow");
        appendSummaryTile(page, primaryAlgorithmName, "Algorithm");
        String utilizationText = totalMentorCapacity > 0 ? ((allocatedCount * 100) / totalMentorCapacity) + "%" : "0%";
        appendSummaryTile(page, utilizationText, "Capacity Utilization");
        page.append("</div>\n</section>\n");

        page.append("<section class=\"card\">\n<h2>Allocation Table</h2>\n<table>\n");
        page.append("<tr><th>Student</th><th>Assigned Mentor</th><th>Preference Rank</th><th>Status</th></tr>\n");
        for (int studentIndex = 0; studentIndex < studentCount; studentIndex++) {
            int assignedMentorIndex = studentAssignedMentorIndex[studentIndex];
            page.append("<tr><td>").append(studentNames[studentIndex]).append("</td>");
            if (assignedMentorIndex != -1) {
                int studentNode = 1 + studentIndex;
                int mentorNode = 1 + studentCount + assignedMentorIndex;
                int preferenceRank = cost[studentNode][mentorNode];
                page.append("<td>").append(mentorNames[assignedMentorIndex]).append("</td>");
                page.append("<td>").append(preferenceRank).append("</td>");
                page.append("<td class=\"status-allocated\">Allocated</td>");
            } else {
                page.append("<td>-</td><td>-</td><td class=\"status-unallocated\">Unallocated</td>");
            }
            page.append("</tr>\n");
        }
        page.append("</table>\n</section>\n");

        page.append("<section class=\"card\">\n<h2>Bottleneck Analysis</h2>\n<table>\n");
        page.append("<tr><th>Mentor</th><th>Seats Used</th><th>Capacity</th></tr>\n");
        for (int mentorIndex = 0; mentorIndex < mentorCount; mentorIndex++) {
            page.append("<tr><td>").append(mentorNames[mentorIndex]).append("</td><td>");
            page.append(mentorAssignedCount[mentorIndex]).append(" / ").append(mentorCapacities[mentorIndex]);
            page.append("</td><td>").append(mentorAssignedCount[mentorIndex] >= mentorCapacities[mentorIndex] ? "Saturated" : "Available").append("</td></tr>\n");
        }
        page.append("</table>\n");
        page.append("<pre class=\"report-block\">").append(escapeHtml(cutReport)).append("</pre>\n");

        for (int studentIndex = 0; studentIndex < studentCount; studentIndex++) {
            if (studentAssignedMentorIndex[studentIndex] == -1) {
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
                page.append("<p>").append(studentNames[studentIndex]);
                if (!hasCompatibleMentor) {
                    page.append(" could not be allocated because it has no compatible mentor.</p>\n");
                } else if (allCompatibleFull) {
                    page.append(" could not be allocated because every compatible mentor reached maximum capacity.</p>\n");
                } else {
                    page.append(" could not be allocated because the maximum flow algorithm found a better overall assignment for other students.</p>\n");
                }
            }
        }
        page.append("</section>\n");

        page.append("<section class=\"card\">\n<h2>Algorithm Statistics</h2>\n<table>\n");
        appendStatRow(page, "Algorithm Used", primaryAlgorithmName);
        appendStatRow(page, "Maximum Flow", String.valueOf(primaryMaxFlow));
        appendStatRow(page, "Execution Time", String.format("%.3f ms", executionMillis));
        appendStatRow(page, "Iterations", String.valueOf(primaryIterations));
        appendStatRow(page, "Time Complexity", primaryComplexity);
        appendStatRow(page, "Bipartite Matching Baseline", bipartiteMatchedCount + " matched");
        appendStatRow(page, "Edmonds-Karp Max Flow", String.valueOf(edmondsKarpMaxFlow));
        appendStatRow(page, "Dinic Max Flow", String.valueOf(dinicMaxFlow));
        appendStatRow(page, "Edmonds-Karp / Dinic Agreement", flowAlgorithmsMatch ? "Match" : "Mismatch");
        appendStatRow(page, "Min-Cost Max-Flow Allocation", String.valueOf(minCostMaxFlowValue));
        appendStatRow(page, "Min-Cost Max-Flow Total Preference Cost", String.valueOf(minCostTotalCost));
        page.append("</table>\n</section>\n");

        page.append("<section class=\"card\">\n<h2>Graph Model Recap</h2>\n");
        page.append("<div class=\"flow-diagram\">SOURCE &rarr; STUDENTS &rarr; MENTORS &rarr; SINK</div>\n");
        page.append("<p>Source-to-student capacity = 1. Student-to-mentor capacity = 1 for compatible pairs. Mentor-to-sink capacity = mentor seats. Maximum flow = ").append(primaryMaxFlow).append(" successful assignments.</p>\n");
        page.append("</section>\n");

        page.append("<a class=\"back-link\" href=\"/\">Back to CampusFlow</a>\n");
        page.append("</main>\n</body>\n</html>\n");

        return page.toString();
    }

    private static void appendSummaryTile(StringBuilder page, String value, String label) {
        page.append("<div class=\"summary-tile\"><div class=\"value\">").append(value).append("</div><div class=\"label\">").append(label).append("</div></div>\n");
    }

    private static void appendStatRow(StringBuilder page, String label, String value) {
        page.append("<tr><td>").append(label).append("</td><td>").append(value).append("</td></tr>\n");
    }

    private static String escapeHtml(String rawText) {
        StringBuilder escaped = new StringBuilder();
        for (int characterIndex = 0; characterIndex < rawText.length(); characterIndex++) {
            char currentChar = rawText.charAt(characterIndex);
            if (currentChar == '<') {
                escaped.append("&lt;");
            } else if (currentChar == '>') {
                escaped.append("&gt;");
            } else if (currentChar == '&') {
                escaped.append("&amp;");
            } else {
                escaped.append(currentChar);
            }
        }
        return escaped.toString();
    }
}
