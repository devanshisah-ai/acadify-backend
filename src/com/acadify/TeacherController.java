package com.acadify;

import com.sun.net.httpserver.HttpExchange;
import java.io.IOException;
import java.sql.*;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.concurrent.TimeUnit;

public class TeacherController {

    public static void handle(HttpExchange exchange, int userId, int teacherId) throws IOException {

        String path = exchange.getRequestURI().getPath()
                .replace("/teacher", "")
                .replaceAll("/+$", "");

        if (path.isEmpty()) path = "/";

        String method = exchange.getRequestMethod();
        System.out.println("[TeacherController] PATH = " + path + " METHOD = " + method);

        switch (path) {

            case "/overview":
                if (method.equals("GET")) handleGetOverview(exchange, teacherId);
                else ResponseUtil.sendMethodNotAllowed(exchange);
                break;

            case "/profile":
                if (method.equals("GET")) handleGetProfile(exchange, teacherId, userId);
                else ResponseUtil.sendMethodNotAllowed(exchange);
                break;

            case "/classes":
                if (method.equals("GET")) handleGetClasses(exchange, teacherId);
                else ResponseUtil.sendMethodNotAllowed(exchange);
                break;

            case "/class/students":
                if (method.equals("GET")) handleGetClassStudents(exchange, teacherId);
                else ResponseUtil.sendMethodNotAllowed(exchange);
                break;

            case "/class/performance":
                if (method.equals("GET")) handleClassPerformance(exchange, teacherId);
                else ResponseUtil.sendMethodNotAllowed(exchange);
                break;

            case "/student/performance":
                if (method.equals("GET")) handleStudentPerformance(exchange, teacherId);
                else ResponseUtil.sendMethodNotAllowed(exchange);
                break;

            case "/doubts":
                if (method.equals("GET")) handleGetPendingDoubts(exchange, teacherId);
                else ResponseUtil.sendMethodNotAllowed(exchange);
                break;

            case "/doubt/answer":
                if (method.equals("POST")) handleAnswerDoubt(exchange, teacherId, userId);
                else ResponseUtil.sendMethodNotAllowed(exchange);
                break;

            case "/attendance/list":
                if (method.equals("GET")) handleGetAttendanceList(exchange, teacherId);
                else ResponseUtil.sendMethodNotAllowed(exchange);
                break;

            case "/attendance/mark":
                if (method.equals("POST")) handleMarkAttendance(exchange, teacherId, userId);
                else ResponseUtil.sendMethodNotAllowed(exchange);
                break;

            case "/marks":
                if (method.equals("GET"))       handleGetMarks(exchange, teacherId);
                else if (method.equals("POST"))  handleAddMarks(exchange, teacherId, userId);
                else if (method.equals("PUT"))   handleUpdateMarks(exchange, teacherId, userId);
                else ResponseUtil.sendMethodNotAllowed(exchange);
                break;

            case "/activity":
                if (method.equals("GET")) handleGetActivity(exchange, userId);
                else ResponseUtil.sendMethodNotAllowed(exchange);
                break;

            default:
                System.out.println("[TeacherController] Unmatched path: " + path);
                ResponseUtil.sendNotFound(exchange, "Endpoint not found: " + path);
        }
    }

    // ================= PROFILE =================

