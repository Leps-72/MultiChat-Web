# 🚀 MultiChat - Quick Start Guide

Hướng dẫn nhanh để bắt đầu phát triển và deploy MultiChat

## 🏃 Quick Start - Local Development (3 phút)

```bash
# 1. Clone hoặc navigate tới project
cd MultiChat

# 2. Start tất cả services (Web + Database)
docker-compose up --build

# 3. Chờ thông báo "HTTP Server listening on port 8080"

# 4. Mở browser và truy cập
# http://localhost:8080
```

**Thế là done!** ✅ Ứng dụng chạy locally.

---

## 🌐 Deploy to Render Cloud (5-10 phút)

### Bước 1: Chuẩn Bị GitHub
```bash
# Đảm bảo project đã push lên GitHub
git add .
git commit -m "Ready for Render deployment"
git push origin main
```

### Bước 2: Vào Render Dashboard
1. Đăng nhập: https://dashboard.render.com
2. Click "New +" → "Web Service"
3. Chọn GitHub repository (MultiChat)

### Bước 3: Configure Service
```
Name: multichat-web
Runtime: Docker
Auto-deploy: ✓ Enabled
```

### Bước 4: Add Environment Variables
```
DB_TYPE=postgresql
DB_HOST=[sẽ auto-fill]
DB_PORT=5432
DB_NAME=multichat
DB_USER=multichat
DB_PASS=[tạo password mạnh]
```

### Bước 5: Create Database
1. Click "New +" → "PostgreSQL"
2. Name: `multichat-db`
3. Username: `multichat`
4. Password: [tạo password mạnh]
5. Click "Create"

### Bước 6: Deploy
- Click "Create Web Service"
- Chờ ~5 phút cho build & deploy hoàn tất

**Xong!** 🎉 App đang chạy trên: `https://multichat-web.onrender.com`

---

## 📝 Common Commands

### Development
```bash
# Start services
docker-compose up --build

# Stop services
docker-compose down

# View logs
docker-compose logs -f app
docker-compose logs -f db

# Restart app
docker-compose restart app

# SSH into app container
docker-compose exec app bash

# SSH into database
docker-compose exec db psql -U multichat -d multichat
```

### Database
```bash
# Connect to PostgreSQL
psql -h localhost -U multichat -d multichat

# List tables
\dt

# View users
SELECT * FROM users;

# View chat rooms
SELECT * FROM rooms;
```

---

## 🔧 Environment Variables

Copy `.env.example` để tạo `.env.local` cho development:

```bash
cp .env.example .env.local
# Edit .env.local với local settings
```

---

## 📖 Documentation

- **Full Deployment Guide**: `DEPLOYMENT_GUIDE.md`
- **Project README**: `README.md`
- **Database Schema**: `init_db.sql`

---

## ❓ Troubleshooting

### Docker không start?
```bash
# Ensure Docker Desktop running
docker --version
docker-compose --version
```

### Database connection error?
```bash
# Check database logs
docker-compose logs db

# Verify credentials trong docker-compose.yml
```

### Web interface not loading?
```bash
# Check app logs
docker-compose logs app

# Verify port 8080 not in use
lsof -i :8080  # macOS/Linux
netstat -ano | findstr :8080  # Windows
```

### Deploy to Render failed?
1. Check Render dashboard logs
2. Verify GitHub repo is public or connected
3. Ensure all required files present:
   - `Dockerfile` ✓
   - `render.yaml` ✓
   - `init_db.sql` ✓

---

## 📊 Architecture

```
┌─ Web Clients (Browser)
│  │
│  ├─→ HTTP Server :8080
│       │
│       ├─ Login/Register
│       ├─ Chat Interface
│       ├─ Message Polling
│       └─ Admin Panel
│
└─ Desktop Clients (Java)
   │
   ├─→ TCP Socket :5000
       │
       ├─ Connect/Disconnect
       ├─ Send/Receive Messages
       └─ Room Management

[Shared Backend - MultiChatServer]
│
└─→ PostgreSQL Database
    ├─ users
    ├─ rooms
    └─ chat_logs
```

---

## 🎯 Next Steps

1. **Customize Web UI**: Edit files trong `web/` folder
2. **Add Features**: Modify Java source trong `src/multichat/`
3. **Scale Up**: Increase `maxInstances` trong `render.yaml`
4. **Monitor**: Setup alerts trong Render dashboard

---

## 💬 Support

- Issues? Check `DEPLOYMENT_GUIDE.md` troubleshooting section
- Questions? Review code comments (many are in Vietnamese)

---

**Happy Chatting! 💬**
