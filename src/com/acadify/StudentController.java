package com.acadify;

import com.sun.net.httpserver.HttpExchange;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.sql.*;

public class StudentController {

    public static void handle(HttpExchange exchange, int userId, int studentId) throws IOException {

        String path = exchange.getRequestURI().getPath()
                .replace("/student", "")
                .replaceAll("/+$", "");

        if (path.isEmpty()) path = "/";

        String method = exchange.getRequestMethod();

        System.out.println("[StudentController] PATH = " + path + " METHOD = " + method);

        switch (path) {

            case "/overview":
                if (method.equals("GET")) handleGetOverview(exchange, studentId);
                else ResponseUtil.sendError(exchange, 405, "Method not allowed");
                break;

            case "/analytics":
                if (method.equals("GET")) handleStudentAnalytics(exchange, studentId);
                else ResponseUtil.sendMethodNotAllowed(exchange);
                break;

            case "/marks":
                if (method.equals("GET")) handleGetMarks(exchange, studentId);
                else ResponseUtil.sendError(exchange, 405, "Method not allowed");
                break;

            case "/attendance":
                if (method.equals("GET")) handleGetAttendance(exchange, studentId);
                else ResponseUtil.sendError(exchange, 405, "Method not allowed");
                break;

            case "/attendance/mark":
                if (method.equals("POST")) handleMarkAttendance(exchange, studentId);
                else ResponseUtil.sendError(exchange, 405, "Method not allowed");
                break;

            case "/activity":
                if (method.equals("GET")) handleGetActivity(exchange, userId);
                else ResponseUtil.sendError(exchange, 405, "Method not allowed");
                break;

            // ── NEW: subjects enrolled by this student ──────────────────
            case "/subjects":
                if (method.equals("GET")) handleGetStudentSubjects(exchange, studentId);
                else ResponseUtil.sendMethodNotAllowed(exchange);
                break;

            // ── NEW: all teachers with their subject info ───────────────
            case "/teachers":
                if (method.equals("GET")) handleGetTeachers(exchange);
                else ResponseUtil.sendMethodNotAllowed(exchange);
                break;

            case "/doubt":
                if (method.equals("POST")) handleRaiseDoubt(exchange, studentId, userId);
                else ResponseUtil.sendMethodNotAllowed(exchange);
                break;

            case "/doubts":
                if (method.equals("GET")) handleGetDoubts(exchange, studentId);
                else ResponseUtil.sendMethodNotAllowed(exchange);
                break;

            default:
                System.out.println("[StudentController] Unmatched path: " + path);
                ResponseUtil.sendError(exchange, 404, "Endpoint not found: " + path);
        }
    }

    // ================= GET STUDENT'S ENROLLED SUBJECTS =================
    // Returns only subjects this student is enrolled in via student_subjects table.
    // Frontend uses this to populate the Subject dropdown in DoubtsPanel.

    private static void handleGetStudentSubjects(HttpExchange exchange, int studentId) throws IOException {
        Connection conn = null;
        try {
            conn = DatabaseConfig.getConnection();

            PreparedStatement ps = conn.prepareStatement(
                "SELECT s.subject_id, s.subject_name, s.semester " +
                "FROM subjects s " +
                "JOIN student_subjects ss ON ss.subject_id = s.subject_id " +
                "WHERE ss.student_id = ? " +
                "ORDER BY s.semester, s.subject_name");
            ps.setInt(1, studentId);
            ResultSet rs = ps.executeQuery();

            StringBuilder sb = new StringBuilder("[");
            boolean first = true;
            while (rs.next()) {
                if (!first) sb.append(",");
                sb.append("{")
                  .append("\"subject_id\":").append(rs.getInt("subject_id")).append(",")
                  .append("\"subject_name\":\"").append(escape(rs.getString("subject_name"))).append("\",")
                  .append("\"semester\":").append(rs.getInt("semester"))
                  .append("}");
                first = false;
            }
            sb.append("]");
            rs.close();
            ps.close();

            ResponseUtil.sendSuccess(exchange, "Subjects", sb.toString());

        } catch (SQLException e) {
            System.err.println("[StudentController] Get subjects error: " + e.getMessage());
            ResponseUtil.sendServerError(exchange, "Failed to fetch subjects");
        } finally {
            DatabaseConfig.releaseConnection(conn);
        }
    }

