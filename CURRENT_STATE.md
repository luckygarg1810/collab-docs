# Collab-Docs - Current Implementation State

**Last Updated:** 2026-02-08  
**Version:** Phase 1 Complete - Docker Setup

---

## 🐳 Docker Quick Start (NEW!)

### First Time Setup
```bash
# 1. Copy environment template
cp .env.example .env

# 2. Edit .env with your credentials
nano .env
# Update: POSTGRES_PASSWORD, REDIS_PASSWORD, JWT_SECRET, EMAIL_USERNAME, EMAIL_PASSWORD

# 3. Start all services
docker-compose up -d

# 4. Run verification script
./docker-test.sh

# 5. Check health
curl http://localhost:8080/actuator/health
```

### Services Running
- **Backend API**: http://localhost:8080
- **Swagger UI**: http://localhost:8080/swagger-ui.html
- **PostgreSQL**: localhost:5432 (database: collab_docs)
- **Redis**: localhost:6379

See **[DOCKER_SETUP.md](file:///home/lucky/collab-docs/DOCKER_SETUP.md)** for complete documentation.

---

## 🎯 Quick Start

**Base URL:** `http://localhost:8080`

**Prerequisites:**
- PostgreSQL running
- Redis running
- Environment variables configured (see application.properties)

---

## ✅ Implemented Features

### 1. User Authentication & Management

#### **Register User** (with OTP verification)
```bash
# Step 1: Register (sends OTP to email)
curl -X POST http://localhost:8080/api/auth/register \
  -H "Content-Type: application/json" \
  -d '{
    "firstName": "John",
    "lastName": "Doe",
    "email": "john.doe@example.com",
    "password": "SecurePass123!"
  }'

# Response: 200 OK
# {
#   "message": "Registration initiated. Please verify OTP sent to your email."
# }

# Step 2: Verify OTP
curl -X POST http://localhost:8080/api/auth/verify-otp \
  -H "Content-Type: application/json" \
  -d '{
    "email": "john.doe@example.com",
    "otp": "123456"
  }'

# Response: 201 Created (with JWT cookie set)
# {
#   "message": "User registered successfully",
#   "user": {
#     "id": 1,
#     "firstName": "John",
#     "lastName": "Doe",
#     "email": "john.doe@example.com"
#   }
# }
```

#### **Resend OTP**
```bash
curl -X POST http://localhost:8080/api/auth/resend-otp \
  -H "Content-Type: application/json" \
  -d '{
    "email": "john.doe@example.com"
  }'
```

#### **Login**
```bash
curl -X POST http://localhost:8080/api/auth/login \
  -H "Content-Type: application/json" \
  -c cookies.txt \
  -d '{
    "email": "john.doe@example.com",
    "password": "SecurePass123!"
  }'
```

#### **Get Current User**
```bash
curl -X GET http://localhost:8080/api/auth/me \
  -b cookies.txt
```

#### **Logout**
```bash
curl -X POST http://localhost:8080/api/auth/logout \
  -b cookies.txt
```

#### **Forgot Password**
```bash
# Request OTP
curl -X POST http://localhost:8080/api/auth/forgot-password \
  -H "Content-Type: application/json" \
  -d '{"email": "john.doe@example.com"}'

# Reset with OTP
curl -X POST http://localhost:8080/api/auth/reset-password \
  -H "Content-Type: application/json" \
  -d '{
    "email": "john.doe@example.com",
    "otp": "123456",
    "newPassword": "NewSecurePass123!"
  }'
```

---

### 2. Document Management

#### **Create Blank Document**
```bash
curl -X POST http://localhost:8080/api/documents/create \
  -H "Content-Type: application/json" \
  -b cookies.txt \
  -d '{"title": "My New Document"}'
```

#### **Upload Document (DOCX/PDF)**
```bash
curl -X POST http://localhost:8080/api/documents/upload \
  -b cookies.txt \
  -F "file=@/path/to/document.docx" \
  -F "title=Imported Document"
```

#### **Get Document by ID**
```bash
curl -X GET http://localhost:8080/api/documents?document_id=1 \
  -b cookies.txt
```

#### **List User Documents**
```bash
curl -X GET "http://localhost:8080/api/documents?page=0&size=20" \
  -b cookies.txt
```

#### **Update Document Visibility**
```bash
curl -X PATCH http://localhost:8080/api/documents/1/visibility \
  -H "Content-Type: application/json" \
  -b cookies.txt \
  -d '{"visibility": "PUBLIC"}'

# Valid: "PRIVATE", "SHARED", "PUBLIC"
```

#### **Soft Delete Document**
```bash
curl -X DELETE http://localhost:8080/api/documents/1 \
  -b cookies.txt
```

---

### 3. Real-Time Collaboration (WebSocket)

⚠️ **SECURITY WARNING**: Currently has NO authentication!

```javascript
// Connect to document
const ws = new WebSocket('ws://localhost:8080/ws/yjs/uuid-room-id');

ws.onmessage = (event) => {
  // Receive Yjs updates
  const updateData = event.data;
};

// Send Yjs update
const update = new Uint8Array([/* binary */]);
ws.send(update);
```

---

## ❌ Not Yet Implemented

- RBAC (roles, permissions)
- Document sharing (links, invitations)
- Document versioning
- Audit logging
- Export APIs
- Rate limiting
- WebSocket authentication
- Presence awareness

---

## 🗄️ Database Schema

### **users**
- `id`, `first_name`, `last_name`, `email`, `password`, `created_at`
- `provider`, `provider_id`, `account_non_locked`

### **documents**
- `id`, `title`, `file_name`, `content_type`, `content`
- `yjs_room_id`, `owner_id`, `visibility`, `is_deleted`
- `created_at`, `updated_at`, `yjs_snapshot`

### **pending_users**
- Temporary storage for unverified registrations

### **email_otps**
- OTP codes for registration and password reset

---

## 🔧 Configuration

Required environment variables:
```bash
SPRING_DATASOURCE_URL=jdbc:postgresql://localhost:5432/collab_docs
SPRING_DATASOURCE_USERNAME=postgres
SPRING_DATASOURCE_PASSWORD=password
JWT_SECRET=your-32-char-secret
JWT_EXPIRATION_MS=86400000
FRONTEND_URL=http://localhost:3000
EMAIL_USERNAME=your@gmail.com
EMAIL_PASSWORD=gmail-app-password
REDIS_HOST=localhost
REDIS_PORT=6379
REDIS_PASSWORD=redis-password
REDIS_DATABASE=0
```

---

## 🧪 Quick Testing

```bash
# Register & login
curl -X POST http://localhost:8080/api/auth/register \
  -H "Content-Type: application/json" \
  -d '{"firstName":"Test","lastName":"User","email":"test@example.com","password":"Test123!"}'

# Verify (check email/logs for OTP)
curl -X POST http://localhost:8080/api/auth/verify-otp \
  -H "Content-Type: application/json" \
  -c cookies.txt \
  -d '{"email":"test@example.com","otp":"123456"}'

# Create document
curl -X POST http://localhost:8080/api/documents/create \
  -H "Content-Type: application/json" \
  -b cookies.txt \
  -d '{"title":"Test Doc"}'
```

---

## 📊 Known Issues

**Critical:**
- ❌ WebSocket has no authentication
- ❌ No permission system
- ❌ No rate limiting

**High:**
- No audit logging
- No versioning
- GraalVM Yjs needs replacement

---

## 🚀 Next: Phase 2 - Node.js Yjs Microservice

Phase 1 Complete! ✅

Next will implement:
- Node.js Yjs collaboration service
- WebSocket authentication
- Integration with Spring Boot backend

---

## 📝 Update Log

| Date | Phase | Changes |
|------|-------|---------|
| 2026-02-08 | Baseline | Initial state documentation |
| 2026-02-08 | Phase 1 | ✅ Docker setup complete (compose, Dockerfile, test script) |
