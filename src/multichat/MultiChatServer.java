package multichat;

import com.sun.net.httpserver.HttpServer;
import java.net.*;
import java.sql.*;
import java.util.*;
import java.util.concurrent.*;

/**
 * Lớp đại diện cho một phòng chat trong bộ nhớ Server.
 *
 * Message Buffer: Lưu tin nhắn in-memory để Web User poll (không cần query DB mỗi giây).
 * Thread-safe: CopyOnWriteArrayList chịu được đọc/ghi đồng thời từ nhiều luồng.
 */
class Room {
    String name;

    // Danh sách TCP client đang kết nối socket trong phòng này
    java.util.List<ClientHandler> clients = new CopyOnWriteArrayList<>();

    // ----------------------------------------------------------------
    // Message Buffer — cho Web User polling
    // ----------------------------------------------------------------
    // Mỗi tin nhắn được đánh số thứ tự (seq) tăng dần.
    // Web User gửi seq cuối cùng họ đã nhận, server trả về tin mới hơn.
    // Không dùng synchronized vì CopyOnWriteArrayList thread-safe.
    private final java.util.List<MsgEntry> msgBuffer = new CopyOnWriteArrayList<>();
    private final java.util.concurrent.atomic.AtomicInteger seqCounter
            = new java.util.concurrent.atomic.AtomicInteger(0);

    // Giới hạn buffer — chỉ giữ 200 tin gần nhất
    private static final int MAX_BUFFER = 200;

    public Room(String name) { this.name = name; }

    /**
     * Thêm tin nhắn vào buffer. Gọi cả khi TCP client gửi lẫn khi Web User gửi.
     * @param sender Tên người gửi (username)
     * @param text   Nội dung tin nhắn
     * @return Tin nhắn đã format để broadcast qua TCP (prefix "MSG|")
     */
    public String addMessage(String sender, String text) {
        int seq = seqCounter.getAndIncrement();
        String time = new java.util.Date().toString().substring(11, 16); // HH:mm
        MsgEntry entry = new MsgEntry(seq, sender, text, time);
        msgBuffer.add(entry);
        // Cắt buffer nếu quá dài (thread-safe với CopyOnWriteArrayList)
        if (msgBuffer.size() > MAX_BUFFER) msgBuffer.remove(0);
        return "MSG|" + sender + ": " + text;
    }

    /**
     * Trả về danh sách tin nhắn có seq >= fromSeq (cho Web polling API).
     */
    public java.util.List<MsgEntry> getMsgsSince(int fromSeq) {
        java.util.List<MsgEntry> result = new java.util.ArrayList<>();
        for (MsgEntry e : msgBuffer) {
            if (e.seq >= fromSeq) result.add(e);
        }
        return result;
    }

    /** Sequence số lớn nhất hiện tại (Web User dùng lần đầu để lấy seq hiện tại) */
    public int getCurrentSeq() { return seqCounter.get(); }

    // ----------------------------------------------------------------
    // Inner class: một entry trong Message Buffer
    // ----------------------------------------------------------------
    public static class MsgEntry {
        public final int seq;
        public final String sender;
        public final String text;
        public final String time;

        public MsgEntry(int seq, String sender, String text, String time) {
            this.seq = seq; this.sender = sender; this.text = text; this.time = time;
        }
    }
}


/**
 * MultiChatServer — Phiên bản Web (không còn Swing GUI).
 *
 * Chạy song song 2 server trong cùng 1 process:
 *   - TCP Server  (port 5000) : phục vụ các Java Client kết nối Socket
 *   - HTTP Server (port 8080) : phục vụ Web Admin và Web User Chat
 */
public class MultiChatServer {
    private static final int TCP_PORT  = 5000;
    private static final int HTTP_PORT = 8080;

    // Dữ liệu chia sẻ giữa TCP Handler và HTTP Handler
    static Map<String, Room> rooms = new ConcurrentHashMap<>();
    static java.util.List<ClientHandler> onlineUsers = new CopyOnWriteArrayList<>();

    // Log hệ thống — CopyOnWriteArrayList thay vì ArrayDeque để thread-safe
    // (nhiều luồng TCP + HTTP đồng thời ghi log, không được dùng ArrayDeque)
    private final java.util.List<String> systemLogs = new CopyOnWriteArrayList<>();
    private static final int MAX_LOG_LINES = 200;

    // ===== ENTRY POINT =====

    public static void main(String[] args) {
        // Hỗ trợ tham số dòng lệnh: java MultiChatServer <host> [port] [db] [user] [pass]
        if (args.length >= 1) {
            String host     = args[0];
            String port     = args.length >= 2 ? args[1] : "1433";
            String database = args.length >= 3 ? args[2] : "multichat";
            String user     = args.length >= 4 ? args[3] : "sa";
            String password = args.length >= 5 ? args[4] : "12345";
            DBConnection.setDatabaseConfig(host, port, database, user, password);
            System.out.println("[STARTUP] Database: " + host + ":" + port + "/" + database);
        }

        MultiChatServer server = new MultiChatServer();
        server.start();
    }

    // ===== KHỞI ĐỘNG =====