    // ================= GET ALL TEACHERS (with subject_id for filtering) =================
    // Returns teacher_id, name, department, and the subject_id they are assigned to.
    // Frontend filters this list client-side when a student picks a subject.

    private static void handleGetTeachers(HttpExchange exchange) throws IOException {
        Connection conn = null;
        try {
            conn = DatabaseConfig.getConnection();

            // Join teachers with subjects so we know which subject each teacher teaches.
            // A teacher may teach multiple subjects — one row per subject assignment.
            PreparedStatement ps = conn.prepareStatement(
                "SELECT t.teacher_id, t.name, t.department, s.subject_id, s.subject_name " +
                "FROM teachers t " +
                "LEFT JOIN subjects s ON s.teacher_id = t.teacher_id " +
                "ORDER BY t.name");
            ResultSet rs = ps.executeQuery();

            StringBuilder sb = new StringBuilder("[");
            boolean first = true;
            while (rs.next()) {
                if (!first) sb.append(",");
                int subjectId = rs.getInt("subject_id");
                boolean hasSubject = !rs.wasNull();
                sb.append("{")
                  .append("\"teacher_id\":").append(rs.getInt("teacher_id")).append(",")
                  .append("\"name\":\"").append(escape(rs.getString("name"))).append("\",")
                  .append("\"department\":\"").append(escape(rs.getString("department"))).append("\",")
                  .append("\"subject_id\":").append(hasSubject ? subjectId : "null").append(",")
                  .append("\"subject_name\":").append(hasSubject ? "\"" + escape(rs.getString("subject_name")) + "\"" : "null")
                  .append("}");
                first = false;
            }
            sb.append("]");
            rs.close();
            ps.close();

            ResponseUtil.sendSuccess(exchange, "Teachers", sb.toString());

        } catch (SQLException e) {
            System.err.println("[StudentController] Get teachers error: " + e.getMessage());
            ResponseUtil.sendServerError(exchange, "Failed to fetch teachers");
        } finally {
            DatabaseConfig.releaseConnection(conn);
        }
    }

    // ================= RAISE DOUBT =================
    // Now accepts subject_id (required) and teacher_id (optional).
    // Also wraps logActivity in try-catch so a broken activity_logs table never breaks doubt submission.

    private static void handleRaiseDoubt(HttpExchange exchange, int studentId, int userId) throws IOException {
        Connection conn = null;
        try {
            String body = RequestUtil.readBody(exchange);
            String[] fields = RequestUtil.parseJson(body, "question", "subject_id", "teacher_id");
            String question    = fields[0];
            String subjectIdStr= fields[1];
            String teacherIdStr= fields[2];

            if (question == null || question.isBlank()) {
                ResponseUtil.sendError(exchange, 400, "Question is required");
                return;
            }
            if (subjectIdStr == null || subjectIdStr.isBlank()) {
                ResponseUtil.sendError(exchange, 400, "subject_id is required");
                return;
            }

            int subjectId = Integer.parseInt(subjectIdStr.trim());
            Integer teacherId = null;
            if (teacherIdStr != null && !teacherIdStr.isBlank() && !teacherIdStr.equals("null")) {
                try { teacherId = Integer.parseInt(teacherIdStr.trim()); } catch (NumberFormatException ignored) {}
            }

            conn = DatabaseConfig.getConnection();

            // Insert doubt — store subject_id and optional teacher_id
            // Adjust column names if your doubts table uses different ones
            PreparedStatement ps;
            if (teacherId != null) {
                ps = conn.prepareStatement(
                    "INSERT INTO doubts (student_id, question, subject_id, teacher_id, status) " +
                    "VALUES (?, ?, ?, ?, 'PENDING') RETURNING doubt_id");
                ps.setInt(1, studentId);
                ps.setString(2, question);
                ps.setInt(3, subjectId);
                ps.setInt(4, teacherId);
            } else {
                ps = conn.prepareStatement(
                    "INSERT INTO doubts (student_id, question, subject_id, status) " +
                    "VALUES (?, ?, ?, 'PENDING') RETURNING doubt_id");
                ps.setInt(1, studentId);
                ps.setString(2, question);
                ps.setInt(3, subjectId);
            }

            ResultSet rs = ps.executeQuery();
            int doubtId = rs.next() ? rs.getInt("doubt_id") : -1;
            rs.close();
            ps.close();

            // Safe log — never breaks the response
            try {
                AuthController.logActivity(conn, userId, "DOUBT_RAISED", "doubts", doubtId);
            } catch (Exception e) {
                System.err.println("[StudentController] Activity log skipped: " + e.getMessage());
            }

            ResponseUtil.sendJson(exchange, 201, "{\"message\":\"Doubt submitted successfully\",\"doubt_id\":" + doubtId + "}");

        } catch (SQLException e) {
            System.err.println("[StudentController] Raise doubt error: " + e.getMessage());
            ResponseUtil.sendError(exchange, 500, "Failed to submit doubt: " + e.getMessage());
        } finally {
            DatabaseConfig.releaseConnection(conn);
        }
    }

