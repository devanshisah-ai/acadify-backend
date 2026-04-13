package com.acadify;

import java.io.IOException;
import java.sql.*;
import com.sun.net.httpserver.HttpExchange;

public class AdminController {

    public static void handle(HttpExchange exchange, int userId) throws IOException {

        String path = exchange.getRequestURI().getPath()
                .replace("/admin", "")
                .replaceAll("/+$", "");

        if (path.isEmpty()) path = "/";

        String method = exchange.getRequestMethod();

        System.out.println("[AdminController] PATH = " + path + " METHOD = " + method);

        switch (path) {
            case "/overview":
                if (method.equals("GET")) handleGetOverview(exchange);
                else ResponseUtil.sendMethodNotAllowed(exchange);
                break;
            case "/add-student":
                if (method.equals("POST")) handleCreateStudent(exchange, userId);
                else ResponseUtil.sendMethodNotAllowed(exchange);
                break;
            case "/add-teacher":
                if (method.equals("POST")) handleCreateTeacher(exchange, userId);
                else ResponseUtil.sendMethodNotAllowed(exchange);
                break;
            case "/create-subject":
                if (method.equals("POST")) handleCreateSubject(exchange, userId);
                else ResponseUtil.sendMethodNotAllowed(exchange);
                break;
            case "/assign-teacher":
                if (method.equals("POST")) handleAssignTeacher(exchange, userId);
                else ResponseUtil.sendMethodNotAllowed(exchange);
                break;
            case "/subjects":
                if (method.equals("GET")) handleGetSubjects(exchange);
                else ResponseUtil.sendMethodNotAllowed(exchange);
                break;
            case "/teachers":
                if (method.equals("GET")) handleGetTeachers(exchange);
                else ResponseUtil.sendMethodNotAllowed(exchange);
                break;
            case "/courses":
                if (method.equals("GET")) handleGetCourses(exchange);
                else ResponseUtil.sendMethodNotAllowed(exchange);
                break;
            default:
                System.out.println("[AdminController] Unmatched path: " + path);
                ResponseUtil.sendNotFound(exchange, "Endpoint not found: " + path);
        }
    }

    // ─── Safe activity logger — NEVER throws, never affects main transaction ───
    private static void safeLog(Connection conn, int adminUserId, String action, String table, int recordId) {
        try {
            AuthController.logActivity(conn, adminUserId, action, table, recordId);
        } catch (Exception e) {
            System.err.println("[AdminController] Activity log skipped: " + e.getMessage());
        }
    }

    // ================= OVERVIEW =================

    private static void handleGetOverview(HttpExchange exchange) throws IOException {
        Connection conn = null;
        try {
            conn = DatabaseConfig.getConnection();

            int totalStudents = count(conn, "students");
            int totalTeachers = count(conn, "teachers");
            int totalCourses  = count(conn, "subjects");

            PreparedStatement psDoubts = conn.prepareStatement(
                "SELECT COUNT(*) as count FROM doubts WHERE status = 'PENDING'");
            ResultSet rsDoubts = psDoubts.executeQuery();
            int pendingDoubts = rsDoubts.next() ? rsDoubts.getInt("count") : 0;
            rsDoubts.close();
            psDoubts.close();

            String data = JsonBuilder.object()
                .add("totalStudents", totalStudents)
                .add("totalTeachers", totalTeachers)
                .add("totalCourses",  totalCourses)
                .add("pendingDoubts", pendingDoubts)
                .build();

            ResponseUtil.sendSuccess(exchange, "Overview", data);

        } catch (SQLException e) {
            System.err.println("[AdminController] Overview error: " + e.getMessage());
            ResponseUtil.sendServerError(exchange, "Failed to retrieve overview");
        } finally {
            DatabaseConfig.releaseConnection(conn);
        }
    }

    private static int count(Connection conn, String table) throws SQLException {
        PreparedStatement ps = conn.prepareStatement(
            "SELECT COUNT(*) as count FROM " + table);
        ResultSet rs = ps.executeQuery();
        int c = rs.next() ? rs.getInt("count") : 0;
        rs.close();
        ps.close();
        return c;
    }

    // ================= ADD STUDENT =================

