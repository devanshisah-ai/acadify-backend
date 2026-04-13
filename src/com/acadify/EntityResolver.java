package com.acadify;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
public class EntityResolver {
    public static int resolveStudentId(int userId) {
        Connection conn = null;
        try {
            conn = DatabaseConfig.getConnection();
            PreparedStatement ps = conn.prepareStatement(
                    "SELECT student_id FROM students WHERE user_id = ?");
            ps.setInt(1, userId);
            ResultSet rs = ps.executeQuery();
            int result = rs.next() ? rs.getInt("student_id") : -1;
            rs.close();
            ps.close();
            return result;
        } catch (SQLException e) {
            System.err.println("[EntityResolver] resolveStudentId error: " + e.getMessage());
            return -1;
        } finally {
            DatabaseConfig.releaseConnection(conn);
        }
    }
    public static int resolveTeacherId(int userId) {
        Connection conn = null;
        try {
            conn = DatabaseConfig.getConnection();
            PreparedStatement ps = conn.prepareStatement(
                    "SELECT teacher_id FROM teachers WHERE user_id = ?");
            ps.setInt(1, userId);
            ResultSet rs = ps.executeQuery();
            int result = rs.next() ? rs.getInt("teacher_id") : -1;
            rs.close();
            ps.close();
            return result;
        } catch (SQLException e) {
            System.err.println("[EntityResolver] resolveTeacherId error: " + e.getMessage());
            return -1;
        } finally {
            DatabaseConfig.releaseConnection(conn);
        }
    }
}