    // ================= GET DOUBTS =================
    // Now also returns subject_name and teacher_name by joining

    private static void handleGetDoubts(HttpExchange exchange, int studentId) throws IOException {
        Connection conn = null;
        try {
            conn = DatabaseConfig.getConnection();

            PreparedStatement ps = conn.prepareStatement(
                "SELECT d.doubt_id, d.question, d.answer, d.status, " +
                "       s.subject_name, t.name AS teacher_name " +
                "FROM doubts d " +
                "LEFT JOIN subjects s ON s.subject_id = d.subject_id " +
                "LEFT JOIN teachers t ON t.teacher_id = d.teacher_id " +
                "WHERE d.student_id = ? " +
                "ORDER BY d.doubt_id DESC");
            ps.setInt(1, studentId);
            ResultSet rs = ps.executeQuery();

            StringBuilder json = new StringBuilder("[");
            boolean first = true;
            while (rs.next()) {
                if (!first) json.append(",");
                String answer      = rs.getString("answer");
                String subjectName = rs.getString("subject_name");
                String teacherName = rs.getString("teacher_name");

                json.append("{")
                    .append("\"doubt_id\":").append(rs.getInt("doubt_id")).append(",")
                    .append("\"question\":\"").append(escape(rs.getString("question"))).append("\",")
                    .append("\"answer\":").append(answer == null ? "null" : "\"" + escape(answer) + "\"").append(",")
                    .append("\"status\":\"").append(rs.getString("status")).append("\",")
                    .append("\"subject_name\":").append(subjectName == null ? "null" : "\"" + escape(subjectName) + "\"").append(",")
                    .append("\"teacher_name\":").append(teacherName == null ? "null" : "\"" + escape(teacherName) + "\"")
                    .append("}");
                first = false;
            }
            json.append("]");
            rs.close();
            ps.close();

            ResponseUtil.sendJson(exchange, 200, json.toString());

        } catch (SQLException e) {
            System.err.println("[StudentController] Get doubts error: " + e.getMessage());
            ResponseUtil.sendError(exchange, 500, "Failed to fetch doubts");
        } finally {
            DatabaseConfig.releaseConnection(conn);
        }
    }

    // ================= OVERVIEW =================