    private static void handleCreateStudent(HttpExchange exchange, int adminUserId) throws IOException {
        String body = RequestUtil.readBody(exchange);
        String[] f = RequestUtil.parseJson(body, "name", "email", "course");

        String name   = f[0];
        String email  = f[1];
        String course = f[2];

        if (name == null || email == null || course == null) {
            ResponseUtil.sendBadRequest(exchange, "name, email and course are required");
            return;
        }

        java.util.List<Integer> subjectIds = parseIntArray(body, "subject_ids");

        Connection conn = null;
        try {
            conn = DatabaseConfig.getConnection();
            conn.setAutoCommit(false);

            String defaultPassword = AuthController.hashPassword("Student@123");

            PreparedStatement psUser = conn.prepareStatement(
                "INSERT INTO users (email, password, role) VALUES (?, ?, 'STUDENT') RETURNING user_id");
            psUser.setString(1, email);
            psUser.setString(2, defaultPassword);
            ResultSet rsUser = psUser.executeQuery();
            if (!rsUser.next()) {
                conn.rollback();
                ResponseUtil.sendServerError(exchange, "Failed to create user account");
                return;
            }
            int newUserId = rsUser.getInt("user_id");
            rsUser.close();
            psUser.close();

            PreparedStatement ps = conn.prepareStatement(
                "INSERT INTO students (user_id, name, roll_number, stream, age) VALUES (?, ?, ?, ?, 18) RETURNING student_id");
            ps.setInt(1, newUserId);
            ps.setString(2, name);
            ps.setString(3, "STU-" + newUserId);
            ps.setString(4, course);
            ResultSet rsStudent = ps.executeQuery();
            int studentId = rsStudent.next() ? rsStudent.getInt("student_id") : -1;
            rsStudent.close();
            ps.close();

            if (studentId != -1 && !subjectIds.isEmpty()) {
                PreparedStatement psEnroll = conn.prepareStatement(
                    "INSERT INTO student_subjects (student_id, subject_id) VALUES (?, ?) ON CONFLICT DO NOTHING");
                for (int subjectId : subjectIds) {
                    psEnroll.setInt(1, studentId);
                    psEnroll.setInt(2, subjectId);
                    psEnroll.addBatch();
                }
                psEnroll.executeBatch();
                psEnroll.close();
            }

            // ✅ Commit FIRST — data is safe regardless of what logActivity does
            conn.commit();

            // ✅ Log after commit, in its own try-catch — never affects saved data
            safeLog(conn, adminUserId, "STUDENT_ADDED", "students", newUserId);

            ResponseUtil.sendSuccess(exchange, "Student added successfully");

        } catch (SQLException e) {
            if (conn != null) try { conn.rollback(); } catch (SQLException ignored) {}
            if (e.getMessage() != null && e.getMessage().contains("duplicate key")) {
                ResponseUtil.sendConflict(exchange, "Email already registered");
            } else {
                System.err.println("[AdminController] Add student error: " + e.getMessage());
                ResponseUtil.sendServerError(exchange, "Failed to add student: " + e.getMessage());
            }
        } finally {
            if (conn != null) try { conn.setAutoCommit(true); } catch (SQLException ignored) {}
            DatabaseConfig.releaseConnection(conn);
        }
    }

    private static java.util.List<Integer> parseIntArray(String json, String key) {
        java.util.List<Integer> result = new java.util.ArrayList<>();
        if (json == null) return result;
        String search = "\"" + key + "\"";
        int keyIdx = json.indexOf(search);
        if (keyIdx == -1) return result;
        int bracketOpen = json.indexOf('[', keyIdx);
        int bracketClose = json.indexOf(']', bracketOpen);
        if (bracketOpen == -1 || bracketClose == -1) return result;
        String arr = json.substring(bracketOpen + 1, bracketClose).trim();
        if (arr.isEmpty()) return result;
        for (String part : arr.split(",")) {
            try { result.add(Integer.parseInt(part.trim())); } catch (NumberFormatException ignored) {}
        }
        return result;
    }

    // ================= ADD TEACHER =================