    private static void handleGetProfile(HttpExchange exchange, int teacherId, int userId) throws IOException {
        Connection conn = null;
        try {
            conn = DatabaseConfig.getConnection();

            PreparedStatement ps = conn.prepareStatement(
                "SELECT t.name, t.email, t.department, u.email as login_email, u.created_at " +
                "FROM teachers t " +
                "JOIN users u ON t.user_id = u.user_id " +
                "WHERE t.teacher_id = ?");
            ps.setInt(1, teacherId);
            ResultSet rs = ps.executeQuery();

            if (!rs.next()) {
                ResponseUtil.sendNotFound(exchange, "Teacher profile not found");
                return;
            }

            PreparedStatement psClasses = conn.prepareStatement(
                "SELECT COUNT(*) as count FROM subjects WHERE teacher_id = ?");
            psClasses.setInt(1, teacherId);
            ResultSet rsClasses = psClasses.executeQuery();
            int totalClasses = rsClasses.next() ? rsClasses.getInt("count") : 0;
            rsClasses.close(); psClasses.close();

            PreparedStatement psStudents = conn.prepareStatement(
                "SELECT COUNT(DISTINCT m.student_id) as count FROM marks m " +
                "JOIN subjects s ON m.subject_id = s.subject_id WHERE s.teacher_id = ?");
            psStudents.setInt(1, teacherId);
            ResultSet rsStudents = psStudents.executeQuery();
            int totalStudents = rsStudents.next() ? rsStudents.getInt("count") : 0;
            rsStudents.close(); psStudents.close();

            PreparedStatement psDoubts = conn.prepareStatement(
                "SELECT COUNT(*) as count FROM doubts WHERE teacher_id = ? AND status = 'PENDING'");
            psDoubts.setInt(1, teacherId);
            ResultSet rsDoubts = psDoubts.executeQuery();
            int pendingDoubts = rsDoubts.next() ? rsDoubts.getInt("count") : 0;
            rsDoubts.close(); psDoubts.close();

            String name      = rs.getString("name");
            String email     = rs.getString("login_email");
            String dept      = rs.getString("department");
            Timestamp joined = rs.getTimestamp("created_at");

            String data = JsonBuilder.object()
                .add("name",          name)
                .add("email",         email)
                .add("department",    dept != null ? dept : "")
                .add("teacherId",     teacherId)
                .add("userId",        userId)
                .add("totalClasses",  totalClasses)
                .add("totalStudents", totalStudents)
                .add("pendingDoubts", pendingDoubts)
                .add("joinedAt",      joined != null ? joined.toString() : "")
                .build();

            rs.close(); ps.close();
            ResponseUtil.sendJson(exchange, 200, data);

        } catch (SQLException e) {
            System.err.println("[TeacherController] Profile error: " + e.getMessage());
            ResponseUtil.sendServerError(exchange, "Failed to fetch profile");
        } finally {
            DatabaseConfig.releaseConnection(conn);
        }
    }

    // ================= OVERVIEW =================

    private static void handleGetOverview(HttpExchange exchange, int teacherId) throws IOException {
        Connection conn = null;
        try {
            conn = DatabaseConfig.getConnection();

            PreparedStatement psClasses = conn.prepareStatement(
                "SELECT COUNT(*) as count FROM subjects WHERE teacher_id = ?");
            psClasses.setInt(1, teacherId);
            ResultSet rsClasses = psClasses.executeQuery();
            int totalClasses = rsClasses.next() ? rsClasses.getInt("count") : 0;
            rsClasses.close(); psClasses.close();

            PreparedStatement psStudents = conn.prepareStatement(
                "SELECT COUNT(DISTINCT m.student_id) as count FROM marks m " +
                "INNER JOIN subjects s ON m.subject_id = s.subject_id WHERE s.teacher_id = ?");
            psStudents.setInt(1, teacherId);
            ResultSet rsStudents = psStudents.executeQuery();
            int totalStudents = rsStudents.next() ? rsStudents.getInt("count") : 0;
            rsStudents.close(); psStudents.close();

            PreparedStatement psPending = conn.prepareStatement(
                "SELECT COUNT(*) as count FROM doubts WHERE teacher_id = ? AND status = 'PENDING'");
            psPending.setInt(1, teacherId);
            ResultSet rsPending = psPending.executeQuery();
            int pendingDoubts = rsPending.next() ? rsPending.getInt("count") : 0;
            rsPending.close(); psPending.close();

            String pythonOutput = runPythonAnalytics(teacherId);

            String data = JsonBuilder.object()
                .add("totalClasses",  totalClasses)
                .add("totalStudents", totalStudents)
                .add("pendingDoubts", pendingDoubts)
                .addRaw("analytics",  pythonOutput)
                .build();

            ResponseUtil.sendSuccess(exchange, "Overview retrieved", data);

        } catch (SQLException e) {
            System.err.println("[TeacherController] Overview error: " + e.getMessage());
            ResponseUtil.sendServerError(exchange, "Failed to retrieve overview");
        } finally {
            DatabaseConfig.releaseConnection(conn);
        }
    }

    // ================= CLASSES =================

