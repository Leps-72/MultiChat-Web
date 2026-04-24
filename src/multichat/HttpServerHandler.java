package multichat;

import com.sun.net.httpserver.*;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.sql.*;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Xử lý toàn bộ HTTP requests cho Web Admin và Web User.
 * Sử dụng com.sun.net.httpserver (built-in JDK, không cần thư viện ngoài).
 */
public class HttpServerHandler {

    private final MultiChatServer server;

    public HttpServerHandler(MultiChatServer server) {
        this.server = server;
    }

    /** Đăng ký tất cả các route vào HttpServer */
    public void registerRoutes(HttpServer httpServer) {
        httpServer.createContext("/", this::handleRoot);
        httpServer.createContext("/login", this::handleLoginPage);
        httpServer.createContext("/logout", this::handleLogout);
        httpServer.createContext("/admin", this::handleAdminPage);
        httpServer.createContext("/chat", this::handleChatPage);

        // Static files (CSS, JS, images)
        httpServer.createContext("/static/", this::handleStatic);

        // REST API
        httpServer.createContext("/api/login", this::apiLogin);
        httpServer.createContext("/api/register", this::apiRegister);
        httpServer.createContext("/api/me", this::apiMe);
        httpServer.createContext("/api/rooms", this::apiRooms);
        httpServer.createContext("/api/users", this::apiUsers);
        httpServer.createContext("/api/logs", this::apiLogs);
        httpServer.createContext("/api/stats", this::apiStats);
        httpServer.createContext("/api/rooms/delete", this::apiDeleteRoom);
        httpServer.createContext("/api/rooms/create", this::apiCreateRoom);
        httpServer.createContext("/api/users/kick", this::apiKickUser);
        httpServer.createContext("/api/chat/history", this::apiChatHistory);
        httpServer.createContext("/api/chat/poll", this::apiChatPoll);
        httpServer.createContext("/api/chat/send", this::apiChatSend);
        httpServer.createContext("/api/chat/members", this::apiChatMembers);
        httpServer.createContext("/api/chat/join", this::apiChatJoin);
        httpServer.createContext("/api/chat/leave", this::apiChatLeave);
    }

    // ===== PAGE HANDLERS =====

    private void handleRoot(HttpExchange ex) throws IOException {
        SessionManager.SessionInfo si = getSession(ex);
        if (si == null) {
            redirect(ex, "/login");
        } else if ("admin".equals(si.role)) {
            redirect(ex, "/admin");
        } else {
            redirect(ex, "/chat");
        }
    }

    private void handleLoginPage(HttpExchange ex) throws IOException {
        if ("GET".equals(ex.getRequestMethod())) {
            SessionManager.SessionInfo si = getSession(ex);
            if (si != null) {
                redirect(ex, "admin".equals(si.role) ? "/admin" : "/chat");
                return;
            }
            serveHtmlFile(ex, "login.html");
        } else {
            sendText(ex, 405, "Method Not Allowed");
        }
    }

    private void handleAdminPage(HttpExchange ex) throws IOException {
        SessionManager.SessionInfo si = getSession(ex);
        if (si == null) { redirect(ex, "/login"); return; }
        if (!"admin".equals(si.role)) { redirect(ex, "/chat"); return; }
        serveHtmlFile(ex, "admin.html");
    }

    private void handleChatPage(HttpExchange ex) throws IOException {
        SessionManager.SessionInfo si = getSession(ex);
        if (si == null) { redirect(ex, "/login"); return; }
        serveHtmlFile(ex, "chat.html");
    }

    private void handleLogout(HttpExchange ex) throws IOException {
        String cookie = ex.getRequestHeaders().getFirst("Cookie");
        String token = SessionManager.extractToken(cookie);
        if (token != null) SessionManager.removeSession(token);
        ex.getResponseHeaders().add("Set-Cookie", "SESSION_ID=; Max-Age=0; Path=/");
        redirect(ex, "/login");
    }

