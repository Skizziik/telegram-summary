package org.example;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;

public class Database {

    private static final String URL = "jdbc:sqlite:telegram.db";

    public static void init() throws Exception {

        Connection conn = DriverManager.getConnection(URL);

        Statement stmt = conn.createStatement();

        stmt.execute("""
                CREATE TABLE IF NOT EXISTS posts (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    channel TEXT,
                    text TEXT,
                    hash TEXT UNIQUE,
                    created_at DATETIME DEFAULT CURRENT_TIMESTAMP
                )
                """);

        conn.close();

        System.out.println("База данных готова");
    }

    public static void savePost(String channel,
                                String text,
                                String hash) throws Exception {

        Connection conn = DriverManager.getConnection(URL);

        var stmt = conn.prepareStatement("""
        INSERT OR IGNORE INTO posts
        (channel, text, hash)
        VALUES (?, ?, ?)
    """);

        stmt.setString(1, channel);
        stmt.setString(2, text);
        stmt.setString(3, hash);

        stmt.executeUpdate();

        conn.close();
    }

    public static void countPosts() throws Exception {

        Connection conn = DriverManager.getConnection(URL);

        var stmt = conn.createStatement();
        var rs = stmt.executeQuery("SELECT COUNT(*) FROM posts");

        if (rs.next()) {
            System.out.println("Записей в БД: " + rs.getInt(1));
        }

        conn.close();

    }

    public static void showLastPosts() throws Exception {

        Connection conn = DriverManager.getConnection(URL);

        var stmt = conn.createStatement();

        var rs = stmt.executeQuery("""
        SELECT channel, text
        FROM posts
        ORDER BY id DESC
        LIMIT 10
    """);

        while (rs.next()) {
            System.out.println("[" + rs.getString("channel") + "]");
            System.out.println(rs.getString("text"));
            System.out.println("----------------");
        }

        conn.close();
    }

    public static String getTodayPosts() throws Exception {

        Connection conn = DriverManager.getConnection(URL);

        var stmt = conn.createStatement();

        var rs = stmt.executeQuery("""
        SELECT channel, text
        FROM posts
        WHERE created_at >= datetime('now', '-1 day')
    """);

        StringBuilder sb = new StringBuilder();

        while (rs.next()) {
            sb.append("Канал: ")
                    .append(rs.getString("channel"))
                    .append("\n");

            sb.append(rs.getString("text"))
                    .append("\n\n");
        }

        conn.close();

        return sb.toString();
    }
}