    private static void handleGetClasses(HttpExchange exchange, int teacherId) throws IOException {
        Connection conn = null;
        try {
            conn = DatabaseConfig.getConnection();

            PreparedStatement ps = conn.prepareStatement(
                "SELECT subject_id, subject_name, " +
                "COALESCE(total_marks, 100) as total_marks " +
                "FROM subjects WHERE teacher_id = ?");
            ps.setInt(1, teacherId);
            ResultSet rs = ps.executeQuery();

            JsonBuilder.JsonArrayBuilder array = JsonBuilder.array();
            while (rs.next()) {
                array.add(JsonBuilder.object()
                    .add("subject_id",   rs.getInt("subject_id"))
                    .add("subject_name", rs.getString("subject_name"))
                    .add("total_marks",  rs.getInt("total_marks"))
                    .build());
            }
            rs.close(); ps.close();

            ResponseUtil.sendJson(exchange, 200, array.build());

        } catch (SQLException e) {
            System.err.println("[TeacherController] Classes error: " + e.getMessage());
            ResponseUtil.sendServerError(exchange, "Failed to fetch classes");
        } finally {
            DatabaseConfig.releaseConnection(conn);
        }
    }

    // ================= CLASS STUDENTS =================

    private static void handleGetClassStudents(HttpExchange exchange, int teacherId) throws IOException {
        Connection conn = null;
        try {
            String query        = exchange.getRequestURI().getQuery();
            String subjectIdStr = RequestUtil.extractQueryParam(query, "subject_id");

            if (subjectIdStr == null) {
                ResponseUtil.sendBadRequest(exchange, "subject_id is required");
                return;
            }
            int subjectId = Integer.parseInt(subjectIdStr);

            conn = DatabaseConfig.getConnection();

            PreparedStatement psCheck = conn.prepareStatement(
                "SELECT subject_name FROM subjects WHERE subject_id = ? AND teacher_id = ?");
            psCheck.setInt(1, subjectId);
            psCheck.setInt(2, teacherId);
            ResultSet rsCheck = psCheck.executeQuery();
            if (!rsCheck.next()) {
                ResponseUtil.sendForbidden(exchange, "You do not teach this subject");
                return;
            }
            rsCheck.close(); psCheck.close();

            PreparedStatement ps = conn.prepareStatement(
                "SELECT st.student_id, st.name, " +
                "COALESCE(m.marks_obtained, 0) as marks_obtained " +
                "FROM students st " +
                "LEFT JOIN marks m ON st.student_id = m.student_id AND m.subject_id = ? " +
                "ORDER BY st.name");
            ps.setInt(1, subjectId);
            ResultSet rs = ps.executeQuery();

            JsonBuilder.JsonArrayBuilder array = JsonBuilder.array();
            while (rs.next()) {
                array.add(JsonBuilder.object()
                    .add("student_id", rs.getInt("student_id"))
                    .add("name",       rs.getString("name"))
                    .add("marks",      rs.getDouble("marks_obtained"))
                    .build());
            }
            rs.close(); ps.close();

            ResponseUtil.sendJson(exchange, 200, array.build());

        } catch (SQLException e) {
            System.err.println("[TeacherController] Class students error: " + e.getMessage());
            ResponseUtil.sendServerError(exchange, "Failed to fetch students");
        } finally {
            DatabaseConfig.releaseConnection(conn);
        }
    }

    // ================= CLASS PERFORMANCE =================

    private static void handleClassPerformance(HttpExchange exchange, int teacherId) throws IOException {
        Connection conn = null;
        try {
            String query        = exchange.getRequestURI().getQuery();
            String subjectIdStr = RequestUtil.extractQueryParam(query, "subject_id");
            String semesterStr  = RequestUtil.extractQueryParam(query, "semester");
            String filter       = RequestUtil.extractQueryParam(query, "filter");
            if (filter == null) filter = "all";

            conn = DatabaseConfig.getConnection();

            StringBuilder sql = new StringBuilder(
                "SELECT st.student_id, st.name, " +
                "AVG(m.marks_obtained) as avg_marks, " +
                "COALESCE(sub.total_marks, 100) as total_marks " +
                "FROM marks m " +
                "JOIN students st ON m.student_id = st.student_id " +
                "JOIN subjects sub ON m.subject_id = sub.subject_id " +
                "WHERE sub.teacher_id = ?");

            if (subjectIdStr != null) sql.append(" AND sub.subject_id = ").append(subjectIdStr);
            if (semesterStr  != null) sql.append(" AND m.semester = ").append(semesterStr);

            sql.append(" GROUP BY st.student_id, st.name, sub.total_marks ORDER BY avg_marks DESC");

            if (filter.equals("top10")) sql.append(" LIMIT 10");

            PreparedStatement ps = conn.prepareStatement(sql.toString());
            ps.setInt(1, teacherId);
            ResultSet rs = ps.executeQuery();

            JsonBuilder.JsonArrayBuilder array = JsonBuilder.array();
            while (rs.next()) {
                double avg   = rs.getDouble("avg_marks");
                double total = rs.getDouble("total_marks");
                double pct   = total > 0 ? (avg / total) * 100 : 0;

                if (filter.equals("due") && pct >= 40) continue;

                String grade;
                if      (pct >= 90) grade = "A+";
                else if (pct >= 80) grade = "A";
                else if (pct >= 70) grade = "B";
                else if (pct >= 60) grade = "C";
                else if (pct >= 50) grade = "D";
                else                grade = "F";

                array.add(JsonBuilder.object()
                    .add("student_id",  rs.getInt("student_id"))
                    .add("name",        rs.getString("name"))
                    .add("marks",       Math.round(avg * 10.0) / 10.0)
                    .add("percentage",  Math.round(pct * 10.0) / 10.0)
                    .add("grade",       grade)
                    .build());
            }
            rs.close(); ps.close();

            ResponseUtil.sendJson(exchange, 200, array.build());

        } catch (SQLException e) {
            System.err.println("[TeacherController] Class performance error: " + e.getMessage());
            ResponseUtil.sendServerError(exchange, "Failed to fetch class performance");
        } finally {
            DatabaseConfig.releaseConnection(conn);
        }
    }