    private static void handleCreateTeacher(HttpExchange exchange, int adminUserId) throws IOException {
        String body = RequestUtil.readBody(exchange);
        String[] f = RequestUtil.parseJson(body, "name", "email", "dept");

        String name  = f[0];
        String email = f[1];
        String dept  = f[2];

        if (name == null || email == null || dept == null) {
            ResponseUtil.sendBadRequest(exchange, "name, email and dept are required");
            return;
        }

        Connection conn = null;
        try {
            conn = DatabaseConfig.getConnection();
            conn.setAutoCommit(false);

            String defaultPassword = AuthController.hashPassword("Teacher@123");

            PreparedStatement psUser = conn.prepareStatement(
                "INSERT INTO users (email, password, role) VALUES (?, ?, 'TEACHER') RETURNING user_id");
            psUser.setString(1, email);
            psUser.setString(2, defaultPassword);
            ResultSet rsUser = psUser.executeQuery();
            if (!rsUser.next()) {
                conn.rollback();
                ResponseUtil.sendServerError(exchange, "Failed to create user account");
                return;
            }
            int newUserId = rsUser.getInt("user_id");
            rsUser.close();
            psUser.close();

            PreparedStatement ps = conn.prepareStatement(
                "INSERT INTO teachers (user_id, name, department, designation) VALUES (?, ?, ?, 'Lecturer')");
            ps.setInt(1, newUserId);
            ps.setString(2, name);
            ps.setString(3, dept);
            ps.executeUpdate();
            ps.close();

            // ✅ Commit FIRST — data is safe regardless of what logActivity does
            conn.commit();

            // ✅ Log after commit, in its own try-catch — never affects saved data
            safeLog(conn, adminUserId, "TEACHER_ADDED", "teachers", newUserId);

            ResponseUtil.sendSuccess(exchange, "Teacher added successfully");

        } catch (SQLException e) {
            if (conn != null) try { conn.rollback(); } catch (SQLException ignored) {}
            if (e.getMessage() != null && e.getMessage().contains("duplicate key")) {
                ResponseUtil.sendConflict(exchange, "Email already registered");
            } else {
                System.err.println("[AdminController] Add teacher error: " + e.getMessage());
                ResponseUtil.sendServerError(exchange, "Failed to add teacher: " + e.getMessage());
            }
        } finally {
            if (conn != null) try { conn.setAutoCommit(true); } catch (SQLException ignored) {}
            DatabaseConfig.releaseConnection(conn);
        }
    }

    // ================= CREATE SUBJECT =================

    private static void handleCreateSubject(HttpExchange exchange, int adminUserId) throws IOException {
        String body = RequestUtil.readBody(exchange);
        String[] fields = RequestUtil.parseJson(body, "subject_name", "semester", "credits");
        String subjectName = fields[0];
        String semesterStr = fields[1];
        String creditsStr  = fields[2];

        if (subjectName == null || subjectName.isBlank()) {
            ResponseUtil.sendBadRequest(exchange, "subject_name is required");
            return;
        }
        if (semesterStr == null || semesterStr.isBlank()) {
            ResponseUtil.sendBadRequest(exchange, "semester is required");
            return;
        }

        Connection conn = null;
        try {
            conn = DatabaseConfig.getConnection();
            int semester = Integer.parseInt(semesterStr.trim());
            PreparedStatement ps;
            if (creditsStr != null && !creditsStr.isBlank()) {
                int credits = Integer.parseInt(creditsStr.trim());
                ps = conn.prepareStatement(
                    "INSERT INTO subjects (subject_name, semester, credits) VALUES (?, ?, ?) RETURNING subject_id");
                ps.setString(1, subjectName.trim());
                ps.setInt(2, semester);
                ps.setInt(3, credits);
            } else {
                ps = conn.prepareStatement(
                    "INSERT INTO subjects (subject_name, semester) VALUES (?, ?) RETURNING subject_id");
                ps.setString(1, subjectName.trim());
                ps.setInt(2, semester);
            }
            ResultSet rs = ps.executeQuery();
            int subjectId = rs.next() ? rs.getInt("subject_id") : -1;
            rs.close();
            ps.close();

            // ✅ Log safely — subject is already committed (no transaction here)
            safeLog(conn, adminUserId, "SUBJECT_CREATED", "subjects", subjectId);

            ResponseUtil.sendSuccess(exchange, "Subject created successfully");

        } catch (SQLException e) {
            System.err.println("[AdminController] Create subject error: " + e.getMessage());
            ResponseUtil.sendServerError(exchange, "Failed to create subject: " + e.getMessage());
        } finally {
            DatabaseConfig.releaseConnection(conn);
        }
    }

    // ================= ASSIGN TEACHER =================

