package multichat;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Quản lý session đăng nhập cho Web Admin/User.
 * Dùng UUID token lưu trong cookie "SESSION_ID".
 */
public class SessionManager {
    // Map<sessionId, SessionInfo>
    private static final Map<String, SessionInfo> sessions = new ConcurrentHashMap<>();
    // Thời gian hết hạn session: 2 tiếng
    private static final long SESSION_TIMEOUT_MS = 2 * 60 * 60 * 1000L;

    public static class SessionInfo {
        public final String username;
        public final String role; // "admin" hoặc "user"
        public long lastAccess;
        public String currentRoom;
        public long lastPollTime;

        public SessionInfo(String username, String role) {
            this.username = username;
            this.role = role;
            this.lastAccess = System.currentTimeMillis();
            this.lastPollTime = System.currentTimeMillis();
        }
    }

    /** Trả về danh sách tất cả các session đang hoạt động */
    public static Collection<SessionInfo> getAllSessions() {
        return sessions.values();
    }

    /** Tạo session mới sau khi đăng nhập thành công */
    public static String createSession(String username, String role) {
        String token = UUID.randomUUID().toString();
        sessions.put(token, new SessionInfo(username, role));
        return token;
    }

    /** Lấy thông tin session từ token (null nếu hết hạn / không tồn tại) */
    public static SessionInfo getSession(String token) {
        if (token == null) return null;
        SessionInfo si = sessions.get(token);
        if (si == null) return null;
        if (System.currentTimeMillis() - si.lastAccess > SESSION_TIMEOUT_MS) {
            sessions.remove(token);
            return null;
        }
        si.lastAccess = System.currentTimeMillis(); // Refresh
        return si;
    }

    /** Cưỡng chế xóa session theo tên đăng nhập (dùng cho Admin Kick) */
    public static boolean removeSessionsByUsername(String username) {
        return sessions.entrySet().removeIf(entry -> entry.getValue().username.equals(username));
    }

    /** Xóa session khi logout */
    public static void removeSession(String token) {
        sessions.remove(token);
    }

    /** Lấy sessionId từ chuỗi Cookie header */
    public static String extractToken(String cookieHeader) {
        if (cookieHeader == null) return null;
        for (String part : cookieHeader.split(";")) {
            String trimmed = part.trim();
            if (trimmed.startsWith("SESSION_ID=")) {
                return trimmed.substring("SESSION_ID=".length());
            }
        }
        return null;
    }
}
