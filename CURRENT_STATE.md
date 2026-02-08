# Collab-Docs - Current Implementation State

**Last Updated:** 2026-02-08  
**Version:** Phase 2 Complete - Node.js Yjs Service + WebSocket Auth

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
- **Yjs Service**: http://localhost:3000
- **Yjs WebSocket**: ws://localhost:3000/ws/yjs/{documentId}
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

### 3. Real-Time Collaboration (WebSocket) - ✅ NOW AUTHENTICATED!

**Yjs Service Endpoints:**

#### **Health Check**
```bash
curl http://localhost:3000/health

# Response:
# {
#   "status": "UP",
#   "service": "yjs-collaboration",
#   "timestamp": "2026-02-08T10:45:00.000Z",
#   "stats": {
#     "activeDocuments": 5,
#     "activeAwarenesses": 5
#   }
# }
```

#### **Get Active Users in Document**
```bash
curl http://localhost:3000/api/documents/uuid-room-id/users

# Response:
# {
#   "users": [
#     {
#       "userId": "1",
#       "name": "John Doe",
#       "email": "john@example.com",
#       "joinedAt": 1707392700000
#     }
#   ],
#   "count": 1
# }
```

#### **WebSocket Connection** (with JWT Authentication)

```javascript
// 1. First, login to get JWT token
const loginResponse = await fetch('http://localhost:8080/api/auth/login', {
  method: 'POST',
  headers: { 'Content-Type': 'application/json' },
  credentials: 'include',
  body: JSON.stringify({
    email: 'john.doe@example.com',
    password: 'SecurePass123!'
  })
});

// 2. Extract JWT from cookie or use it directly
const token = 'your-jwt-token';  // From cookie or response

// 3. Connect to Yjs service with authentication
const documentId = 'uuid-room-id';
const ws = new WebSocket(`ws://localhost:3000/ws/yjs/${documentId}?token=${token}`);

ws.onopen = () => {
  console.log('Connected to document with authentication!');
};

ws.onmessage = (event) => {
  const data = new Uint8Array(event.data);
  console.log('Received Yjs update:', data);
};

ws.onerror = (error) => {
  console.error('WebSocket error:', error);
};

ws.onclose = (event) => {
  console.log('Disconnected:', event.code, event.reason);
};
```

**Alternative: Authorization Header**
```javascript
const ws = new WebSocket('ws://localhost:3000/ws/yjs/uuid-room-id', {
  headers: {
    'Authorization': `Bearer ${token}`
  }
});
```

**Security Features:**
- ✅ JWT authentication required
- ✅ Token validated before connection
- ✅ User permissions checked
- ✅ Heartbeat monitoring (30s intervals)
- ✅ Graceful disconnection handling

---

## ❌ Not Yet Implemented

- RBAC (roles, permissions) - **Phase 3 Priority**
- Document sharing (links, invitations) - **Phase 4**
- Document versioning - **Phase 5**
- Audit logging - **Phase 6**
- Export APIs - **Phase 7**
- Rate limiting - **Phase 8**
- ~~WebSocket authentication~~ ✅ **DONE in Phase 2**
- ~~Presence awareness~~ ✅ **Basic implementation in Phase 2**

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

**Fixed in Phase 2:**
- ✅ WebSocket authentication - **FIXED**
- ✅ No user tracking - **FIXED (Redis presence tracking)**

**Critical:**
- ❌ No permission system (RBAC)
- ❌ No rate limiting

**High:**
- No audit logging
- No versioning
- ~~GraalVM Yjs needs replacement~~ ✅ **REPLACED with Node.js service**

---

## 🚀 Next: Phase 3 - RBAC & Permissions

Phase 2 Complete! ✅

Next will implement:
- Role-based access control (Owner, Editor, Viewer)
- Document permissions management
- Share/collaboration permissions
- Permission checks in WebSocket connections

---

## 📝 Update Log

| Date | Phase | Changes |
|------|-------|---------|
| 2026-02-08 | Baseline | Initial state documentation |
| 2026-02-08 | Phase 1 | ✅ Docker setup complete (compose, Dockerfile, test script) |
| 2026-02-08 | Phase 2 | ✅ Node.js Yjs service with WebSocket authentication, Redis persistence |
