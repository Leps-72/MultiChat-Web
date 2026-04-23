package multichat;

import javax.swing.*;
import javax.swing.border.*;
import java.awt.*;
import java.io.*;
import java.net.Socket;

public class ChatGui extends JFrame {
    private CardLayout cardLayout = new CardLayout();
    private JPanel mainPanel = new JPanel(cardLayout);
    private Socket socket;
    private PrintWriter out;
    private BufferedReader in;
    private boolean imHost = false;

    private JTextField txtUser = new JTextField(18); 
    private JPasswordField txtPass = new JPasswordField(18);
    private DefaultListModel<String> roomModel = new DefaultListModel<>();
    private JList<String> roomList = new JList<>(roomModel);
    private JTextArea chatArea = new JTextArea();
    private JTextField txtMsg = new JTextField();

    public ChatGui(String ip, int port) {
        setTitle("MULTICHAT CLIENT - IT UTE");
        setSize(450, 650);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLocationRelativeTo(null);
        setupUI();
        add(mainPanel);
        connect(ip, port);
    }

    private void setupUI() {
        // --- GIAO DIỆN ĐĂNG NHẬP ---
        JPanel loginP = new JPanel(new GridBagLayout());
        loginP.setBackground(Color.WHITE);
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(12, 10, 12, 10);
        
        txtUser.setBorder(BorderFactory.createTitledBorder("Tên đăng nhập"));
        txtPass.setBorder(BorderFactory.createTitledBorder("Mật khẩu"));
        
        JButton btnLogin = new JButton("ĐĂNG NHẬP");
        btnLogin.setPreferredSize(new Dimension(130, 40)); 
        btnLogin.setBackground(new Color(52, 152, 219)); btnLogin.setForeground(Color.WHITE);
        
        JButton btnReg = new JButton("ĐĂNG KÝ");
        btnReg.setPreferredSize(new Dimension(130, 40)); 
        btnReg.setBackground(new Color(46, 204, 113)); btnReg.setForeground(Color.WHITE);

        gbc.gridx = 0; gbc.gridy = 0; gbc.gridwidth = 2; 
        JLabel lblHeader = new JLabel("HỆ THỐNG TRÒ CHUYỆN");
        lblHeader.setFont(new Font("Segoe UI", Font.BOLD, 20));
        loginP.add(lblHeader, gbc);

        gbc.gridy = 1; gbc.fill = GridBagConstraints.HORIZONTAL; loginP.add(txtUser, gbc);
        gbc.gridy = 2; loginP.add(txtPass, gbc);
        gbc.gridy = 3; gbc.gridwidth = 1; gbc.fill = GridBagConstraints.NONE;
        loginP.add(btnLogin, gbc);
        gbc.gridx = 1; loginP.add(btnReg, gbc);

        // Sự kiện gửi lệnh
        btnLogin.addActionListener(e -> {
            System.out.println("DEBUG: Đang gửi lệnh LOGIN...");
            out.println("LOGIN|" + txtUser.getText().trim() + "|" + new String(txtPass.getPassword()));
        });
        btnReg.addActionListener(e -> {
            System.out.println("DEBUG: Đang gửi lệnh REGISTER...");
            out.println("REGISTER|" + txtUser.getText().trim() + "|" + new String(txtPass.getPassword()));
        });
        
        mainPanel.add(loginP, "LOGIN");

        // --- GIAO DIỆN CHỌN PHÒNG ---
        JPanel roomP = new JPanel(new BorderLayout(15, 15));
        roomP.setBorder(new EmptyBorder(20, 20, 20, 20));
        JPanel roomBtnP = new JPanel(new GridLayout(1, 2, 10, 10));
        JButton btnJoin = new JButton("VÀO PHÒNG"); 
        JButton btnCreate = new JButton("TẠO PHÒNG MỚI");
        roomBtnP.add(btnJoin); roomBtnP.add(btnCreate);
        roomP.add(new JScrollPane(roomList), BorderLayout.CENTER); roomP.add(roomBtnP, BorderLayout.SOUTH);
        
        btnJoin.addActionListener(e -> { if(roomList.getSelectedValue() != null) out.println("JOIN_ROOM|" + roomList.getSelectedValue()); });
        btnCreate.addActionListener(e -> {
            String n = JOptionPane.showInputDialog("Tên phòng mới:");
            if(n != null && !n.trim().isEmpty()) out.println("CREATE_ROOM|" + n.trim());
        });
        mainPanel.add(roomP, "ROOMS");

        // --- GIAO DIỆN CHAT ---
        JPanel chatP = new JPanel(new BorderLayout());
        JPanel chatTop = new JPanel(new GridLayout(1, 2, 5, 5));
        JButton btnLeave = new JButton("RỜI PHÒNG"); JButton btnMembers = new JButton("THÀNH VIÊN");
        chatTop.add(btnMembers); chatTop.add(btnLeave);
        chatP.add(chatTop, BorderLayout.NORTH);
        chatArea.setEditable(false); chatArea.setLineWrap(true);
        chatArea.setFont(new Font("Segoe UI", Font.PLAIN, 15));
        chatP.add(new JScrollPane(chatArea), BorderLayout.CENTER);
        JPanel btm = new JPanel(new BorderLayout()); btm.add(txtMsg, BorderLayout.CENTER);
        JButton btnSend = new JButton("GỬI"); btm.add(btnSend, BorderLayout.EAST);
        chatP.add(btm, BorderLayout.SOUTH);

        btnLeave.addActionListener(e -> out.println("LEAVE_ROOM"));
        btnMembers.addActionListener(e -> out.println("GET_MEMBERS"));
        btnSend.addActionListener(e -> { if(!txtMsg.getText().isEmpty()){ out.println("CHAT|" + txtMsg.getText()); txtMsg.setText(""); }});
        mainPanel.add(chatP, "CHAT");
    }