    private static void handleGetOverview(HttpExchange exchange, int studentId) throws IOException {
        Connection conn = null;
        try {
            conn = DatabaseConfig.getConnection();

            PreparedStatement psAvg = conn.prepareStatement(
                "SELECT COALESCE(AVG(marks_obtained), 0) as avg_marks FROM marks WHERE student_id = ?");
            psAvg.setInt(1, studentId);
            ResultSet rsAvg = psAvg.executeQuery();
            String avgScore = "0%";
            if (rsAvg.next()) avgScore = String.format("%.1f%%", rsAvg.getDouble("avg_marks"));
            rsAvg.close(); psAvg.close();

            PreparedStatement psCount = conn.prepareStatement("SELECT COUNT(*) as count FROM students");
            ResultSet rsCount = psCount.executeQuery();
            int totalStudents = rsCount.next() ? rsCount.getInt("count") : 0;
            rsCount.close(); psCount.close();

            PreparedStatement psRank = conn.prepareStatement(
                "SELECT COUNT(*) + 1 as rank FROM students s " +
                "WHERE (SELECT COALESCE(AVG(marks_obtained), 0) FROM marks WHERE student_id = s.student_id) " +
                "> (SELECT COALESCE(AVG(marks_obtained), 0) FROM marks WHERE student_id = ?)");
            psRank.setInt(1, studentId);
            ResultSet rsRank = psRank.executeQuery();
            String rank = rsRank.next() ? rsRank.getInt("rank") + " / " + totalStudents : "N/A";
            rsRank.close(); psRank.close();

            PreparedStatement psAtt = conn.prepareStatement(
                "SELECT COUNT(*) AS total, SUM(CASE WHEN status = 'PRESENT' THEN 1 ELSE 0 END) AS present " +
                "FROM attendance WHERE student_id = ?");
            psAtt.setInt(1, studentId);
            ResultSet rsAtt = psAtt.executeQuery();
            String attendance = "N/A";
            if (rsAtt.next()) {
                int total = rsAtt.getInt("total");
                int present = rsAtt.getInt("present");
                if (total > 0) attendance = String.format("%.1f%%", (present * 100.0) / total);
            }
            rsAtt.close(); psAtt.close();

            String data = JsonBuilder.object()
                .add("avgScore",      avgScore)
                .add("attendance",    attendance)
                .add("rank",          rank)
                .add("totalStudents", totalStudents)
                .add("alert",         avgScore.equals("0%") ? "No performance data available" : "")
                .build();

            ResponseUtil.sendJson(exchange, 200, data);

        } catch (SQLException e) {
            System.err.println("[StudentController] Overview error: " + e.getMessage());
            ResponseUtil.sendError(exchange, 500, "Failed to retrieve overview");
        } finally {
            DatabaseConfig.releaseConnection(conn);
        }
    }

    // ================= MARKS =================

    private static void handleGetMarks(HttpExchange exchange, int studentId) throws IOException {
        Connection conn = null;
        try {
            conn = DatabaseConfig.getConnection();

            PreparedStatement ps = conn.prepareStatement(
                "SELECT sub.subject_name, m.marks_obtained, " +
                "       COALESCE(sub.total_marks, 100) as total_marks, m.semester " +
                "FROM marks m " +
                "JOIN subjects sub ON m.subject_id = sub.subject_id " +
                "WHERE m.student_id = ? ORDER BY m.semester, sub.subject_name");
            ps.setInt(1, studentId);
            ResultSet rs = ps.executeQuery();
            JsonBuilder.JsonArrayBuilder array = JsonBuilder.array();

            while (rs.next()) {
                double obtained = rs.getDouble("marks_obtained");
                double total    = rs.getDouble("total_marks");
                double pct      = total > 0 ? (obtained / total) * 100 : 0;
                String grade    = pct >= 90 ? "A+" : pct >= 80 ? "A" : pct >= 70 ? "B" :
                                  pct >= 60 ? "C"  : pct >= 50 ? "D" : "F";
                array.add(JsonBuilder.object()
                    .add("subject",    rs.getString("subject_name"))
                    .add("marks",      obtained)
                    .add("totalMarks", total)
                    .add("percentage", Math.round(pct * 10.0) / 10.0)
                    .add("grade",      grade)
                    .add("semester",   rs.getInt("semester"))
                    .build());
            }
            rs.close(); ps.close();
            ResponseUtil.sendJson(exchange, 200, array.build());

        } catch (SQLException e) {
            System.err.println("[StudentController] Marks error: " + e.getMessage());
            ResponseUtil.sendError(exchange, 500, "Failed to fetch marks");
        } finally {
            DatabaseConfig.releaseConnection(conn);
        }
    }

