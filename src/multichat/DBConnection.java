package multichat;
import java.sql.*;

public class DBConnection {
    // Database configuration - support both SQL Server and PostgreSQL
    private static String DB_HOST = System.getenv("DB_HOST") != null ? System.getenv("DB_HOST") : "localhost";
    private static String DB_PORT = System.getenv("DB_PORT") != null ? System.getenv("DB_PORT") : "5432";
    private static String DB_NAME = System.getenv("DB_NAME") != null ? System.getenv("DB_NAME") : "multichat";
    private static String DB_USER = System.getenv("DB_USER") != null ? System.getenv("DB_USER") : "multichat";
    private static String DB_PASS = System.getenv("DB_PASS") != null ? System.getenv("DB_PASS") : "multichat123";
    private static String DB_TYPE = System.getenv("DB_TYPE") != null ? System.getenv("DB_TYPE") : "postgresql"; // 'postgresql' or 'sqlserver'
    
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
        try {
            Connection conn = null;
            String url = "";
            
            if ("postgresql".equalsIgnoreCase(DB_TYPE)) {
                // PostgreSQL connection
                Class.forName("org.postgresql.Driver");
                url = "jdbc:postgresql://" + DB_HOST + ":" + DB_PORT + "/" + DB_NAME;
                System.out.println("[DB] Connecting to PostgreSQL: " + url.replace(DB_PASS, "***"));
                conn = DriverManager.getConnection(url, DB_USER, DB_PASS);
            } else {
                // SQL Server connection (legacy)
                Class.forName("com.microsoft.sqlserver.jdbc.SQLServerDriver");
                url = "jdbc:sqlserver://" + DB_HOST + ":" + DB_PORT + ";databaseName=" + DB_NAME + ";encrypt=false;trustServerCertificate=true";
                System.out.println("[DB] Connecting to SQL Server: " + url.replace(DB_PASS, "***"));
                conn = DriverManager.getConnection(url, DB_USER, DB_PASS);
            }
            
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