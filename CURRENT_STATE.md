# Collab-Docs - Current Implementation State

**Last Updated:** 2026-02-08  
**Version:** Phase 3 Complete - Production-Ready Microservices Architecture

---

## 🎯 Architecture Overview

**Microservices Stack:**
- **Spring Boot Backend** - REST API, Auth, PostgreSQL persistence
- **Node.js Yjs Service** - Real-time collaboration, WebSocket, Redis
- **PostgreSQL** - Primary database
- **Redis** - Yjs document state cache

---

## 🐳 Docker Quick Start

```bash
# 1. Setup environment
cp .env.example .env
# Edit .env with your credentials

# 2. Start all services
docker-compose up -d

# 3. Verify health
curl http://localhost:8080/actuator/health
curl http://localhost:3000/health
```

### Services Running
- **Backend API**: http://localhost:8080
- **Swagger UI**: http://localhost:8080/swagger-ui.html
- **Yjs Service**: http://localhost:3001
- **Yjs WebSocket**: ws://localhost:3001/ws/yjs/{documentId}?token={jwt}
- **PostgreSQL**: localhost:5433 (mapped from container's 5432)
- **Redis**: localhost:6379

See [DOCKER_SETUP.md](DOCKER_SETUP.md) for details.

---

## ✅ Implemented Features

### 1. User Authentication (Spring Boot)

**Register with OTP:**
```bash
curl -X POST http://localhost:8080/api/auth/register \
  -H "Content-Type: application/json" \
  -d '{"firstName":"John","lastName":"Doe","email":"john@example.com","password":"Pass123!"}'

curl -X POST http://localhost:8080/api/auth/verify-otp \
  -H "Content-Type: application/json" \
  -c cookies.txt \
  -d '{"email":"john@example.com","otp":"123456"}'
```

**Login:**
```bash
curl -X POST http://localhost:8080/api/auth/login \
  -H "Content-Type: application/json" \
  -c cookies.txt \
  -d '{"email":"john@example.com","password":"Pass123!"}'
```

**Features:**
- ✅ Email OTP verification
- ✅ JWT tokens (HttpOnly cookies)
- ✅ Password reset flow
- ✅ Logout endpoint

---

### 2. Document Management (Spring Boot)

**Create Document:**
```bash
curl -X POST http://localhost:8080/api/documents/create \
  -H "Content-Type: application/json" \
  -b cookies.txt \
  -d '{"title":"My Document"}'
```

**Upload DOCX/PDF:**
```bash
curl -X POST http://localhost:8080/api/documents/upload \
  -b cookies.txt \
  -F "file=@document.docx" \
  -F "title=Imported"
```

**Features:**
- ✅ Create blank HTML documents
- ✅ Upload DOCX/PDF with content extraction
- ✅ List user documents (paginated)
- ✅ Document visibility (PRIVATE/SHARED/PUBLIC)
- ✅ Soft delete

---

### 3. Real-Time Collaboration (Node.js Yjs Service)

**WebSocket Connection (with JWT):**
```javascript
// 1. Login to get JWT
const res = await fetch('http://localhost:8080/api/auth/login', {
  method: 'POST',
  credentials: 'include',
  body: JSON.stringify({ email: 'user@example.com', password: 'Pass123!' })
});

// 2. Extract JWT from cookie
const jwt = document.cookie.split('; ')
  .find(row => row.startsWith('JWT='))
  ?.split('=')[1];

// 3. Connect to Yjs service
const ws = new WebSocket(`ws://localhost:3000/ws/yjs/${documentId}?token=${jwt}`);

ws.onopen = () => console.log('Connected with authentication!');
ws.onmessage = (event) => {
  // Receive Yjs CRDT updates
  const update = new Uint8Array(event.data);
};
```

**Yjs Service API:**
```bash
# Health check
curl http://localhost:3000/health

# Get active users in document
curl http://localhost:3000/api/documents/{document-id}/users
```

**Features:**
- ✅ **JWT Authentication** - Required for all connections
- ✅ **Yjs CRDT Sync** - Full y-protocols implementation
- ✅ **Awareness Protocol** - User presence tracking
- ✅ **Redis Persistence** - Auto-save every 5 minutes
- ✅ **Active Users API** - Real-time user tracking
- ✅ **Heartbeat Monitoring** - 30s ping/pong
- ✅ **Graceful Shutdown** - Proper cleanup on restart

---

## 🔒 Security Features

### Authentication & Authorization
- ✅ JWT tokens with HttpOnly cookies
- ✅ WebSocket JWT authentication (query param/cookie/header)
- ✅ Document ownership verification
- ✅ BCrypt password hashing (10 rounds)
- ✅ Email OTP verification

### Security Headers
- ✅ CORS configuration
- ✅ CSRF disabled (stateless JWT)
- ✅ Environment-based CORS origins

### What's Missing
- ❌ RBAC (Owner/Editor/Viewer roles)
- ❌ Rate limiting
- ❌ API key authentication
- ❌ Document sharing permissions

---

## 🗄️ Database Schema

### users
```sql
id, first_name, last_name, email, password_hash,
created_at, provider, provider_id, account_non_locked
```

### documents
```sql
id, title, file_name, content_type, content,
yjs_room_id, owner_id, visibility, is_deleted,
created_at, updated_at, yjs_snapshot, file_size
```

### pending_users
```sql
email, first_name, last_name, password_hash, otp, created_at
```

### email_otps
```sql
email, otp, purpose, created_at, expires_at
```

---

## 🔧 Technology Stack

### Backend (Spring Boot)
- Java 17
- Spring Boot 3.5.0
- PostgreSQL + JPA
- Redis (Lettuce)
- JWT (jjwt 0.12.3)
- Mail (Gmail SMTP)
- Apache POI, docx4j, PDFBox

### Collaboration Service (Node.js)
- Node.js 18+
- Express.js
- WebSocket (`ws`)
- Yjs + y-protocols
- Redis (ioredis)
- JWT (jsonwebtoken)
- Winston logging

### Infrastructure
- Docker + Docker Compose
- PostgreSQL 15
- Redis 7

---

## 📁 Project Structure

```
collab-docs/
├── src/main/java/              # Spring Boot backend
│   ├── controller/             # REST endpoints
│   ├── service/                # Business logic
│   ├── entities/               # JPA entities
│   ├── repository/             # Data access
│   ├── security/               # JWT, auth
│   └── config/                 # Configuration
├── yjs-service/                # Node.js microservice
│   ├── server.js               # WebSocket server
│   ├── config/logger.js        # Winston logging
│   ├── utils/auth.js           # JWT validation
│   ├── services/
│   │   ├── yjsHandler.js       # Yjs CRDT operations
│   │   └── redisAdapter.js     # Redis persistence
│   ├── package.json
│   └── Dockerfile
├── docker-compose.yml          # Full stack orchestration
├── Dockerfile                  # Spring Boot container
├── .env.example                # Environment template
└── DOCKER_SETUP.md             # Docker guide
```

---

## ❌ Not Yet Implemented

**High Priority:**
- RBAC system (roles, permissions)
- Document sharing (collaborators table)
- Rate limiting
- Audit logging

**Medium Priority:**
- Document versioning
- Export APIs (PDF, DOCX)
- Email invitations
- Public share links

**Low Priority:**
- Anonymous document access
- Real-time cursors
- Comments/annotations

---

## 📊 Known Issues

**Fixed:**
- ✅ GraalVM Yjs code removed (~800 lines)
- ✅ WebSocket authentication implemented
- ✅ User presence tracking working
- ✅ Redis persistence functional

**Current:**
- ⚠️ No RBAC - only owner can access documents
- ⚠️ No rate limiting on APIs
- ⚠️ No PostgreSQL snapshot persistence (only Redis)

---

## 🚀 Next Phase: RBAC & Permissions

**Phase 4 Goals:**
1. Role-based access control (Owner, Editor, Viewer)
2. Document collaborators table
3. Share endpoints
4. Permission checks in Yjs service
5. Invitation system

---

## 📝 Update Log

| Date | Phase | Changes |
|------|-------|---------|
| 2026-02-08 | Phase 1 | ✅ Docker setup (compose, health checks, test script) |
| 2026-02-08 | Phase 2 | ✅ Node.js Yjs service with JWT, Redis, awareness |
| 2026-02-08 | Phase 3 | ✅ Removed GraalVM code, cleaned up WebSocket config |

---

## 🧪 Quick Test

```bash
# 1. Register
curl -X POST http://localhost:8080/api/auth/register \
  -H "Content-Type: application/json" \
  -d '{"firstName":"Test","lastName":"User","email":"test@example.com","password":"Test123!"}'

# 2. Get OTP from logs
docker-compose logs backend | grep OTP

# 3. Verify
curl -X POST http://localhost:8080/api/auth/verify-otp \
  -H "Content-Type: application/json" \
  -c cookies.txt \
  -d '{"email":"test@example.com","otp":"YOUR_OTP"}'

# 4. Create document
curl -X POST http://localhost:8080/api/documents/create \
  -H "Content-Type: application/json" \
  -b cookies.txt \
  -d '{"title":"Test Doc"}'

# 5. Extract JWT
JWT=$(grep -oP 'JWT=\K[^;]+' cookies.txt)

# 6. Connect to Yjs (use documentId from step 4 response)
# websocat "ws://localhost:3000/ws/yjs/{documentId}?token=$JWT"
```