    // ================= ATTENDANCE =================

    private static void handleGetAttendance(HttpExchange exchange, int studentId) throws IOException {
        Connection conn = null;
        try {
            conn = DatabaseConfig.getConnection();
            PreparedStatement ps = conn.prepareStatement(
                "SELECT attendance_date, status FROM attendance " +
                "WHERE student_id = ? ORDER BY attendance_date DESC");
            ps.setInt(1, studentId);
            ResultSet rs = ps.executeQuery();

            StringBuilder json = new StringBuilder("[");
            boolean first = true;
            int total = 0, present = 0;
            while (rs.next()) {
                if (!first) json.append(",");
                String date   = rs.getDate("attendance_date").toString();
                String status = rs.getString("status");
                json.append("{\"date\":\"").append(date).append("\",\"status\":\"").append(status).append("\"}");
                total++;
                if ("PRESENT".equals(status)) present++;
                first = false;
            }
            json.append("]");
            rs.close(); ps.close();

            double pct = total > 0 ? (present * 100.0) / total : 0;
            String response = "{\"percentage\":" + String.format("%.1f", pct) +
                ",\"totalDays\":" + total +
                ",\"presentDays\":" + present +
                ",\"absentDays\":" + (total - present) +
                ",\"records\":" + json + "}";
            ResponseUtil.sendJson(exchange, 200, response);

        } catch (SQLException e) {
            System.err.println("[StudentController] Attendance error: " + e.getMessage());
            ResponseUtil.sendError(exchange, 500, "Failed to fetch attendance");
        } finally {
            DatabaseConfig.releaseConnection(conn);
        }
    }

    private static void handleMarkAttendance(HttpExchange exchange, int studentId) throws IOException {
        Connection conn = null;
        try {
            String body = RequestUtil.readBody(exchange);
            String[] fields = RequestUtil.parseJson(body, "date", "status");
            String date   = fields[0];
            String status = fields[1];
            if (date == null || date.isBlank()) { ResponseUtil.sendError(exchange, 400, "date is required"); return; }
            if (status == null || (!status.equals("PRESENT") && !status.equals("ABSENT"))) {
                ResponseUtil.sendError(exchange, 400, "status must be PRESENT or ABSENT"); return;
            }
            conn = DatabaseConfig.getConnection();
            PreparedStatement ps = conn.prepareStatement(
                "INSERT INTO attendance (student_id, attendance_date, status) VALUES (?, ?::date, ?) " +
                "ON CONFLICT (student_id, attendance_date) DO UPDATE SET status = EXCLUDED.status");
            ps.setInt(1, studentId); ps.setString(2, date); ps.setString(3, status);
            ps.executeUpdate(); ps.close();
            ResponseUtil.sendJson(exchange, 200, "{\"message\":\"Attendance recorded\"}");
        } catch (SQLException e) {
            System.err.println("[StudentController] Mark attendance error: " + e.getMessage());
            ResponseUtil.sendError(exchange, 500, "Failed to mark attendance");
        } finally {
            DatabaseConfig.releaseConnection(conn);
        }
    }

    // ================= ANALYTICS =================

    private static void handleStudentAnalytics(HttpExchange exchange, int studentId) throws IOException {
        String pythonOutput = null;
        try {
            ProcessBuilder pb = new ProcessBuilder("python", "analytics/student_analysis.py", String.valueOf(studentId));
            pb.redirectErrorStream(true);
            Process process = pb.start();
            BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
            StringBuilder output = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) output.append(line);
            String result = output.toString().trim();
            if (result.startsWith("{") || result.startsWith("[")) {
                pythonOutput = result;
            }
        } catch (Exception e) {
            System.err.println("[StudentController] Python error: " + e.getMessage());
        }

