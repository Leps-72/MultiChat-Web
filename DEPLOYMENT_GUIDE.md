# MultiChat Deployment Guide - Render Cloud

Hướng dẫn triển khai ứng dụng MultiChat lên Render Cloud

## 📋 Tổng Quan Kiến Trúc

### Local Development
```
┌─────────────────────────────────────────┐
│         docker-compose.yml              │
├─────────────────────────────────────────┤
│ Web Clients                             │
│ :8080 (HTTP Server)                     │
│         │                               │
│   ┌─────────────────┐                   │
│   │  MultiChat App  │                   │
│   │  :5000 (TCP)    │                   │
│   │  :8080 (HTTP)   │                   │
│   └────────┬────────┘                   │
│            │                            │
│   ┌─────────────────┐                   │
│   │  PostgreSQL DB  │                   │
│   │  :5432          │                   │
│   └─────────────────┘                   │
└─────────────────────────────────────────┘
```

### Cloud Deployment (Render)
```
┌──────────────────────────────────────────┐
│         Render Cloud (render.yaml)       │
├──────────────────────────────────────────┤
│ ┌──────────────────────────────────────┐ │
│ │  Web Service                         │ │
│ │  HTTP :8080 → Web Browser            │ │
│ │  PostgreSQL Connection               │ │
│ └──────────────────────────────────────┘ │
│ ┌──────────────────────────────────────┐ │
│ │  Background Worker                   │ │
│ │  TCP :5000 → Desktop Clients         │ │
│ │  PostgreSQL Connection               │ │
│ └──────────────────────────────────────┘ │
│ ┌──────────────────────────────────────┐ │
│ │  PostgreSQL (Managed Service)        │ │
│ │  Automatic Backups & Updates         │ │
│ └──────────────────────────────────────┘ │
└──────────────────────────────────────────┘
```

---

## 🚀 Phần 1: Local Development Setup

### 1.1 Yêu Cầu Hệ Thống
- Docker & Docker Compose (phiên bản mới nhất)
- Git
- Terminal/Command Prompt

### 1.2 Chạy Locally Với Docker Compose

```bash
# Clone/navigate to project
cd /path/to/MultiChat

# Build và start tất cả services
docker-compose up --build

# Output sẽ như sau:
# multichat-db    | database system is ready to accept connections
# multichat-app   | [OK] Database connection successful!
# multichat-app   | [STARTUP] TCP Server listening on port 5000
# multichat-app   | [STARTUP] HTTP Server listening on port 8080
```

### 1.3 Truy Cập Ứng Dụng Locally
- **Web Interface**: http://localhost:8080
- **TCP Socket**: localhost:5000 (cho Java client)
- **PostgreSQL**: localhost:5432

### 1.4 Dừng Services
```bash
docker-compose down
# Nếu muốn xóa volume (database data):
docker-compose down -v
```

---

## 🌐 Phần 2: Deploy Lên Render Cloud