    // ================= STUDENT PERFORMANCE =================

    private static void handleStudentPerformance(HttpExchange exchange, int teacherId) throws IOException {
        Connection conn = null;
        try {
            String query        = exchange.getRequestURI().getQuery();
            String studentIdStr = RequestUtil.extractQueryParam(query, "student_id");
            String subjectIdStr = RequestUtil.extractQueryParam(query, "subject_id");

            if (studentIdStr == null) {
                ResponseUtil.sendBadRequest(exchange, "student_id is required");
                return;
            }
            int studentId = Integer.parseInt(studentIdStr);

            conn = DatabaseConfig.getConnection();

            PreparedStatement psName = conn.prepareStatement(
                "SELECT name FROM students WHERE student_id = ?");
            psName.setInt(1, studentId);
            ResultSet rsName = psName.executeQuery();
            String studentName = rsName.next() ? rsName.getString("name") : "Unknown";
            rsName.close(); psName.close();

            StringBuilder sql = new StringBuilder(
                "SELECT sub.subject_name, sub.subject_id, m.marks_obtained, " +
                "COALESCE(sub.total_marks, 100) as total_marks, m.semester " +
                "FROM marks m " +
                "JOIN subjects sub ON m.subject_id = sub.subject_id " +
                "WHERE m.student_id = ? AND sub.teacher_id = ?");
            if (subjectIdStr != null) sql.append(" AND sub.subject_id = ").append(subjectIdStr);
            sql.append(" ORDER BY m.semester, sub.subject_name");

            PreparedStatement ps = conn.prepareStatement(sql.toString());
            ps.setInt(1, studentId);
            ps.setInt(2, teacherId);
            ResultSet rs = ps.executeQuery();

            JsonBuilder.JsonArrayBuilder marksArray = JsonBuilder.array();
            double totalPct = 0; int count = 0;

            while (rs.next()) {
                double obtained = rs.getDouble("marks_obtained");
                double total    = rs.getDouble("total_marks");
                double pct      = total > 0 ? (obtained / total) * 100 : 0;
                totalPct += pct; count++;

                String grade = pct >= 90 ? "A+" : pct >= 80 ? "A" : pct >= 70 ? "B"
                    : pct >= 60 ? "C" : pct >= 50 ? "D" : "F";

                marksArray.add(JsonBuilder.object()
                    .add("subject",    rs.getString("subject_name"))
                    .add("marks",      obtained)
                    .add("totalMarks", total)
                    .add("percentage", Math.round(pct * 10.0) / 10.0)
                    .add("grade",      grade)
                    .add("semester",   rs.getInt("semester"))
                    .build());
            }
            rs.close(); ps.close();

            double avgPct = count > 0 ? totalPct / count : 0;

            PreparedStatement psAtt = conn.prepareStatement(
                "SELECT COUNT(*) as total, " +
                "SUM(CASE WHEN status='PRESENT' THEN 1 ELSE 0 END) as present " +
                "FROM attendance WHERE student_id = ?");
            psAtt.setInt(1, studentId);
            ResultSet rsAtt = psAtt.executeQuery();
            String attPct = "N/A";
            if (rsAtt.next()) {
                int total   = rsAtt.getInt("total");
                int present = rsAtt.getInt("present");
                if (total > 0) attPct = String.format("%.1f%%", (present * 100.0) / total);
            }
            rsAtt.close(); psAtt.close();

            String response = "{" +
                "\"studentId\":" + studentId + "," +
                "\"name\":\"" + studentName.replace("\"", "\\\"") + "\"," +
                "\"avgPercentage\":" + Math.round(avgPct * 10.0) / 10.0 + "," +
                "\"attendance\":\"" + attPct + "\"," +
                "\"marks\":" + marksArray.build() +
                "}";

            ResponseUtil.sendJson(exchange, 200, response);

        } catch (SQLException e) {
            System.err.println("[TeacherController] Student performance error: " + e.getMessage());
            ResponseUtil.sendServerError(exchange, "Failed to fetch student performance");
        } finally {
            DatabaseConfig.releaseConnection(conn);
        }
    }