        if (pythonOutput == null) {
            handleGetAnalytics(exchange, studentId);
            return;
        }
        ResponseUtil.sendSuccess(exchange, "Student analytics",
            JsonBuilder.object().addRaw("analytics", pythonOutput).build());
    }

    private static void handleGetAnalytics(HttpExchange exchange, int studentId) throws IOException {
        Connection conn = null;
        try {
            conn = DatabaseConfig.getConnection();
            PreparedStatement ps = conn.prepareStatement(
                "SELECT s.subject_name, m.marks_obtained FROM marks m " +
                "JOIN subjects s ON m.subject_id = s.subject_id WHERE m.student_id = ?");
            ps.setInt(1, studentId);
            ResultSet rs = ps.executeQuery();

            double total = 0; int count = 0;
            java.util.List<String> weak = new java.util.ArrayList<>();
            java.util.List<String> strong = new java.util.ArrayList<>();
            java.util.List<String> trendsJson = new java.util.ArrayList<>();

            while (rs.next()) {
                double marks = rs.getDouble("marks_obtained");
                String name  = rs.getString("subject_name");
                total += marks; count++;
                if (marks < 50)  weak.add(name);
                if (marks >= 75) strong.add(name);
                String trend, message;
                if (marks >= 75)      { trend = "improved"; message = "Best performance in " + name + " — keep it up!"; }
                else if (marks >= 50) { trend = "stable";   message = name + " was okay this time."; }
                else                  { trend = "declined"; message = "Your performance in " + name + " needs improvement."; }
                trendsJson.add("{\"subject\":\"" + name + "\",\"currentMarks\":" + marks +
                    ",\"previousMarks\":null,\"trend\":\"" + trend + "\",\"message\":\"" + message + "\"}");
            }
            rs.close(); ps.close();

            double avg = count == 0 ? 0 : total / count;
            String rec = avg >= 75 ? "Excellent performance! Maintain consistency." :
                         avg >= 60 ? "Good performance. Focus on improving weaker subjects." :
                         avg >= 40 ? "Average performance. Dedicate more time to declined subjects." :
                                     "Performance needs improvement. Seek help for struggling subjects.";

            String data = JsonBuilder.object()
                .add("average", avg)
                .addRaw("weakSubjects",   "[" + String.join(",", weak.stream().map(s -> "\"" + s + "\"").toArray(String[]::new)) + "]")
                .addRaw("strongSubjects", "[" + String.join(",", strong.stream().map(s -> "\"" + s + "\"").toArray(String[]::new)) + "]")
                .addRaw("subjectTrends",  "[" + String.join(",", trendsJson) + "]")
                .add("recommendation", rec)
                .build();
            ResponseUtil.sendJson(exchange, 200, data);

        } catch (SQLException e) {
            System.err.println("[StudentController] Analytics fallback error: " + e.getMessage());
            ResponseUtil.sendError(exchange, 500, "Failed to fetch analytics");
        } finally {
            DatabaseConfig.releaseConnection(conn);
        }
    }

    // ================= ACTIVITY =================

    private static void handleGetActivity(HttpExchange exchange, int userId) throws IOException {
        Connection conn = null;
        try {
            conn = DatabaseConfig.getConnection();
            PreparedStatement ps = conn.prepareStatement(
                "SELECT action, created_at FROM activity_logs WHERE user_id = ? ORDER BY created_at DESC LIMIT 20");
            ps.setInt(1, userId);
            ResultSet rs = ps.executeQuery();
            JsonBuilder.JsonArrayBuilder array = JsonBuilder.array();
            while (rs.next()) {
                array.add(JsonBuilder.object()
                    .add("action", rs.getString("action"))
                    .add("time",   rs.getTimestamp("created_at").toString())
                    .build());
            }
            rs.close(); ps.close();
            ResponseUtil.sendJson(exchange, 200, array.build());
        } catch (SQLException e) {
            System.err.println("[StudentController] Activity error: " + e.getMessage());
            ResponseUtil.sendError(exchange, 500, "Failed to fetch activity");
        } finally {
            DatabaseConfig.releaseConnection(conn);
        }
    }

    // ================= HELPER =================

    private static String escape(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n").replace("\r", "\\r");
    }
}