    private static void handleAssignTeacher(HttpExchange exchange, int adminUserId) throws IOException {
        String body = RequestUtil.readBody(exchange);
        String[] f = RequestUtil.parseJson(body, "subject_id", "teacher_id");

        String subjectIdStr = f[0];
        String teacherIdStr = f[1];

        if (subjectIdStr == null || teacherIdStr == null) {
            ResponseUtil.sendBadRequest(exchange, "subject_id and teacher_id are required");
            return;
        }

        Connection conn = null;
        try {
            conn = DatabaseConfig.getConnection();
            int subjectId = Integer.parseInt(subjectIdStr.trim());
            int teacherId = Integer.parseInt(teacherIdStr.trim());

            PreparedStatement ps = conn.prepareStatement(
                "UPDATE subjects SET teacher_id = ? WHERE subject_id = ?");
            ps.setInt(1, teacherId);
            ps.setInt(2, subjectId);
            int updated = ps.executeUpdate();
            ps.close();

            if (updated == 0) {
                ResponseUtil.sendError(exchange, 404, "Subject not found");
                return;
            }

            // ✅ Log safely
            safeLog(conn, adminUserId, "TEACHER_ASSIGNED", "subjects", subjectId);

            ResponseUtil.sendSuccess(exchange, "Teacher assigned successfully");

        } catch (SQLException e) {
            System.err.println("[AdminController] Assign teacher error: " + e.getMessage());
            ResponseUtil.sendServerError(exchange, "Failed to assign teacher: " + e.getMessage());
        } finally {
            DatabaseConfig.releaseConnection(conn);
        }
    }

    // ================= GET SUBJECTS =================

    private static void handleGetSubjects(HttpExchange exchange) throws IOException {
        Connection conn = null;
        try {
            conn = DatabaseConfig.getConnection();
            PreparedStatement ps = conn.prepareStatement(
                "SELECT subject_id, subject_name, semester FROM subjects ORDER BY subject_name");
            ResultSet rs = ps.executeQuery();

            StringBuilder sb = new StringBuilder("[");
            boolean first = true;
            while (rs.next()) {
                if (!first) sb.append(",");
                sb.append("{\"subject_id\":").append(rs.getInt("subject_id"))
                  .append(",\"subject_name\":\"")
                  .append(rs.getString("subject_name").replace("\"", "\\\""))
                  .append("\",\"semester\":")
                  .append(rs.getInt("semester"))
                  .append("}");
                first = false;
            }
            sb.append("]");
            rs.close();
            ps.close();

            ResponseUtil.sendSuccess(exchange, "Subjects", sb.toString());

        } catch (SQLException e) {
            System.err.println("[AdminController] Get subjects error: " + e.getMessage());
            ResponseUtil.sendServerError(exchange, "Failed to fetch subjects");
        } finally {
            DatabaseConfig.releaseConnection(conn);
        }
    }

    // ================= GET TEACHERS =================

    private static void handleGetTeachers(HttpExchange exchange) throws IOException {
        Connection conn = null;
        try {
            conn = DatabaseConfig.getConnection();
            PreparedStatement ps = conn.prepareStatement(
                "SELECT teacher_id, name, course FROM teachers ORDER BY name");
            ResultSet rs = ps.executeQuery();

            StringBuilder sb = new StringBuilder("[");
            boolean first = true;
            while (rs.next()) {
                if (!first) sb.append(",");
                String tCourse = rs.getString("course");
                sb.append("{\"teacher_id\":").append(rs.getInt("teacher_id"))
                  .append(",\"name\":\"")
                  .append(rs.getString("name").replace("\"", "\\\""))
                  .append("\",\"course\":")
                  .append(tCourse == null ? "null" : "\"" + tCourse.replace("\"", "\\\"") + "\"")
                  .append("}");
                first = false;
            }
            sb.append("]");
            rs.close();
            ps.close();

            ResponseUtil.sendSuccess(exchange, "Teachers", sb.toString());

        } catch (SQLException e) {
            System.err.println("[AdminController] Get teachers error: " + e.getMessage());
            ResponseUtil.sendServerError(exchange, "Failed to fetch teachers");
        } finally {
            DatabaseConfig.releaseConnection(conn);
        }
    }

    // ================= GET COURSES =================

    private static void handleGetCourses(HttpExchange exchange) throws IOException {
        Connection conn = null;
        try {
            conn = DatabaseConfig.getConnection();
            PreparedStatement ps = conn.prepareStatement(
                "SELECT DISTINCT stream AS name FROM students " +
                "WHERE stream IS NOT NULL ORDER BY stream");
            ResultSet rs = ps.executeQuery();

            StringBuilder sb = new StringBuilder("[");
            boolean first = true;
            while (rs.next()) {
                if (!first) sb.append(",");
                sb.append("{\"name\":\"")
                  .append(rs.getString("name").replace("\"", "\\\""))
                  .append("\"}");
                first = false;
            }
            sb.append("]");
            rs.close();
            ps.close();

            ResponseUtil.sendSuccess(exchange, "Courses", sb.toString());

        } catch (SQLException e) {
            System.err.println("[AdminController] Get courses error: " + e.getMessage());
            ResponseUtil.sendServerError(exchange, "Failed to fetch courses");
        } finally {
            DatabaseConfig.releaseConnection(conn);
        }
    }
}