    // ================= DOUBTS =================

    private static void handleGetPendingDoubts(HttpExchange exchange, int teacherId) throws IOException {
        Connection conn = null;
        try {
            conn = DatabaseConfig.getConnection();

            PreparedStatement ps = conn.prepareStatement(
                "SELECT d.doubt_id, d.question, d.answer, d.status, d.subject, " +
                "st.name as student_name, st.student_id " +
                "FROM doubts d " +
                "JOIN students st ON d.student_id = st.student_id " +
                "WHERE d.teacher_id = ? OR (d.teacher_id IS NULL AND d.status = 'PENDING') " +
                "ORDER BY CASE WHEN d.status='PENDING' THEN 0 ELSE 1 END, d.doubt_id DESC");
            ps.setInt(1, teacherId);
            ResultSet rs = ps.executeQuery();

            JsonBuilder.JsonArrayBuilder array = JsonBuilder.array();
            while (rs.next()) {
                String answer  = rs.getString("answer");
                String subject = rs.getString("subject");
                array.add(JsonBuilder.object()
                    .add("doubt_id",   rs.getInt("doubt_id"))
                    .add("question",   rs.getString("question"))
                    .add("student",    rs.getString("student_name"))
                    .add("student_id", rs.getInt("student_id"))
                    .add("subject",    subject != null ? subject : "")
                    .add("status",     rs.getString("status"))
                    .addRaw("answer",  answer == null ? "null" : "\"" + answer.replace("\"", "\\\"") + "\"")
                    .build());
            }
            rs.close(); ps.close();

            ResponseUtil.sendJson(exchange, 200, array.build());

        } catch (SQLException e) {
            System.err.println("[TeacherController] Doubts error: " + e.getMessage());
            ResponseUtil.sendServerError(exchange, "Failed to fetch doubts");
        } finally {
            DatabaseConfig.releaseConnection(conn);
        }
    }

    // ================= ANSWER DOUBT =================

    private static void handleAnswerDoubt(HttpExchange exchange, int teacherId, int userId) throws IOException {
        Connection conn = null;
        try {
            String body     = RequestUtil.readBody(exchange);
            String[] fields = RequestUtil.parseJson(body, "doubt_id", "answer");
            String doubtIdStr = fields[0];
            String answer     = fields[1];

            if (doubtIdStr == null || answer == null || answer.isBlank()) {
                ResponseUtil.sendBadRequest(exchange, "doubt_id and answer are required");
                return;
            }

            int doubtId = Integer.parseInt(doubtIdStr);
            conn = DatabaseConfig.getConnection();

            PreparedStatement ps = conn.prepareStatement(
                "UPDATE doubts SET answer = ?, status = 'ANSWERED', teacher_id = ? WHERE doubt_id = ?");
            ps.setString(1, answer);
            ps.setInt(2, teacherId);
            ps.setInt(3, doubtId);
            int updated = ps.executeUpdate();
            ps.close();

            if (updated == 0) {
                ResponseUtil.sendError(exchange, 404, "Doubt not found");
                return;
            }

            AuthController.logActivity(conn, userId, "DOUBT_ANSWERED", "doubts", doubtId);
            ResponseUtil.sendJson(exchange, 200, "{\"message\":\"Doubt answered successfully\"}");

        } catch (SQLException e) {
            System.err.println("[TeacherController] Answer doubt error: " + e.getMessage());
            ResponseUtil.sendServerError(exchange, "Failed to answer doubt");
        } finally {
            DatabaseConfig.releaseConnection(conn);
        }
    }

    // ================= ATTENDANCE LIST =================