    private void connect(String ip, int port) {
        new Thread(() -> {
            try {
                socket = new Socket(ip, port);
                out = new PrintWriter(new OutputStreamWriter(socket.getOutputStream(), "UTF-8"), true);
                in = new BufferedReader(new InputStreamReader(socket.getInputStream(), "UTF-8"));
                System.out.println("DEBUG: Đã kết nối Socket tới " + ip);
                listen(); 
            } catch (Exception e) { 
                SwingUtilities.invokeLater(() -> JOptionPane.showMessageDialog(this, "Không thể kết nối Server!"));
            }
        }).start();
    }

    private void listen() {
        try {
            String line;
            while ((line = in.readLine()) != null) {
                final String res = line;
                System.out.println("DEBUG: Nhận phản hồi từ Server -> " + res);
                SwingUtilities.invokeLater(() -> {
                    if (res.equals("LOGIN_SUCCESS")) {
                        cardLayout.show(mainPanel, "ROOMS");
                        out.println("GET_ROOMS");
                    } 
                    else if (res.equals("LEFT_ROOM") || res.equals("KICK_FROM_ROOM")) {
                        cardLayout.show(mainPanel, "ROOMS");
                        imHost = false; 
                    }
                    else if (res.startsWith("JOIN_SUCCESS")) {
                        String[] p = res.split("\\|");
                        imHost = p[2].equals("HOST");
                        cardLayout.show(mainPanel, "CHAT"); chatArea.setText("");
                    } 
                    else if (res.startsWith("ROOM_LIST")) {
                        roomModel.clear(); String[] p = res.split("\\|");
                        if(p.length > 1) for (String r : p[1].split(",")) roomModel.addElement(r);
                    } 
                    else if (res.startsWith("MSG")) {
                        chatArea.append(res.split("\\|")[1] + "\n");
                        chatArea.setCaretPosition(chatArea.getDocument().getLength());
                    }
                    else if (res.startsWith("SERVER")) {
                        JOptionPane.showMessageDialog(this, res.split("\\|")[1]);
                    }
                });
            }
        } catch (Exception e) {
            System.err.println("Mất kết nối!");
        }
    }

    private void showMemberDialog(String data) {
        String[] members = data.split(",");
        JList<String> list = new JList<>(members);
        JPanel p = new JPanel(new BorderLayout());
        p.add(new JScrollPane(list), BorderLayout.CENTER);
        if (imHost) {
            JButton btnKick = new JButton("Kick");
            p.add(btnKick, BorderLayout.SOUTH);
            btnKick.addActionListener(e -> {
                String target = list.getSelectedValue();
                if (target != null && target.contains("[TV]")) {
                    out.println("KICK_MEMBER|" + target.replace("[TV] ", "").trim());
                    SwingUtilities.getWindowAncestor(btnKick).dispose();
                }
            });
        }
        JOptionPane.showMessageDialog(this, p, "Thành viên", JOptionPane.PLAIN_MESSAGE);
    }
}