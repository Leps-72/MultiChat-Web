package multichat;

/**
 * MultiChatClient — Launcher cho TCP Client (CLI mode).
 *
 * Sử dụng: java multichat.MultiChatClient [serverIP] [port]
 *   Ví dụ: java multichat.MultiChatClient 192.168.1.100 5000
 *
 * Lưu ý: Giao diện người dùng chính hiện là WEB (http://localhost:8080).
 * File này chỉ dùng để kết nối TCP Socket thuần (cho mục đích demo TCP).
 */
public class MultiChatClient {
    public static void main(String[] args) {
        String serverIP = args.length >= 1 ? args[0] : "localhost";
        int port = args.length >= 2 ? Integer.parseInt(args[1]) : 5000;

        System.out.println("========================================================");
        System.out.println("  MultiChat TCP Client");
        System.out.println("  Kết nối tới: " + serverIP + ":" + port);
        System.out.println("  Web Interface: http://" + serverIP + ":8080");
        System.out.println("========================================================");
        System.out.println("[INFO] Khởi động ChatGui...");

        // Khởi động Swing GUI (ChatGui.java vẫn được giữ cho TCP client)
        javax.swing.SwingUtilities.invokeLater(() -> new ChatGui(serverIP, port).setVisible(true));
    }
}