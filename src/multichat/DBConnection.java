package multichat;

import java.net.URI;
import java.sql.*;

public class DBConnection {
    // Database configuration - support both SQL Server and PostgreSQL
    private static String DB_HOST = System.getenv("DB_HOST");
    private static String DB_PORT = System.getenv("DB_PORT");
    private static String DB_NAME = System.getenv("DB_NAME");
    private static String DB_USER = System.getenv("DB_USER");
    private static String DB_PASS = System.getenv("DB_PASS");
    private static String DB_TYPE = System.getenv("DB_TYPE") != null ? System.getenv("DB_TYPE") : "postgresql";

    static {
        // Automatically parse DATABASE_URL or INTERNAL_DATABASE_URL if present (Render standard)
        String databaseUrl = System.getenv("DATABASE_URL");
        if (databaseUrl == null || databaseUrl.trim().isEmpty()) {
            databaseUrl = System.getenv("INTERNAL_DATABASE_URL");
        }
        if (databaseUrl != null && !databaseUrl.trim().isEmpty()) {
            try {
                String cleanUrl = databaseUrl.replace("postgresql://", "http://").replace("postgres://", "http://");
                URI uri = new URI(cleanUrl);
                if (DB_HOST == null || DB_HOST.trim().isEmpty()) DB_HOST = uri.getHost();
                if (DB_PORT == null || DB_PORT.trim().isEmpty()) {
                    DB_PORT = uri.getPort() > 0 ? String.valueOf(uri.getPort()) : "5432";
                }
                if (DB_NAME == null || DB_NAME.trim().isEmpty()) {
                    String path = uri.getPath();
                    if (path != null && path.startsWith("/")) DB_NAME = path.substring(1);
                }
                if (uri.getUserInfo() != null) {
                    String[] userInfo = uri.getUserInfo().split(":");
                    if (DB_USER == null || DB_USER.trim().isEmpty()) DB_USER = userInfo[0];
                    if (userInfo.length > 1 && (DB_PASS == null || DB_PASS.trim().isEmpty())) DB_PASS = userInfo[1];
                }
                DB_TYPE = "postgresql";
            } catch (Exception e) {
                System.err.println("[DB CONFIG] Error parsing DATABASE_URL: " + e.getMessage());
            }
        }

        // Apply defaults if still unconfigured
        if (DB_HOST == null || DB_HOST.trim().isEmpty()) DB_HOST = "localhost";
        if (DB_PORT == null || DB_PORT.trim().isEmpty()) DB_PORT = "5432";
        if (DB_NAME == null || DB_NAME.trim().isEmpty()) DB_NAME = "multichat";
        if (DB_USER == null || DB_USER.trim().isEmpty()) DB_USER = "multichat";
        if (DB_PASS == null || DB_PASS.trim().isEmpty()) DB_PASS = "multichat123";
    }

    // Function to change database config (when running on different machines)
    public static void setDatabaseConfig(String host, String port, String database, String user, String password) {
        DB_HOST = host;
        DB_PORT = port;
        DB_NAME = database;
        DB_USER = user;
        DB_PASS = password;
        System.out.println("[DB CONFIG] Host: " + host + ", Port: " + port + ", Database: " + database);
    }

    public static void setDatabaseType(String type) {
        DB_TYPE = type; // 'postgresql' or 'sqlserver'
        System.out.println("[DB TYPE] Set to: " + type);
    }

    public static Connection getConnection() {
        if ("postgresql".equalsIgnoreCase(DB_TYPE)) {
            return getPostgreSQLConnection();
        } else {
            return getSQLServerConnection();
        }
    }

    private static Connection getPostgreSQLConnection() {
        try {
            Class.forName("org.postgresql.Driver");
        } catch (ClassNotFoundException e) {
            System.err.println("[ERROR] PostgreSQL Driver not found: " + e.getMessage());
            return null;
        }

        String envSslMode = System.getenv("DB_SSL_MODE");
        java.util.List<String> modesToTry = new java.util.ArrayList<>();

        if (envSslMode != null && !envSslMode.trim().isEmpty()) {
            modesToTry.add(envSslMode.trim());
        }
        
        // Try prefer and disable as resilient fallbacks
        if (!modesToTry.contains("prefer")) modesToTry.add("prefer");
        if (!modesToTry.contains("disable")) modesToTry.add("disable");
        if (!modesToTry.contains("require")) modesToTry.add("require");

        Exception lastException = null;
        for (String sslMode : modesToTry) {
            String url;
            if (!"disable".equalsIgnoreCase(sslMode)) {
                url = "jdbc:postgresql://" + DB_HOST + ":" + DB_PORT + "/" + DB_NAME + "?sslmode=" + sslMode;
            } else {
                url = "jdbc:postgresql://" + DB_HOST + ":" + DB_PORT + "/" + DB_NAME;
            }

            try {
                System.out.println("[DB] Connecting to PostgreSQL (sslmode=" + sslMode + "): " + url.replace(DB_PASS != null ? DB_PASS : "", "***"));
                Connection conn = DriverManager.getConnection(url, DB_USER, DB_PASS);
                if (conn != null) {
                    System.out.println("[DB] Connection successful using sslmode=" + sslMode + "!");
                    return conn;
                }
            } catch (Exception e) {
                lastException = e;
                System.err.println("[DB WARNING] Connection attempt failed with sslmode=" + sslMode + ": " + e.getMessage());
            }
        }

        System.err.println("[ERROR] Database connection error: All SSL/connection attempts failed.");
        System.err.println("[ERROR] DB_TYPE: " + DB_TYPE + ", Host: " + DB_HOST + ":" + DB_PORT);
        if (lastException != null) {
            lastException.printStackTrace();
        }
        return null;
    }

    private static Connection getSQLServerConnection() {
        try {
            Class.forName("com.microsoft.sqlserver.jdbc.SQLServerDriver");
            String url = "jdbc:sqlserver://" + DB_HOST + ":" + DB_PORT + ";databaseName=" + DB_NAME + ";encrypt=false;trustServerCertificate=true";
            System.out.println("[DB] Connecting to SQL Server: " + url.replace(DB_PASS != null ? DB_PASS : "", "***"));
            Connection conn = DriverManager.getConnection(url, DB_USER, DB_PASS);
            System.out.println("[DB] Connection successful!");
            return conn;
        } catch (Exception e) {
            System.err.println("[ERROR] Database connection error: " + e.getMessage());
            System.err.println("[ERROR] DB_TYPE: " + DB_TYPE + ", Host: " + DB_HOST + ":" + DB_PORT);
            e.printStackTrace();
            return null;
        }
    }
}