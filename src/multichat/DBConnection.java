package multichat;
import java.sql.*;

public class DBConnection {
    // Các thông số có thể thay đổi
    private static String DB_HOST = System.getenv("DB_HOST") != null ? System.getenv("DB_HOST") : "LEPS";
    private static String DB_PORT = System.getenv("DB_PORT") != null ? System.getenv("DB_PORT") : "1433";
    private static String DB_NAME = System.getenv("DB_NAME") != null ? System.getenv("DB_NAME") : "multichat";
    private static String DB_USER = System.getenv("DB_USER") != null ? System.getenv("DB_USER") : "sa";
    private static String DB_PASS = System.getenv("DB_PASS") != null ? System.getenv("DB_PASS") : "12345";
    
    // Hàm để thay đổi cấu hình database (khi chạy trên máy khác)
    public static void setDatabaseConfig(String host, String port, String database, String user, String password) {
        DB_HOST = host;
        DB_PORT = port;
        DB_NAME = database;
        DB_USER = user;
        DB_PASS = password;
        System.out.println("[DB CONFIG] Host: " + host + ", Port: " + port + ", Database: " + database);
    }
    
    public static Connection getConnection() {
        try {
            // Nạp Driver JDBC của Microsoft cho SQL Server
            Class.forName("com.microsoft.sqlserver.jdbc.SQLServerDriver");
            
            // Xây dựng chuỗi kết nối động
            String url = "jdbc:sqlserver://" + DB_HOST + ":" + DB_PORT + ";databaseName=" + DB_NAME + ";encrypt=false;trustServerCertificate=true";
            
            System.out.println("[DB] Đang kết nối: " + url.replace(DB_PASS, "***"));
            Connection conn = DriverManager.getConnection(url, DB_USER, DB_PASS);
            System.out.println("[DB] Kết nối thành công!");
            return conn;
        } catch (Exception e) {
            System.err.println("[ERROR] Lỗi kết nối SQL Server: " + e.getMessage());
            System.err.println("[ERROR] Đang cố kết nối: " + DB_HOST + ":" + DB_PORT);
            e.printStackTrace();
            return null;
        }
    }
}