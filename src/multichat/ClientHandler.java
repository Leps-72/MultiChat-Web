package multichat;

import java.io.*;
import java.net.*;
import java.sql.*;
import java.util.*;

public class ClientHandler implements Runnable {
    private Socket socket;
    private MultiChatServer server;
    private BufferedReader in;
    private PrintWriter out;
    private String username;
    private String currentRoom;

    public ClientHandler(Socket socket, MultiChatServer server) {
        this.socket = socket;
        this.server = server;
    }

    public String getUsername() { return username != null ? username : "Khách"; }
    public String getCurrentRoom() { return currentRoom; }

    // Kiểm tra quyền Chủ phòng từ SQL Server
    private boolean checkIsHost(String rName) {
        try (Connection conn = DBConnection.getConnection()) {
            if (conn == null) {
                System.err.println("[WARNING] Connection null - checkIsHost for room: " + rName);
                return false;
            }
            PreparedStatement ps = conn.prepareStatement("SELECT host_name FROM rooms WHERE room_name = ?");
            ps.setString(1, rName);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) return username.equals(rs.getString("host_name"));
        } catch (Exception e) { 
            System.err.println("[ERROR] checkIsHost: " + e.getMessage());
        }
        return false;
    }

    @Override
    public void run() {
        try {
            in = new BufferedReader(new InputStreamReader(socket.getInputStream(), "UTF-8"));
            out = new PrintWriter(new OutputStreamWriter(socket.getOutputStream(), "UTF-8"), true);

            String req;
            while ((req = in.readLine()) != null) {
                String[] p = req.split("\\|");
                switch (p[0]) {
                    case "REGISTER": handleRegister(p[1], p[2]); break;
                    case "LOGIN": handleLogin(p[1], p[2]); break;
                    case "GET_ROOMS": out.println("ROOM_LIST|" + String.join(",", MultiChatServer.rooms.keySet())); break;
                    case "JOIN_ROOM": handleJoin(p[1]); break;
                    case "LEAVE_ROOM": handleLeave(); break;
                    case "CREATE_ROOM": handleCreateRoom(p[1]); break;
                    case "GET_MEMBERS": handleGetMembers(); break;
                    case "KICK_MEMBER": handleKickMember(p[1]); break;
                    case "CHAT":
                        // 1. Lưu nội dung chat vào SQL Server (Nhật ký dài hạn)
                        saveChatLog(username, currentRoom, p[1]);

                        // 2. Kiểm tra quyền chủ phòng
                        boolean isHost = checkIsHost(currentRoom);
                        String displayName = (isHost ? "[Chủ] " : "") + username;

                        // 3. Ghi vào Message Buffer của phòng (để Web User poll được)
                        //    addMessage() vừa lưu buffer vừa trả về chuỗi format "MSG|..."
                        Room chatRoom = MultiChatServer.rooms.get(currentRoom);
                        if (chatRoom != null) chatRoom.addMessage(displayName, p[1]);

                        // 4. Gửi log về console Admin
                        server.log("{" + currentRoom + "} " + displayName + ": " + p[1]);

                        // 5. Đẩy tin nhắn tới các TCP client trong phòng qua Socket
                        broadcastToTcp("MSG|" + displayName + ": " + p[1]);
                        break;
                }
            }
        } catch (Exception e) { } finally { cleanup(); }
    }

    // Các hàm xử lý SQL và Phân quyền (Giữ nguyên logic nốt trước)
    private void handleCreateRoom(String rName) {
        try (Connection conn = DBConnection.getConnection()) {
            if (conn == null) {
                out.println("SERVER|Không thể kết nối Database!");
                System.err.println("[ERROR] Connection null - Create room: " + rName);
                return;
            }
            PreparedStatement ps = conn.prepareStatement("INSERT INTO rooms(room_name, host_name) VALUES(?, ?)");
            ps.setString(1, rName); ps.setString(2, username); 
            ps.executeUpdate();
            out.println("SERVER|Tạo phòng thành công!");
            server.loadRoomsFromDB(); server.broadcastRoomList(); 
        } catch (SQLException e) {
            if (e.getMessage().contains("PK_rooms") || e.getMessage().contains("PRIMARY KEY")) {
                out.println("SERVER|Tên phòng đã tồn tại!");
            } else {
                out.println("SERVER|Lỗi tạo phòng: " + e.getMessage());
                System.err.println("[ERROR] handleCreateRoom: " + e.getMessage());
            }
        } catch (Exception e) { 
            out.println("SERVER|Lỗi tạo phòng: " + e.getMessage());
            System.err.println("[ERROR] handleCreateRoom: " + e.getMessage());
        }
    }

    private void handleJoin(String rName) {
        handleLeave(); 
        currentRoom = rName;
        if (MultiChatServer.rooms.containsKey(currentRoom)) {
            MultiChatServer.rooms.get(currentRoom).clients.add(this);
            boolean isHost = checkIsHost(rName);
            out.println("JOIN_SUCCESS|" + rName + "|" + (isHost ? "HOST" : "MEMBER"));
            
            // Load history chat cũ cho User mới vào phòng
            loadHistory(rName);
            server.updateUserUI();
            broadcastToTcp("MSG|Hệ thống: " + username + " đã vào phòng.");
        }
    }

    private void loadHistory(String rName) {
        try (Connection conn = DBConnection.getConnection()) {
            if (conn == null) {
                System.err.println("[WARNING] Connection null - Load history for room: " + rName);
                return;
            }
            String sql = "SELECT TOP 20 username, message FROM chat_logs WHERE room_name = ? ORDER BY sent_at DESC";
            PreparedStatement ps = conn.prepareStatement(sql);
            ps.setString(1, rName);
            ResultSet rs = ps.executeQuery();
            List<String> list = new ArrayList<>();
            while(rs.next()) list.add("MSG|(Cũ) " + rs.getString("username") + ": " + rs.getString("message"));
            for(int i = list.size()-1; i>=0; i--) out.println(list.get(i));
        } catch(Exception e) {
            System.err.println("[ERROR] loadHistory: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private void handleGetMembers() {
        if (currentRoom != null) {
            List<String> names = new ArrayList<>();
            try (Connection conn = DBConnection.getConnection()) {
                if (conn == null) {
                    System.err.println("[ERROR] Connection null - Get members");
                    return;
                }
                PreparedStatement ps = conn.prepareStatement("SELECT host_name FROM rooms WHERE room_name = ?");
                ps.setString(1, currentRoom);
                ResultSet rs = ps.executeQuery();
                String hostInDB = rs.next() ? rs.getString("host_name") : "";
                for (ClientHandler h : MultiChatServer.rooms.get(currentRoom).clients) {
                    String tag = h.getUsername().equals(hostInDB) ? "[Chủ]" : "[TV]";
                    names.add(tag + " " + h.getUsername());
                }
                out.println("MEMBER_LIST|" + String.join(",", names));
            } catch (Exception e) { 
                System.err.println("[ERROR] handleGetMembers: " + e.getMessage());
                e.printStackTrace();
            }
        }
    }

    private void handleLeave() {
        if (currentRoom != null) {
            MultiChatServer.rooms.get(currentRoom).clients.remove(this);
            currentRoom = null;
            out.println("LEFT_ROOM");
            server.updateUserUI();
        }
    }

    private void saveChatLog(String user, String room, String msg) {
        try (Connection conn = DBConnection.getConnection()) {
            if (conn == null) {
                System.err.println("[WARNING] Connection null - Save chat log");
                return;
            }
            PreparedStatement ps = conn.prepareStatement("INSERT INTO chat_logs(username, room_name, message) VALUES(?,?,?)");
            ps.setString(1, user); ps.setString(2, room); ps.setString(3, msg);
            ps.executeUpdate();
        } catch (Exception e) { 
            System.err.println("[ERROR] saveChatLog: " + e.getMessage());
        }
    }

    public void kickByAdmin() { out.println("KICKED_BY_ADMIN"); try { socket.close(); } catch (Exception e) {} }
    
    /**
     * Broadcast qua TCP Socket tới tất cả client đang kết nối trong phòng.
     * Lưu ý: Chỉ push tới TCP client. Web User nhận qua HTTP poll trên Message Buffer.
     */
    private void broadcastToTcp(String msg) {
        if (currentRoom != null && MultiChatServer.rooms.containsKey(currentRoom)) {
            for (ClientHandler h : MultiChatServer.rooms.get(currentRoom).clients) {
                h.out.println(msg);
            }
        }
    }

    public void sendMessage(String msg) { out.println(msg); }

    private void cleanup() {
        server.onlineUsers.remove(this);
        handleLeave();
        try { socket.close(); } catch (Exception e) {}
    }

    // Xử lý Login và Register (Giữ nguyên)
    private void handleLogin(String user, String pass) {
        try (Connection conn = DBConnection.getConnection()) {
            if (conn == null) {
                out.println("SERVER|Không thể kết nối Database!");
                System.err.println("[ERROR] Connection null cho user: " + user);
                return;
            }
            PreparedStatement ps = conn.prepareStatement("SELECT * FROM users WHERE username=? AND password=?");
            ps.setString(1, user); ps.setString(2, pass);
            if (ps.executeQuery().next()) { this.username = user; out.println("LOGIN_SUCCESS"); server.updateUserUI(); }
            else { out.println("SERVER|Sai tài khoản!"); }
        } catch (Exception e) { 
            out.println("SERVER|Lỗi đăng nhập: " + e.getMessage());
            System.err.println("[ERROR] handleLogin: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private void handleRegister(String user, String pass) {
        try (Connection conn = DBConnection.getConnection()) {
            if (conn == null) {
                out.println("SERVER|Không thể kết nối Database!");
                System.err.println("[ERROR] Connection null cho register: " + user);
                return;
            }
            PreparedStatement ps = conn.prepareStatement(
                "INSERT INTO users (username, password, role) VALUES (?, ?, 'user')");
            ps.setString(1, user);
            ps.setString(2, pass);
            ps.executeUpdate();
            out.println("SERVER|Đăng ký thành công!");
        } catch (SQLException e) {
            // Bắt riêng lỗi UNIQUE KEY (username đã tồn tại)
            String msg = e.getMessage();
            if (msg != null && (msg.contains("UNIQUE") || msg.contains("duplicate") || msg.contains("Violation"))) {
                out.println("SERVER|Tên đăng nhập đã tồn tại!");
                System.err.println("[WARN] handleRegister: username trùng - " + user);
            } else {
                out.println("SERVER|Lỗi đăng ký: " + msg);
                System.err.println("[ERROR] handleRegister: " + msg);
            }
        } catch (Exception e) {
            out.println("SERVER|Lỗi đăng ký: " + e.getMessage());
            System.err.println("[ERROR] handleRegister: " + e.getMessage());
        }
    }

    private void handleKickMember(String targetName) {
        if (!checkIsHost(currentRoom)) return;
        Room room = MultiChatServer.rooms.get(currentRoom);
        for (ClientHandler h : room.clients) {
            if (h.getUsername().equals(targetName)) {
                h.out.println("KICK_FROM_ROOM"); h.handleLeave();
                broadcastToTcp("MSG|Hệ thống: Chủ phòng đã đuổi " + targetName + " ra ngoài.");
                break;
            }
        }
    }
}