    private static void handleGetAttendanceList(HttpExchange exchange, int teacherId) throws IOException {
        Connection conn = null;
        try {
            String query        = exchange.getRequestURI().getQuery();
            String subjectIdStr = RequestUtil.extractQueryParam(query, "subject_id");
            String date         = RequestUtil.extractQueryParam(query, "date");

            if (subjectIdStr == null) {
                ResponseUtil.sendBadRequest(exchange, "subject_id is required");
                return;
            }
            int subjectId = Integer.parseInt(subjectIdStr);

            if (date == null) {
                date = new java.sql.Date(System.currentTimeMillis()).toString();
            }

            conn = DatabaseConfig.getConnection();

            PreparedStatement psCheck = conn.prepareStatement(
                "SELECT subject_name FROM subjects WHERE subject_id = ? AND teacher_id = ?");
            psCheck.setInt(1, subjectId);
            psCheck.setInt(2, teacherId);
            ResultSet rsCheck = psCheck.executeQuery();
            if (!rsCheck.next()) {
                ResponseUtil.sendForbidden(exchange, "You do not teach this subject");
                return;
            }
            rsCheck.close(); psCheck.close();

            PreparedStatement ps = conn.prepareStatement(
                "SELECT st.student_id, st.name, " +
                "COALESCE(a.status, 'NOT_MARKED') as status " +
                "FROM students st " +
                "LEFT JOIN attendance a ON st.student_id = a.student_id " +
                "  AND a.attendance_date = ?::date " +
                "  AND a.marked_by IN (SELECT user_id FROM teachers WHERE teacher_id = ?) " +
                "ORDER BY st.name");
            ps.setString(1, date);
            ps.setInt(2, teacherId);
            ResultSet rs = ps.executeQuery();

            JsonBuilder.JsonArrayBuilder array = JsonBuilder.array();
            while (rs.next()) {
                array.add(JsonBuilder.object()
                    .add("student_id", rs.getInt("student_id"))
                    .add("name",       rs.getString("name"))
                    .add("status",     rs.getString("status"))
                    .build());
            }
            rs.close(); ps.close();

            String response = "{\"date\":\"" + date + "\",\"students\":" + array.build() + "}";
            ResponseUtil.sendJson(exchange, 200, response);

        } catch (SQLException e) {
            System.err.println("[TeacherController] Attendance list error: " + e.getMessage());
            ResponseUtil.sendServerError(exchange, "Failed to fetch attendance list");
        } finally {
            DatabaseConfig.releaseConnection(conn);
        }
    }

    // ================= MARK ATTENDANCE =================

    private static void handleMarkAttendance(HttpExchange exchange, int teacherId, int userId) throws IOException {
        Connection conn = null;
        try {
            String body     = RequestUtil.readBody(exchange);
            String[] fields = RequestUtil.parseJson(body, "subject_id", "date");
            String subjectIdStr = fields[0];
            String date         = fields[1];

            if (subjectIdStr == null || date == null) {
                ResponseUtil.sendBadRequest(exchange, "subject_id and date are required");
                return;
            }
            int subjectId = Integer.parseInt(subjectIdStr);

            conn = DatabaseConfig.getConnection();

            PreparedStatement psUser = conn.prepareStatement(
                "SELECT user_id FROM teachers WHERE teacher_id = ?");
            psUser.setInt(1, teacherId);
            ResultSet rsUser = psUser.executeQuery();
            int teacherUserId = rsUser.next() ? rsUser.getInt("user_id") : userId;
            rsUser.close(); psUser.close();

            int recordsStart = body.indexOf("\"records\"");
            if (recordsStart == -1) {
                ResponseUtil.sendBadRequest(exchange, "records array is required");
                return;
            }

            int arrStart = body.indexOf('[', recordsStart);
            int arrEnd   = body.lastIndexOf(']');
            if (arrStart == -1 || arrEnd == -1) {
                ResponseUtil.sendBadRequest(exchange, "Invalid records format");
                return;
            }

            String recordsJson = body.substring(arrStart + 1, arrEnd);
            String[] entries   = recordsJson.split("\\},\\s*\\{");

            int markedCount = 0;
            for (String entry : entries) {
                String studentIdStr = RequestUtil.extractValue(entry, "student_id");
                String status       = RequestUtil.extractValue(entry, "status");

                if (studentIdStr == null || status == null) continue;
                if (!status.equals("PRESENT") && !status.equals("ABSENT")) continue;

                int studentId = Integer.parseInt(studentIdStr.trim());

                PreparedStatement ps = conn.prepareStatement(
                    "INSERT INTO attendance (student_id, attendance_date, status, marked_by) " +
                    "VALUES (?, ?::date, ?, ?) " +
                    "ON CONFLICT (student_id, attendance_date) " +
                    "DO UPDATE SET status = EXCLUDED.status, marked_by = EXCLUDED.marked_by");
                ps.setInt(1, studentId);
                ps.setString(2, date);
                ps.setString(3, status);
                ps.setInt(4, teacherUserId);
                ps.executeUpdate();
                ps.close();
                markedCount++;
            }

            AuthController.logActivity(conn, userId, "ATTENDANCE_MARKED", "attendance", subjectId);
            ResponseUtil.sendJson(exchange, 200,
                "{\"message\":\"Attendance marked for " + markedCount + " students\"}");

        } catch (SQLException e) {
            System.err.println("[TeacherController] Mark attendance error: " + e.getMessage());
            ResponseUtil.sendServerError(exchange, "Failed to mark attendance");
        } finally {
            DatabaseConfig.releaseConnection(conn);
        }
    }

