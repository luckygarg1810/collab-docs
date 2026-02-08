# Collab-Docs - Docker Setup Guide

## 🐳 Quick Start

### Prerequisites
- Docker 20.10+
- Docker Compose 2.0+

### Setup Steps

1. **Copy environment template**
```bash
cp .env.example .env
```

2. **Edit `.env` file with your configuration**
```bash
# IMPORTANT: Change these values!
nano .env

# At minimum, update:
# - POSTGRES_PASSWORD
# - REDIS_PASSWORD
# - JWT_SECRET (generate a random 32+ character string)
# - EMAIL_USERNAME and EMAIL_PASSWORD (Gmail app password)
```

3. **Start all services**
```bash
docker-compose up -d
```

4. **Check status**
```bash
docker-compose ps
```

5. **View logs**
```bash
# All services
docker-compose logs -f

# Specific service
docker-compose logs -f backend
docker-compose logs -f postgres
docker-compose logs -f redis
```

6. **Access the application**
- Backend API: http://localhost:8080
- Swagger UI: http://localhost:8080/swagger-ui.html
- Health Check: http://localhost:8080/actuator/health

---

## 🔧 Configuration

### Environment Variables

See `.env.example` for all available configuration options.

**Critical settings:**
- `JWT_SECRET` - Must be 32+ characters, keep secret!
- `POSTGRES_PASSWORD` - Strong password for database
- `REDIS_PASSWORD` - Strong password for Redis
- `EMAIL_USERNAME` / `EMAIL_PASSWORD` - Gmail SMTP credentials

### Gmail SMTP Setup

1. Enable 2-factor authentication on your Gmail account
2. Generate an App Password: https://myaccount.google.com/apppasswords
3. Use the generated app password in `EMAIL_PASSWORD`

---

## 📦 Services

### PostgreSQL
- **Port:** 5432 (configurable)
- **Database:** collab_docs (configurable)
- **Data:** Persisted in `postgres_data` volume

### Redis
- **Port:** 6379 (configurable)
- **Data:** Persisted in `redis_data` volume
- **Password protected**

### Backend (Spring Boot)
- **Port:** 8080 (configurable)
- **Health check:** `/actuator/health`
- **Logs:** Stored in `backend_logs` volume

---

## 🛠️ Common Commands

### Start services
```bash
docker-compose up -d
```

### Stop services
```bash
docker-compose down
```

### Rebuild and restart
```bash
docker-compose up -d --build
```

### View logs (follow)
```bash
docker-compose logs -f backend
```

### Execute commands in container
```bash
# Access Spring Boot container
docker-compose exec backend sh

# Access PostgreSQL
docker-compose exec postgres psql -U postgres -d collab_docs

# Access Redis CLI
docker-compose exec redis redis-cli -a your_redis_password
```

### Clean up (including volumes)
```bash
docker-compose down -v
```

---

## 🧪 Testing the Setup

### 1. Check health
```bash
curl http://localhost:8080/actuator/health
```

Expected response:
```json
{
  "status": "UP"
}
```

### 2. Register a user
```bash
curl -X POST http://localhost:8080/api/auth/register \
  -H "Content-Type: application/json" \
  -d '{
    "firstName": "Test",
    "lastName": "User",
    "email": "test@example.com",
    "password": "Test123!"
  }'
```

### 3. Check database connection
```bash
docker-compose exec postgres psql -U postgres -d collab_docs -c "SELECT count(*) FROM pending_users;"
```

### 4. Check Redis connection
```bash
docker-compose exec redis redis-cli -a your_redis_password PING
```

---

## 🔍 Troubleshooting

### Backend won't start
1. Check if PostgreSQL is healthy:
```bash
docker-compose ps postgres
```

2. Check backend logs:
```bash
docker-compose logs backend
```

3. Common issues:
- Database credentials incorrect
- Redis password mismatch
- Port already in use (change `BACKEND_PORT` in `.env`)

### Database connection failed
1. Ensure PostgreSQL is running:
```bash
docker-compose ps postgres
```

2. Check database credentials in `.env`

3. Test connection manually:
```bash
docker-compose exec postgres psql -U postgres -d collab_docs
```

### Email not sending
1. Verify Gmail app password is correct
2. Check backend logs for SMTP errors
3. Ensure 2FA is enabled on Gmail account

---

## 🚀 Production Deployment

### Security Checklist
- [ ] Change all default passwords
- [ ] Use strong JWT_SECRET (32+ random characters)
- [ ] Use environment-specific `.env` file
- [ ] Enable HTTPS (use reverse proxy like Nginx)
- [ ] Restrict database access (don't expose port 5432 publicly)
- [ ] Set `SPRING_PROFILES_ACTIVE=prod`
- [ ] Configure firewall rules
- [ ] Set up regular database backups

### Recommended Production Setup
```yaml
# docker-compose.prod.yml
services:
  postgres:
    # Don't expose port publicly
    # ports:
    #   - "5432:5432"
    # Use internal network only
  
  redis:
    # Don't expose port publicly
    # ports:
    #   - "6379:6379"
  
  backend:
    restart: always
    # Add resource limits
    deploy:
      resources:
        limits:
          cpus: '2'
          memory: 2G
```

Run with:
```bash
docker-compose -f docker-compose.yml -f docker-compose.prod.yml up -d
```

---

## 📊 Monitoring

### View resource usage
```bash
docker stats
```

### Check container health
```bash
docker-compose ps
```

### Database size
```bash
docker-compose exec postgres psql -U postgres -d collab_docs -c "SELECT pg_size_pretty(pg_database_size('collab_docs'));"
```

---

## 🔄 Updates & Maintenance

### Update application code
```bash
# Pull latest code
git pull

# Rebuild and restart
docker-compose up -d --build backend
```

### Backup database
```bash
docker-compose exec postgres pg_dump -U postgres collab_docs > backup_$(date +%Y%m%d).sql
```

### Restore database
```bash
cat backup_20260208.sql | docker-compose exec -T postgres psql -U postgres collab_docs
```

---

## 📝 Next Steps

After successful Docker setup:
1. Test all authentication endpoints
2. Create a test document
3. Implement Phase 2: Node.js Yjs microservice
4. Add WebSocket authentication

See `current_state.md` for API testing examples.
