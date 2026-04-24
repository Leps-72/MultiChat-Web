-- Initialize PostgreSQL Database for MultiChat

-- 1. Users table (with role for web admin)
CREATE TABLE IF NOT EXISTS users (
    id SERIAL PRIMARY KEY,
    username VARCHAR(50) UNIQUE NOT NULL,
    password VARCHAR(50) NOT NULL,
    role VARCHAR(10) NOT NULL DEFAULT 'user'  -- 'admin' or 'user'
);

-- 2. Rooms table (chat rooms)
CREATE TABLE IF NOT EXISTS rooms (
    id SERIAL PRIMARY KEY,
    room_name VARCHAR(100) UNIQUE NOT NULL,
    host_name VARCHAR(50),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- 3. Chat logs table (message history)
CREATE TABLE IF NOT EXISTS chat_logs (
    id SERIAL PRIMARY KEY,
    username VARCHAR(50),
    room_name VARCHAR(100),
    message TEXT,
    sent_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- 4. Create indexes for better performance
CREATE INDEX IF NOT EXISTS idx_chat_logs_room_name ON chat_logs(room_name);
CREATE INDEX IF NOT EXISTS idx_chat_logs_sent_at ON chat_logs(sent_at DESC);
CREATE INDEX IF NOT EXISTS idx_rooms_name ON rooms(room_name);
CREATE INDEX IF NOT EXISTS idx_users_username ON users(username);

-- 5. Sample data
INSERT INTO users (username, password, role) VALUES 
('admin', '12345', 'admin'),   -- Admin account
('huynh', '123', 'user'), 
('user1', '123', 'user')
ON CONFLICT DO NOTHING;

INSERT INTO rooms (room_name, host_name) VALUES 
('General', 'admin'),
('Random', 'admin')
ON CONFLICT DO NOTHING;