    // ================= GET MARKS =================

    private static void handleGetMarks(HttpExchange exchange, int teacherId) throws IOException {
        Connection conn = null;
        try {
            String query        = exchange.getRequestURI().getQuery();
            String subjectIdStr = RequestUtil.extractQueryParam(query, "subject_id");

            conn = DatabaseConfig.getConnection();

            StringBuilder sql = new StringBuilder(
                "SELECT st.student_id, st.name, sub.subject_name, sub.subject_id, " +
                "m.marks_obtained, COALESCE(sub.total_marks, 100) as total_marks, m.semester " +
                "FROM students st " +
                "LEFT JOIN marks m ON st.student_id = m.student_id " +
                "LEFT JOIN subjects sub ON m.subject_id = sub.subject_id AND sub.teacher_id = ? " +
                "WHERE sub.teacher_id = ?");

            if (subjectIdStr != null) sql.append(" AND sub.subject_id = ").append(subjectIdStr);
            sql.append(" ORDER BY st.name");

            PreparedStatement ps = conn.prepareStatement(sql.toString());
            ps.setInt(1, teacherId);
            ps.setInt(2, teacherId);
            ResultSet rs = ps.executeQuery();

            JsonBuilder.JsonArrayBuilder array = JsonBuilder.array();
            while (rs.next()) {
                double obtained = rs.getDouble("marks_obtained");
                double total    = rs.getDouble("total_marks");
                double pct      = total > 0 ? (obtained / total) * 100 : 0;

                array.add(JsonBuilder.object()
                    .add("student_id",   rs.getInt("student_id"))
                    .add("name",         rs.getString("name"))
                    .add("subject_name", rs.getString("subject_name"))
                    .add("subject_id",   rs.getInt("subject_id"))
                    .add("marks",        obtained)
                    .add("totalMarks",   total)
                    .add("percentage",   Math.round(pct * 10.0) / 10.0)
                    .add("semester",     rs.getInt("semester"))
                    .build());
            }
            rs.close(); ps.close();

            ResponseUtil.sendJson(exchange, 200, array.build());

        } catch (SQLException e) {
            System.err.println("[TeacherController] Get marks error: " + e.getMessage());
            ResponseUtil.sendServerError(exchange, "Failed to fetch marks");
        } finally {
            DatabaseConfig.releaseConnection(conn);
        }
    }

    // ================= ADD MARKS =================

