# Yjs Collaboration Service

Real-time collaborative editing service built with Yjs CRDT, WebSocket, and Redis persistence.

## Features

✅ **Real-time Collaboration**: Multiple users can edit the same document simultaneously  
✅ **JWT Authentication**: Secure WebSocket connections with token validation  
✅ **Conflict-free Editing**: Yjs CRDT ensures consistent state across all clients  
✅ **Presence Awareness**: Track active users and their cursors  
✅ **Redis Persistence**: Automatic document state saving and recovery  
✅ **Health Monitoring**: Health check endpoint and heartbeat monitoring  
✅ **Production Ready**: Structured logging, error handling, graceful shutdown  

## Architecture

```
Client (Browser)
    │
    ├──> JWT Token in query param or header
    │
    ▼
WebSocket Server (server.js)
    │
    ├──> Auth Middleware (utils/auth.js)
    ├──> Yjs Handler (services/yjsHandler.js)
    │       ├──> Y.Doc management
    │       ├──> CRDT sync protocol
    │       └──> Awareness protocol
    │
    └──> Redis Adapter (services/redisAdapter.js)
            ├──> Document state persistence
            └──> Active user tracking
```

## API Endpoints

### Health Check
```
GET /health
```
Returns service status and statistics.

### Active Users
```
GET /api/documents/:documentId/users
```
Returns list of currently active users in a document.

### WebSocket Endpoint
```
ws://localhost:3000/ws/yjs/{documentId}?token={JWT_TOKEN}
```

Or with Authorization header:
```
Authorization: Bearer {JWT_TOKEN}
```

## Environment Variables

See `.env.example` for all configuration options.

**Critical:**
- `JWT_SECRET` - Must match Spring Boot backend secret!
- `REDIS_HOST`, `REDIS_PASSWORD` - Must connect to same Redis as backend

## Development

### Local Setup
```bash
cd yjs-service
npm install
cp .env.example .env
# Edit .env with credentials
npm start
```

### With Docker
```bash
# From project root
docker-compose up --build yjs-service
```

## Testing

### Test WebSocket Connection (JavaScript)
```javascript
const jwt = 'your-jwt-token-here';
const documentId = 'doc-uuid';
const ws = new WebSocket(`ws://localhost:3000/ws/yjs/${documentId}?token=${jwt}`);

ws.onopen = () => {
  console.log('Connected!');
};

ws.onmessage = (event) => {
  console.log('Received:', event.data);
};
```

### Test Health Endpoint
```bash
curl http://localhost:3000/health
```

## Protocol

This service implements the Yjs sync protocol and awareness protocol:

**Sync Protocol** (MESSAGE_SYNC = 0):
- Step 1: Client sends state vector
- Step 2: Server sends missing updates
- Updates: Incremental document changes

**Awareness Protocol** (MESSAGE_AWARENESS = 1):
- Broadcasts user presence (cursor, selection, name)
- Real-time presence updates

## Logging

Uses Winston for structured logging:
- **Console**: Colored, human-readable
- **File** (production): `logs/error.log`, `logs/combined.log`
- **Levels**: error, warn, info, debug

## Performance

- **Auto-save**: Documents saved to Redis every 5 minutes
- **Heartbeat**: 30-second ping/pong to detect broken connections
- **Memory**: In-memory Y.Doc per active document
- **Redis TTL**: Cached states expire after 24 hours

## Security

✅ JWT authentication on WebSocket upgrade  
✅ User permissions validated before connection  
✅ No unauthenticated access  
✅ Token in query param or header (not in message body)  

## Troubleshooting

**Connection refused:**
- Check JWT_SECRET matches backend
- Verify Redis is running
- Check token is valid and not expired

**No sync messages:**
- Ensure document exists in backend
- Check user has permission to access document
- Verify WebSocket connection is established

**Redis errors:**
- Confirm Redis credentials in .env
- Check Redis is accessible from container

## Integration with Spring Boot

The Spring Boot backend should proxy WebSocket connections or redirect clients to this service.

WebSocket path format: `/ws/yjs/{documentId}`

JWT must be obtained from Spring Boot `/api/auth/login` endpoint.

---

**Built with:**
- [Yjs](https://github.com/yjs/yjs) - CRDT framework
- [ws](https://github.com/websockets/ws) - WebSocket library
- [ioredis](https://github.com/redis/ioredis) - Redis client
- [Winston](https://github.com/winstonjs/winston) - Logging