    public void start() {
        // Thử kết nối DB, hỏi lại nếu thất bại
        configureDatabaseIfNeeded();

        // Tải danh sách phòng từ DB
        loadRoomsFromDB();

        // Khởi động HTTP Server (Web)
        startHttpServer();

        // Khởi động TCP Server (Socket)
        startTcpServer();
    }

    private void configureDatabaseIfNeeded() {
        try {
            Connection c = DBConnection.getConnection();
            if (c != null) { c.close(); log("[OK] Kết nối Database thành công!"); return; }
        } catch (Exception ignored) {}

        // Nếu không kết nối được, dùng Scanner để nhập từ console
        System.out.print("[CONFIG] Nhập địa chỉ SQL Server (Enter để dùng mặc định 'LEPS'): ");
        Scanner sc = new Scanner(System.in);
        String input = sc.nextLine().trim();
        if (!input.isEmpty()) {
            DBConnection.setDatabaseConfig(input, "1433", "multichat", "sa", "12345");
        }
    }

    // ===== HTTP SERVER (WEB) =====

    private void startHttpServer() {
        try {
            // Tự động tìm thư mục web/ tương đối với project
            if (System.getProperty("app.webDir") == null) {
                // Thử các vị trí phổ biến
                String[] candidates = { "web", "../web", "../../web", "MultiChat/web" };
                for (String candidate : candidates) {
                    java.io.File dir = new java.io.File(candidate);
                    if (dir.exists() && dir.isDirectory() && new java.io.File(dir, "login.html").exists()) {
                        System.setProperty("app.webDir", dir.getCanonicalPath());
                        log("[HTTP] Thư mục web: " + dir.getCanonicalPath());
                        break;
                    }
                }
                if (System.getProperty("app.webDir") == null) {
                    log("[WARNING] Không tìm thấy thư mục web/. Hãy chạy từ thư mục gốc dự án.");
                    System.setProperty("app.webDir", "web");
                }
            }

            HttpServer httpServer = HttpServer.create(new InetSocketAddress(HTTP_PORT), 0);
            HttpServerHandler handler = new HttpServerHandler(this);
            handler.registerRoutes(httpServer);
            httpServer.setExecutor(Executors.newFixedThreadPool(10));
            httpServer.start();
            log("[HTTP] Web server đang chạy tại: http://localhost:" + HTTP_PORT);
            System.out.println("========================================================");
            System.out.println("  🌐  Mở trình duyệt: http://localhost:" + HTTP_PORT);
            System.out.println("  👤  Admin: admin / 12345");
            System.out.println("========================================================");
        } catch (Exception e) {
            System.err.println("[ERROR] Không thể khởi động HTTP Server: " + e.getMessage());
            e.printStackTrace();
        }
    }

    // ===== TCP SERVER (SOCKET) =====

    private void startTcpServer() {
        try (ServerSocket ss = new ServerSocket(TCP_PORT)) {
            log("[TCP] Socket server đang lắng nghe tại cổng " + TCP_PORT);
            while (true) {
                Socket s = ss.accept();
                ClientHandler h = new ClientHandler(s, this);
                onlineUsers.add(h);
                new Thread(h).start();
            }
        } catch (Exception e) {
            System.err.println("[ERROR] TCP Server lỗi: " + e.getMessage());
            e.printStackTrace();
        }
    }

    // ===== PHÒNG CHAT =====

    /** Tải danh sách phòng từ Database */
    public void loadRoomsFromDB() {
        rooms.clear();
        try (Connection conn = DBConnection.getConnection()) {
            if (conn == null) return;
            ResultSet rs = conn.createStatement().executeQuery("SELECT room_name FROM rooms");
            while (rs.next()) {
                String name = rs.getString("room_name");
                rooms.put(name, new Room(name));
            }
            log("[HỆ THỐNG] Đã đồng bộ " + rooms.size() + " phòng từ Database.");
        } catch (Exception e) {
            log("[ERROR] loadRoomsFromDB: " + e.getMessage());
        }
    }

    /** Gửi danh sách phòng mới nhất tới tất cả TCP clients */
    public void broadcastRoomList() {
        String data = "ROOM_LIST|" + String.join(",", rooms.keySet());
        for (ClientHandler h : onlineUsers) h.sendMessage(data);
    }

    // ===== LOG =====

    /** Ghi log vào danh sách nội bộ — không cần synchronized vì CopyOnWriteArrayList thread-safe */
    public void log(String msg) {
        String entry = "[" + new java.util.Date().toString().substring(11, 19) + "] " + msg;
        System.out.println(entry);
        systemLogs.add(entry);
        // Cắt bớt nếu quá giới hạn (xóa dòng cũ nhất ở đầu list)
        if (systemLogs.size() > MAX_LOG_LINES) systemLogs.remove(0);
    }

    /** Trả về bản copy để tránh caller bị ảnh hưởng khi list thay đổi */
    public List<String> getRecentLogs() {
        return new ArrayList<>(systemLogs);
    }

    // ===== CẬP NHẬT UI (Giữ để ClientHandler gọi — không làm gì vì không có Swing) =====

    /** Phương thức tương thích ngược — ClientHandler vẫn gọi hàm này */
    public void updateUserUI() {
        // Không còn Swing — log thống kê vào console
        // Thông tin user online được lấy trực tiếp qua API /api/users
    }
}