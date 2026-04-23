USE master;
GO

-- 1. Nếu Database đang được sử dụng, ngắt kết nối để có thể xóa
IF EXISTS (SELECT name FROM sys.databases WHERE name = N'multichat')
BEGIN
    ALTER DATABASE multichat SET SINGLE_USER WITH ROLLBACK IMMEDIATE;
    DROP DATABASE multichat;
END
GO

-- 2. Tạo mới Database
CREATE DATABASE multichat;
GO

USE multichat;
GO

-- 3. Bảng lưu tài khoản người dùng (thêm cột role cho web admin)
CREATE TABLE users (
    id INT PRIMARY KEY IDENTITY(1,1),
    username NVARCHAR(50) UNIQUE NOT NULL,
    password NVARCHAR(50) NOT NULL,
    role NVARCHAR(10) NOT NULL DEFAULT 'user'  -- 'admin' hoặc 'user'
);
GO

-- 4. Bảng lưu danh sách phòng chat
CREATE TABLE rooms (
    id INT PRIMARY KEY IDENTITY(1,1),
    room_name NVARCHAR(100) UNIQUE NOT NULL,
    host_name NVARCHAR(50)
);
GO

-- 5. Bảng lưu lịch sử tin nhắn (Đáp ứng yêu cầu Log)
CREATE TABLE chat_logs (
    id INT PRIMARY KEY IDENTITY(1,1),
    username NVARCHAR(50),
    room_name NVARCHAR(100),
    message NVARCHAR(MAX),
    sent_at DATETIME DEFAULT GETDATE()
);
GO

-- 6. Chèn dữ liệu mẫu (N'...' để không lỗi font tiếng Việt)
INSERT INTO users (username, password, role) VALUES 
('admin', '12345', 'admin'),   -- Tài khoản Admin quản lý hệ thống
('huynh', '123', 'user'), 
('user1', '123', 'user');

INSERT INTO rooms (room_name, host_name) VALUES 
(N'Sảnh Chung', 'admin'), 
(N'Giải Trí', 'admin'), 
(N'Học Tập', 'admin');
GO

-- 7. Kiểm tra dữ liệu sau khi chạy
SELECT * FROM users;
SELECT * FROM rooms;
SELECT * FROM chat_logs;