### 2.1 Chuẩn Bị Trước Deploy
1. Tạo tài khoản trên [Render.com](https://render.com)
2. Kết nối GitHub repository
3. Chuẩn bị các files (đã có sẵn trong project):
   - `Dockerfile` - Container configuration
   - `render.yaml` - Render deployment configuration
   - `init_db.sql` - Database schema initialization

### 2.2 Deploy Steps

#### Option A: Sử dụng Render Dashboard

1. **Login to Render.com**
   - Vào https://dashboard.render.com

2. **Create New Service**
   - Click "New +" → "Web Service"
   - Chọn repository GitHub (MultiChat)
   - Chọn branch (main)

3. **Configure Web Service**
   ```
   Name: multichat-web
   Environment: Docker
   Instance Type: Standard (cho production)
   Scaling: Enable Auto-scaling (set max 3)
   ```

4. **Add Environment Variables**
   ```
   DB_TYPE=postgresql
   DB_HOST=[auto-filled từ DB service]
   DB_PORT=5432
   DB_NAME=multichat
   DB_USER=multichat
   DB_PASS=[set password mạnh]
   ```

5. **Create PostgreSQL Database**
   - Click "New +" → "PostgreSQL"
   - Name: `multichat-db`
   - User: `multichat`
   - Password: [tạo password mạnh]
   - Region: [chọn gần với người dùng]

6. **Create Background Worker (Optional - cho TCP Server)**
   - Click "New +" → "Background Worker"
   - Kết nối cùng database
   - Sẽ handle TCP connections trên port 5000

#### Option B: Sử dụng Git Push Deploy

```bash
# Thêm Render remote
git remote add render https://git.render.com/your-org/multichat.git

# Push để trigger deploy
git push render main
```

### 2.3 Post-Deployment Verification

```bash
# Kiểm tra Web Service logs
# Trên Render Dashboard: Services → multichat-web → Logs

# Check Database Connection
# Trên Render Dashboard: PostgreSQL → URL (for connection)

# Test Web Interface
curl https://multichat-web.onrender.com

# Test Database Connection
# SSH vào service và test:
psql -h [db-host] -U multichat -d multichat -c "SELECT * FROM users LIMIT 1;"
```

---

## 📱 Phần 3: Client Configuration

### 3.1 Web Client (Browser)
- Mở: `https://multichat-web.onrender.com`
- Tự động kết nối HTTP tới server

### 3.2 Desktop Java Client Configuration
- Chỉnh sửa connection settings để pointing tới:
  ```
  Host: multichat-socket-worker.onrender.com
  Port: 5000 (hoặc Render sẽ map automatically)
  ```
- Render sẽ expose TCP port automatically

---

## 🔧 Phần 4: Configuration & Troubleshooting

### 4.1 Thay Đổi Database Password
```bash
# Trên Render Dashboard
1. PostgreSQL → Settings
2. Reset Master Password
3. Update environment variables trên Web Service
```

### 4.2 View Logs
```bash
# Web Service Logs
Render Dashboard → Services → multichat-web → Logs

# Database Logs (if needed)
Render Dashboard → PostgreSQL → Logs
```

### 4.3 Common Issues

**Issue: Connection Refused**
```
Giải pháp:
1. Check database service running: Render Dashboard → PostgreSQL → Status
2. Verify environment variables match database credentials
3. Restart Web Service
```

**Issue: Out of Memory**
```
Giải pháp:
1. Increase instance size: Services → Settings
2. Add -Xmx flag to Dockerfile
```

**Issue: Database Initialization Failed**
```
Giải pháp:
1. Check init_db.sql syntax
2. Manually run commands trong Render database console
3. Review PostgreSQL logs
```

### 4.4 Performance Tuning

```yaml
# Trong render.yaml - adjust for production:
maxInstances: 5  # Increase for high traffic
reservedConcurrency: 100  # Limit concurrent connections
```

---

## 💾 Phần 5: Data Backup & Maintenance

### 5.1 Database Backups
- Render tự động backup PostgreSQL hàng ngày
- Access backups: Dashboard → PostgreSQL → Backups

### 5.2 Manual Backup
```bash
# Kết nối tới database và export
pg_dump -h [host] -U multichat multichat > backup.sql

# Restore từ backup
psql -h [host] -U multichat multichat < backup.sql
```

### 5.3 Database Maintenance
```sql
-- Optimize table
VACUUM ANALYZE;

-- Check table sizes
SELECT 
    schemaname,
    tablename,
    pg_size_pretty(pg_total_relation_size(schemaname||'.'||tablename)) 
FROM pg_tables 
WHERE schemaname != 'pg_catalog' 
ORDER BY pg_total_relation_size(schemaname||'.'||tablename) DESC;
```

---

## 📊 Phần 6: Monitoring

### 6.1 Render Metrics
- CPU Usage
- Memory Usage
- Request Count
- Response Time
- Error Rate

Xem tại: Dashboard → Services → [Service Name] → Metrics

### 6.2 Set Up Alerts
1. Dashboard → Services → [Service Name] → Settings
2. Enable notifications for:
   - Service failures
   - High memory usage
   - High CPU usage

---

## 🔐 Security Considerations

### 6.1 Environment Variables (Never in Code!)
```bash
✓ GOOD: Sử dụng Render environment variables
✗ BAD: Hardcode DB passwords trong source code
```

### 6.2 Database Access
- Chỉ cho phép connection từ app services
- Không expose database port publicly

### 6.3 SSL/TLS
- Render tự động issue SSL certificates
- Enable force HTTPS: Dashboard → Web Service → Settings

### 6.4 Database Credentials
- Render generates strong passwords
- Store securely - không share publicly
- Rotate periodically

---

## 📈 Phần 7: Scaling Guidelines

### 7.1 Auto-scaling Configuration
```yaml
# Mở render.yaml - maxInstances
maxInstances: 5  # Tăng dựa trên traffic
```

### 7.2 Database Scaling
- Render PostgreSQL tự động handle scaling
- Monitor: Dashboard → PostgreSQL → Metrics

### 7.3 Cost Optimization
```
Starter Tier: $7/month - Development
Standard Tier: $25/month - Production
```

---

## 🚢 Phần 8: CI/CD & Auto-Deploy

### 8.1 Enable Auto-Deploy
1. Dashboard → Services → multichat-web → Settings
2. Auto-Deploy: Enable
3. Mỗi khi push lên GitHub → Tự động deploy

### 8.2 Manual Redeploy
```
Dashboard → Services → multichat-web → Manual Redeploy
```

---

## 📞 Support & Resources

- **Render Documentation**: https://render.com/docs
- **PostgreSQL Docs**: https://www.postgresql.org/docs/
- **Java Docker**: https://docs.docker.com/language/java/

---

## ✅ Checklist Pre-Production

- [ ] Database backup tested
- [ ] Environment variables configured
- [ ] SSL certificate enabled
- [ ] Auto-scaling configured
- [ ] Monitoring alerts set up
- [ ] Logs rotation enabled
- [ ] Database credentials rotated
- [ ] Load testing passed
- [ ] User documentation updated
- [ ] Team trained on deployment process

---

## 📝 Quick Reference Commands

```bash
# Local development
docker-compose up --build       # Start all services
docker-compose down             # Stop services
docker-compose logs -f app      # View app logs
docker-compose logs -f db       # View database logs

# Docker operations
docker ps                       # List running containers
docker exec -it [container] bash  # SSH into container
docker build -t multichat .     # Build image

# Database operations
docker exec multichat-db psql -U multichat -d multichat -c "SELECT * FROM users;"

# Render CLI (optional)
# npm install -g @render-web/cli
# render login
# render deploy
```

---

**Last Updated**: 2024
**Version**: 1.0