    private static void handleAddMarks(HttpExchange exchange, int teacherId, int userId) throws IOException {
        Connection conn = null;
        try {
            String body     = RequestUtil.readBody(exchange);
            String[] fields = RequestUtil.parseJson(body, "student_id", "subject_id", "marks", "semester");
            String studentIdStr = fields[0];
            String subjectIdStr = fields[1];
            String marksStr     = fields[2];
            String semesterStr  = fields[3];

            if (studentIdStr == null || subjectIdStr == null || marksStr == null) {
                ResponseUtil.sendBadRequest(exchange, "student_id, subject_id and marks are required");
                return;
            }

            int studentId = Integer.parseInt(studentIdStr);
            int subjectId = Integer.parseInt(subjectIdStr);
            double marks  = Double.parseDouble(marksStr);
            int semester  = semesterStr != null ? Integer.parseInt(semesterStr) : 1;

            conn = DatabaseConfig.getConnection();

            PreparedStatement psCheck = conn.prepareStatement(
                "SELECT subject_id FROM subjects WHERE subject_id = ? AND teacher_id = ?");
            psCheck.setInt(1, subjectId);
            psCheck.setInt(2, teacherId);
            ResultSet rsCheck = psCheck.executeQuery();
            if (!rsCheck.next()) {
                ResponseUtil.sendForbidden(exchange, "You do not teach this subject");
                return;
            }
            rsCheck.close(); psCheck.close();

            PreparedStatement ps = conn.prepareStatement(
                "INSERT INTO marks (student_id, subject_id, marks_obtained, semester) " +
                "VALUES (?, ?, ?, ?) " +
                "ON CONFLICT (student_id, subject_id) " +
                "DO UPDATE SET marks_obtained = EXCLUDED.marks_obtained, semester = EXCLUDED.semester");
            ps.setInt(1, studentId);
            ps.setInt(2, subjectId);
            ps.setDouble(3, marks);
            ps.setInt(4, semester);
            ps.executeUpdate();
            ps.close();

            AuthController.logActivity(conn, userId, "MARKS_ADDED", "marks", studentId);
            ResponseUtil.sendJson(exchange, 201, "{\"message\":\"Marks saved successfully\"}");

        } catch (SQLException e) {
            System.err.println("[TeacherController] Add marks error: " + e.getMessage());
            ResponseUtil.sendServerError(exchange, "Failed to add marks");
        } finally {
            DatabaseConfig.releaseConnection(conn);
        }
    }

    // ================= UPDATE MARKS =================

    private static void handleUpdateMarks(HttpExchange exchange, int teacherId, int userId) throws IOException {
        Connection conn = null;
        try {
            String body     = RequestUtil.readBody(exchange);
            String[] fields = RequestUtil.parseJson(body, "student_id", "subject_id", "marks");
            String studentIdStr = fields[0];
            String subjectIdStr = fields[1];
            String marksStr     = fields[2];

            if (studentIdStr == null || subjectIdStr == null || marksStr == null) {
                ResponseUtil.sendBadRequest(exchange, "student_id, subject_id and marks are required");
                return;
            }

            int studentId = Integer.parseInt(studentIdStr);
            int subjectId = Integer.parseInt(subjectIdStr);
            double marks  = Double.parseDouble(marksStr);

            conn = DatabaseConfig.getConnection();

            PreparedStatement ps = conn.prepareStatement(
                "UPDATE marks SET marks_obtained = ? WHERE student_id = ? AND subject_id = ?");
            ps.setDouble(1, marks);
            ps.setInt(2, studentId);
            ps.setInt(3, subjectId);
            int updated = ps.executeUpdate();
            ps.close();

            if (updated == 0) {
                ResponseUtil.sendError(exchange, 404, "Marks record not found");
                return;
            }

            AuthController.logActivity(conn, userId, "MARKS_UPDATED", "marks", studentId);
            ResponseUtil.sendJson(exchange, 200, "{\"message\":\"Marks updated successfully\"}");

        } catch (SQLException e) {
            System.err.println("[TeacherController] Update marks error: " + e.getMessage());
            ResponseUtil.sendServerError(exchange, "Failed to update marks");
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
                "SELECT action, created_at FROM activity_logs " +
                "WHERE user_id = ? ORDER BY created_at DESC LIMIT 20");
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
            System.err.println("[TeacherController] Activity error: " + e.getMessage());
            ResponseUtil.sendServerError(exchange, "Failed to fetch activity");
        } finally {
            DatabaseConfig.releaseConnection(conn);
        }
    }

    // ================= PYTHON HELPER =================

    private static String runPythonAnalytics(int teacherId) {
        try {
            ProcessBuilder pb = new ProcessBuilder(
                "python", "analytics/teacher_analysis.py", String.valueOf(teacherId));
            pb.redirectErrorStream(true);
            Process process = pb.start();
            boolean finished = process.waitFor(5, TimeUnit.SECONDS);
            if (!finished) { process.destroyForcibly(); return "{}"; }

            BufferedReader reader = new BufferedReader(
                new InputStreamReader(process.getInputStream()));
            StringBuilder output = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) output.append(line);
            String result = output.toString().trim();
            return (result.startsWith("{") || result.startsWith("[")) ? result : "{}";
        } catch (Exception e) {
            System.err.println("[TeacherController] Python error: " + e.getMessage());
            return "{}";
        }
    }
}