    private void handleStatic(HttpExchange ex) throws IOException {
        String path = ex.getRequestURI().getPath().replace("/static/", "");
        String basePath = System.getProperty("app.webDir", "web");
        File file = new File(basePath + File.separator + path);
        if (!file.exists() || !file.isFile()) { sendText(ex, 404, "Not Found"); return; }
        String contentType = getContentType(path);
        byte[] bytes = Files.readAllBytes(file.toPath());
        ex.getResponseHeaders().add("Content-Type", contentType);
        ex.sendResponseHeaders(200, bytes.length);
        ex.getResponseBody().write(bytes);
        ex.getResponseBody().close();
    }

    // ===== API HANDLERS =====

    private void apiMe(HttpExchange ex) throws IOException {
        SessionManager.SessionInfo si = getSession(ex);
        if (si == null) { sendJson(ex, 401, "{\"error\":\"Chưa đăng nhập\"}"); return; }
        sendJson(ex, 200, "{\"username\":\"" + escapeJson(si.username) + "\",\"role\":\"" + si.role + "\"}");
    }

    private void apiLogin(HttpExchange ex) throws IOException {
        if (!"POST".equals(ex.getRequestMethod())) { sendText(ex, 405, "Method Not Allowed"); return; }
        Map<String, String> body = parseFormBody(ex);
        String username = body.getOrDefault("username", "").trim();
        String password = body.getOrDefault("password", "").trim();

        try (Connection conn = DBConnection.getConnection()) {
            if (conn == null) { sendJson(ex, 500, "{\"error\":\"Không thể kết nối Database\"}"); return; }
            PreparedStatement ps = conn.prepareStatement("SELECT role FROM users WHERE username=? AND password=?");
            ps.setString(1, username); ps.setString(2, password);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) {
                String role = rs.getString("role");
                String token = SessionManager.createSession(username, role);
                ex.getResponseHeaders().add("Set-Cookie", "SESSION_ID=" + token + "; Path=/; HttpOnly");
                sendJson(ex, 200, "{\"success\":true,\"role\":\"" + role + "\",\"username\":\"" + username + "\"}");
            } else {
                sendJson(ex, 401, "{\"success\":false,\"error\":\"Sai tên đăng nhập hoặc mật khẩu!\"}");
            }
        } catch (Exception e) {
            sendJson(ex, 500, "{\"error\":\"" + escapeJson(e.getMessage()) + "\"}");
        }
    }

    private void apiRegister(HttpExchange ex) throws IOException {
        if (!"POST".equals(ex.getRequestMethod())) { sendText(ex, 405, "Method Not Allowed"); return; }
        Map<String, String> body = parseFormBody(ex);
        String username = body.getOrDefault("username", "").trim();
        String password = body.getOrDefault("password", "").trim();
        if (username.isEmpty() || password.isEmpty()) {
            sendJson(ex, 400, "{\"success\":false,\"error\":\"Tên đăng nhập và mật khẩu không được rỗng!\"}"); return;
        }
        try (Connection conn = DBConnection.getConnection()) {
            if (conn == null) { sendJson(ex, 500, "{\"error\":\"Không thể kết nối Database\"}"); return; }
            PreparedStatement ps = conn.prepareStatement("INSERT INTO users (username, password, role) VALUES (?, ?, 'user')");
            ps.setString(1, username); ps.setString(2, password);
            ps.executeUpdate();
            sendJson(ex, 200, "{\"success\":true,\"message\":\"Đăng ký thành công!\"}");
        } catch (SQLException e) {
            if (e.getMessage() != null && (e.getMessage().contains("UNIQUE") || e.getMessage().contains("duplicate"))) {
                sendJson(ex, 409, "{\"success\":false,\"error\":\"Tên đăng nhập đã tồn tại!\"}");
            } else {
                sendJson(ex, 500, "{\"success\":false,\"error\":\"" + escapeJson(e.getMessage()) + "\"}");
            }
        }
    }

    private void apiRooms(HttpExchange ex) throws IOException {
        StringBuilder sb = new StringBuilder("[");
        try (Connection conn = DBConnection.getConnection()) {
            if (conn != null) {
                ResultSet rs = conn.createStatement().executeQuery("SELECT room_name, host_name FROM rooms ORDER BY room_name");
                boolean first = true;
                while (rs.next()) {
                    if (!first) sb.append(",");
                    first = false;
                    String rName = escapeJson(rs.getString("room_name"));
                    String host = escapeJson(rs.getString("host_name") != null ? rs.getString("host_name") : "");
                    // Đếm user online trong phòng
                    String currentRoomName = rs.getString("room_name");
                    Room room = MultiChatServer.rooms.get(currentRoomName);
                    int onlineCount = room != null ? room.clients.size() : 0;
                    
                    // Thêm số lượng Web Users đang online trong phòng này
                    long now = System.currentTimeMillis();
                    for (SessionManager.SessionInfo session : SessionManager.getAllSessions()) {
                        if (currentRoomName.equals(session.currentRoom) && (now - session.lastPollTime < 5000)) {
                            onlineCount++;
                        }
                    }
                    sb.append("{\"name\":\"").append(rName)
                      .append("\",\"host\":\"").append(host)
                      .append("\",\"online\":").append(onlineCount).append("}");
                }
            }
        } catch (Exception e) { e.printStackTrace(); }
        sb.append("]");
        sendJson(ex, 200, sb.toString());
    }

    private void apiUsers(HttpExchange ex) throws IOException {
        StringBuilder sb = new StringBuilder("[");
        boolean first = true;
        for (ClientHandler h : MultiChatServer.onlineUsers) {
            if (!first) sb.append(",");
            first = false;
            String uname = escapeJson(h.getUsername());
            String room = escapeJson(h.getCurrentRoom() != null ? h.getCurrentRoom() : "Đang chọn phòng");
            sb.append("{\"username\":\"").append(uname)
              .append("\",\"room\":\"").append(room).append("\"}");
        }
        
        long now = System.currentTimeMillis();
        for (SessionManager.SessionInfo si : SessionManager.getAllSessions()) {
            if (now - si.lastPollTime < 5000) { // Chỉ đếm web user active
                if (!first) sb.append(",");
                first = false;
                String uname = escapeJson(si.username + " (Web)");
                String room = escapeJson(si.currentRoom != null ? si.currentRoom : "Đang chọn phòng");
                sb.append("{\"username\":\"").append(uname)
                  .append("\",\"room\":\"").append(room).append("\"}");
            }
        }
        sb.append("]");
        sendJson(ex, 200, sb.toString());
    }

    private void apiLogs(HttpExchange ex) throws IOException {
        List<String> logs = server.getRecentLogs();
        StringBuilder sb = new StringBuilder("[");
        boolean first = true;
        for (String log : logs) {
            if (!first) sb.append(",");
            first = false;
            sb.append("\"").append(escapeJson(log)).append("\"");
        }
        sb.append("]");
        sendJson(ex, 200, sb.toString());
    }

    private void apiStats(HttpExchange ex) throws IOException {
        int totalUsers = 0, totalRooms = 0, totalMsgsToday = 0;
        try (Connection conn = DBConnection.getConnection()) {
            if (conn != null) {
                ResultSet rs1 = conn.createStatement().executeQuery("SELECT COUNT(*) FROM users");
                if (rs1.next()) totalUsers = rs1.getInt(1);
                ResultSet rs2 = conn.createStatement().executeQuery("SELECT COUNT(*) FROM rooms");
                if (rs2.next()) totalRooms = rs2.getInt(1);
                ResultSet rs3 = conn.createStatement().executeQuery("SELECT COUNT(*) FROM chat_logs WHERE CAST(sent_at AS DATE) = CURRENT_DATE");
                if (rs3.next()) totalMsgsToday = rs3.getInt(1);
            }
        } catch (Exception e) { e.printStackTrace(); }
        int onlineNow = MultiChatServer.onlineUsers.size();
        long now = System.currentTimeMillis();
        for (SessionManager.SessionInfo si : SessionManager.getAllSessions()) {
            if (now - si.lastPollTime < 5000) {
                onlineNow++;
            }
        }
        sendJson(ex, 200, "{\"totalUsers\":" + totalUsers + ",\"totalRooms\":" + totalRooms
                + ",\"totalMsgsToday\":" + totalMsgsToday + ",\"onlineNow\":" + onlineNow + "}");
    }

    private void apiDeleteRoom(HttpExchange ex) throws IOException {
        if (!requireAdmin(ex)) return;
        Map<String, String> body = parseFormBody(ex);
        String roomName = body.getOrDefault("room", "");
        if (roomName.isEmpty()) { sendJson(ex, 400, "{\"error\":\"Thiếu tên phòng\"}"); return; }
        try (Connection conn = DBConnection.getConnection()) {
            if (conn == null) { sendJson(ex, 500, "{\"error\":\"DB Error\"}"); return; }
            PreparedStatement ps = conn.prepareStatement("DELETE FROM rooms WHERE room_name=?");
            ps.setString(1, roomName); ps.executeUpdate();
        } catch (Exception e) { sendJson(ex, 500, "{\"error\":\"" + escapeJson(e.getMessage()) + "\"}"); return; }
        server.loadRoomsFromDB();
        server.broadcastRoomList();
        sendJson(ex, 200, "{\"success\":true}");
    }

    private void apiCreateRoom(HttpExchange ex) throws IOException {
        SessionManager.SessionInfo si = getSession(ex);
        if (si == null) { sendJson(ex, 401, "{\"error\":\"Chưa đăng nhập\"}"); return; }
        Map<String, String> body = parseFormBody(ex);
        String roomName = body.getOrDefault("room", "").trim();
        if (roomName.isEmpty()) { sendJson(ex, 400, "{\"error\":\"Tên phòng rỗng\"}"); return; }
        try (Connection conn = DBConnection.getConnection()) {
            if (conn == null) { sendJson(ex, 500, "{\"error\":\"DB Error\"}"); return; }
            PreparedStatement ps = conn.prepareStatement("INSERT INTO rooms(room_name, host_name) VALUES(?,?)");
            ps.setString(1, roomName); ps.setString(2, si.username);
            ps.executeUpdate();
        } catch (SQLException e) {
            if (e.getMessage() != null && (e.getMessage().contains("UNIQUE") || e.getMessage().contains("PK"))) {
                sendJson(ex, 409, "{\"success\":false,\"error\":\"Tên phòng đã tồn tại!\"}"); return;
            }
            sendJson(ex, 500, "{\"error\":\"" + escapeJson(e.getMessage()) + "\"}"); return;
        }
        server.loadRoomsFromDB();
        server.broadcastRoomList();
        sendJson(ex, 200, "{\"success\":true}");
    }

    private void apiKickUser(HttpExchange ex) throws IOException {
        if (!requireAdmin(ex)) return;
        Map<String, String> body = parseFormBody(ex);
        String targetName = body.getOrDefault("username", "");
        for (ClientHandler h : MultiChatServer.onlineUsers) {
            if (h.getUsername().equals(targetName)) {
                h.kickByAdmin();
                server.log("[ADMIN] Đã kick user: " + targetName);
                sendJson(ex, 200, "{\"success\":true}");
                return;
            }
        }
        sendJson(ex, 404, "{\"error\":\"Không tìm thấy user online\"}");
    }

    /**
     * API Polling cho Web User Chat (thay thế kiến trúc query DB mỗi giây).
     *
     * Tham số: ?room=Tên_phòng&since=N
     * - since=0 (hoặc không truyền): trả về toàn bộ buffer (50 tin gần nhất)
     * - since=N: chỉ trả về các tin có seq >= N (các tin mới)
     *
     * Ư u điểm so với query DB:
     *   - Không tốn kết nối DB, không chậm vì I/O
     *   - Web User và TCP client cùng nhìn thấy tin nhắn trong buffer
     */
    private void apiChatPoll(HttpExchange ex) throws IOException {
        SessionManager.SessionInfo si = getSession(ex);
        if (si == null) { sendJson(ex, 401, "{\"error\":\"Chưa đăng nhập\"}"); return; }

        String query = ex.getRequestURI().getQuery();
        String room  = getQueryParam(query, "room");
        String sinceStr = getQueryParam(query, "since");
        if (room == null || room.isEmpty()) { sendJson(ex, 400, "{\"error\":\"Thiếu room\"}"); return; }

        si.currentRoom = room;
        si.lastPollTime = System.currentTimeMillis();

        int sinceSeq = 0;
        try { if (sinceStr != null) sinceSeq = Integer.parseInt(sinceStr); } catch (NumberFormatException ignored) {}

        Room r = MultiChatServer.rooms.get(room);
        if (r == null) {
            // Phòng chưa tồn tại trong bộ nhớ (có thể mới tạo)
            sendJson(ex, 200, "{\"msgs\":[],\"nextSeq\":0}");
            return;
        }

        int nextSeq = r.getCurrentSeq(); // seq hiện tại để client gửi lần sau
        List<Room.MsgEntry> entries = sinceSeq == 0
                ? r.getMsgsSince(Math.max(0, nextSeq - 50)) // lần đầu: lấy 50 tin gần nhất
                : r.getMsgsSince(sinceSeq);                  // lần sau: chỉ lấy tin mới

        StringBuilder sb = new StringBuilder("{\"msgs\":[");
        boolean first = true;
        for (Room.MsgEntry e : entries) {
            if (!first) sb.append(",");
            first = false;
            sb.append("{\"seq\":").append(e.seq)
              .append(",\"sender\":\"").append(escapeJson(e.sender)).append("\"")
              .append(",\"text\":\"").append(escapeJson(e.text)).append("\"")
              .append(",\"time\":\"").append(escapeJson(e.time)).append("\"}");
        }
        sb.append("],\"nextSeq\":").append(nextSeq).append("}");
        sendJson(ex, 200, sb.toString());
    }

    private void apiChatSend(HttpExchange ex) throws IOException {
        SessionManager.SessionInfo si = getSession(ex);
        if (si == null) { sendJson(ex, 401, "{\"error\":\"Chưa đăng nhập\"}"); return; }
        Map<String, String> body = parseFormBody(ex);
        String room    = body.getOrDefault("room", "").trim();
        String message = body.getOrDefault("message", "").trim();
        if (room.isEmpty() || message.isEmpty()) { sendJson(ex, 400, "{\"error\":\"Thiếu room hoặc message\"}"); return; }

        // 1. Lưu vào DB (ở trữ lâu dài)
        try (Connection conn = DBConnection.getConnection()) {
            if (conn == null) { sendJson(ex, 500, "{\"error\":\"DB Error\"}"); return; }
            PreparedStatement ps = conn.prepareStatement(
                    "INSERT INTO chat_logs(username, room_name, message) VALUES(?,?,?)");
            ps.setString(1, si.username); ps.setString(2, room); ps.setString(3, message);
            ps.executeUpdate();
        } catch (Exception e) { sendJson(ex, 500, "{\"error\":\"" + escapeJson(e.getMessage()) + "\"}"); return; }

        Room r = MultiChatServer.rooms.get(room);
        if (r != null) {
            String senderTag = "[Web] " + si.username;

            // 2. Thêm vào Message Buffer — Web User poll sẽ thấy tin này ngay lập tức
            r.addMessage(senderTag, message);

            // 3. Đẩy tới TCP client đang kết nối socket trong phòng
            for (ClientHandler h : r.clients) {
                h.sendMessage("MSG|" + senderTag + ": " + message);
            }
        }
        server.log("{" + room + "} [Web] " + si.username + ": " + message);
        sendJson(ex, 200, "{\"success\":true}");
    }

    private void apiChatMembers(HttpExchange ex) throws IOException {
        SessionManager.SessionInfo si = getSession(ex);
        if (si == null) { sendJson(ex, 401, "{\"error\":\"Chưa đăng nhập\"}"); return; }
        String query = ex.getRequestURI().getQuery();
        String room = getQueryParam(query, "room");
        if (room == null) { sendJson(ex, 400, "{\"error\":\"Thiếu room\"}"); return; }

        StringBuilder sb = new StringBuilder("[");
        Room r = MultiChatServer.rooms.get(room);
        if (r != null) {
            boolean first = true;
            for (ClientHandler h : r.clients) {
                if (!first) sb.append(",");
                first = false;
                sb.append("\"").append(escapeJson(h.getUsername() + " (App)")).append("\"");
            }
            
            long now = System.currentTimeMillis();
            for (SessionManager.SessionInfo si : SessionManager.getAllSessions()) {
                if (room.equals(si.currentRoom) && (now - si.lastPollTime < 5000)) {
                    if (!first) sb.append(",");
                    first = false;
                    sb.append("\"").append(escapeJson(si.username + " (Web)")).append("\"");
                }
            }
        }
        sb.append("]");
        sendJson(ex, 200, sb.toString());
    }

    private void apiChatJoin(HttpExchange ex) throws IOException {
        SessionManager.SessionInfo si = getSession(ex);
        if (si == null) { sendJson(ex, 401, "{\"error\":\"Chưa đăng nhập\"}"); return; }
        Map<String, String> body = parseFormBody(ex);
        String room = body.getOrDefault("room", "").trim();
        server.log("[Web] " + si.username + " đã vào phòng: " + room);
        sendJson(ex, 200, "{\"success\":true}");
    }

    private void apiChatLeave(HttpExchange ex) throws IOException {
        SessionManager.SessionInfo si = getSession(ex);
        if (si == null) { sendJson(ex, 401, "{\"error\":\"Chưa đăng nhập\"}"); return; }
        Map<String, String> body = parseFormBody(ex);
        String room = body.getOrDefault("room", "").trim();
        si.currentRoom = null;
        server.log("[Web] " + si.username + " đã rời phòng: " + room);
        sendJson(ex, 200, "{\"success\":true}");
    }

    private void apiChatHistory(HttpExchange ex) throws IOException {
        SessionManager.SessionInfo si = getSession(ex);
        if (si == null) { sendJson(ex, 401, "{\"error\":\"Chưa đăng nhập\"}"); return; }

        String query = ex.getRequestURI().getQuery();
        String room = getQueryParam(query, "room");
        if (room == null || room.isEmpty()) { sendJson(ex, 400, "{\"error\":\"Thiếu room\"}"); return; }

        StringBuilder sb = new StringBuilder("[");
        try (Connection conn = DBConnection.getConnection()) {
            if (conn != null) {
                String sql = "SELECT TOP 50 username, message, sent_at FROM chat_logs " +
                             "WHERE room_name = ? ORDER BY sent_at DESC";
                PreparedStatement ps = conn.prepareStatement(sql);
                ps.setString(1, room);
                ResultSet rs = ps.executeQuery();
                List<String[]> rows = new java.util.ArrayList<>();
                while (rs.next()) {
                    rows.add(new String[]{
                        rs.getString("username"),
                        rs.getString("message"),
                        rs.getString("sent_at")
                    });
                }
                // Đảo ngược để hiển thị tin cũ trước, mới sau
                java.util.Collections.reverse(rows);
                boolean first = true;
                for (String[] row : rows) {
                    if (!first) sb.append(",");
                    first = false;
                    sb.append("{\"sender\":\"").append(escapeJson(row[0])).append("\"")
                      .append(",\"text\":\"").append(escapeJson(row[1])).append("\"")
                      .append(",\"time\":\"").append(escapeJson(row[2])).append("\"}");
                }
            }
        } catch (Exception e) {
            sendJson(ex, 500, "{\"error\":\"" + escapeJson(e.getMessage()) + "\"}");
            return;
        }
        sb.append("]");
        sendJson(ex, 200, sb.toString());
    }

    // ===== HELPERS =====

    private SessionManager.SessionInfo getSession(HttpExchange ex) {
        String cookie = ex.getRequestHeaders().getFirst("Cookie");
        String token = SessionManager.extractToken(cookie);
        return SessionManager.getSession(token);
    }

    /** Yêu cầu role admin, tự trả 401 nếu không đủ quyền */
    private boolean requireAdmin(HttpExchange ex) throws IOException {
        SessionManager.SessionInfo si = getSession(ex);
        if (si == null) { sendJson(ex, 401, "{\"error\":\"Chưa đăng nhập\"}"); return false; }
        if (!"admin".equals(si.role)) { sendJson(ex, 403, "{\"error\":\"Không có quyền Admin\"}"); return false; }
        return true;
    }

    private void serveHtmlFile(HttpExchange ex, String filename) throws IOException {
        String basePath = System.getProperty("app.webDir", "web");
        File file = new File(basePath + File.separator + filename);
        if (!file.exists()) {
            sendText(ex, 404, "404 - Không tìm thấy trang: " + filename);
            return;
        }
        byte[] bytes = Files.readAllBytes(file.toPath());
        ex.getResponseHeaders().add("Content-Type", "text/html; charset=UTF-8");
        ex.sendResponseHeaders(200, bytes.length);
        ex.getResponseBody().write(bytes);
        ex.getResponseBody().close();
    }

    private void redirect(HttpExchange ex, String location) throws IOException {
        ex.getResponseHeaders().add("Location", location);
        ex.sendResponseHeaders(302, -1);
        ex.getResponseBody().close();
    }

    private void sendJson(HttpExchange ex, int status, String json) throws IOException {
        byte[] bytes = json.getBytes(StandardCharsets.UTF_8);
        ex.getResponseHeaders().add("Content-Type", "application/json; charset=UTF-8");
        ex.getResponseHeaders().add("Access-Control-Allow-Origin", "*");
        ex.sendResponseHeaders(status, bytes.length);
        ex.getResponseBody().write(bytes);
        ex.getResponseBody().close();
    }

    private void sendText(HttpExchange ex, int status, String text) throws IOException {
        byte[] bytes = text.getBytes(StandardCharsets.UTF_8);
        ex.getResponseHeaders().add("Content-Type", "text/plain; charset=UTF-8");
        ex.sendResponseHeaders(status, bytes.length);
        ex.getResponseBody().write(bytes);
        ex.getResponseBody().close();
    }

    private Map<String, String> parseFormBody(HttpExchange ex) throws IOException {
        String body = new String(ex.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
        Map<String, String> result = new HashMap<>();
        if (body.isEmpty()) return result;
        // Thử parse JSON
        if (body.trim().startsWith("{")) {
            body = body.replaceAll("[{}\"]", "");
            for (String pair : body.split(",")) {
                String[] kv = pair.split(":", 2);
                if (kv.length == 2) result.put(kv[0].trim(), kv[1].trim());
            }
        } else {
            // Form URL-encoded
            for (String pair : body.split("&")) {
                String[] kv = pair.split("=", 2);
                if (kv.length == 2) {
                    result.put(URLDecoder.decode(kv[0], StandardCharsets.UTF_8),
                               URLDecoder.decode(kv[1], StandardCharsets.UTF_8));
                }
            }
        }
        return result;
    }

    private String getQueryParam(String query, String key) {
        if (query == null) return null;
        for (String pair : query.split("&")) {
            String[] kv = pair.split("=", 2);
            if (kv.length == 2 && kv[0].equals(key)) {
                try { return URLDecoder.decode(kv[1], "UTF-8"); } catch (Exception e) { return kv[1]; }
            }
        }
        return null;
    }

    private String escapeJson(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\").replace("\"", "\\\"")
                .replace("\n", "\\n").replace("\r", "\\r").replace("\t", "\\t");
    }

    private String getContentType(String filename) {
        if (filename.endsWith(".css")) return "text/css; charset=UTF-8";
        if (filename.endsWith(".js")) return "application/javascript; charset=UTF-8";
        if (filename.endsWith(".png")) return "image/png";
        if (filename.endsWith(".jpg") || filename.endsWith(".jpeg")) return "image/jpeg";
        if (filename.endsWith(".ico")) return "image/x-icon";
        return "text/plain; charset=UTF-8";
    }
}
