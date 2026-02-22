# Collab-Docs API Contract v2.1

**Last Updated:** February 22, 2026  
**Base URL:** `http://localhost:8080`  
**WebSocket URL:** `ws://localhost:3000`  
**API Version:** 2.1  
**Authentication:** JWT (HttpOnly Cookie + token in login response for WebSocket)

---

## 📋 Table of Contents

1. [Authentication & Authorization](#1-authentication--authorization)
2. [Document Management](#2-document-management)
3. [Real-Time Collaboration (WebSocket)](#3-real-time-collaboration-websocket)
4. [Document Versioning](#4-document-versioning)
5. [Collaborator Management](#5-collaborator-management)
6. [Document Sharing](#6-document-sharing)
7. [Error Handling](#7-error-handling)
8. [Rate Limiting](#8-rate-limiting)
9. [Frontend Integration Guide](#9-frontend-integration-guide)

---

## 🔐 1. Authentication & Authorization

All authenticated endpoints require JWT token in HttpOnly cookie.

### 1.1 Register New User

**Endpoint:** `POST /api/auth/register`  
**Auth Required:** No  
**Description:** Initiate user registration with email OTP verification

**Request Body:**
```json
{
  "firstName": "John",
  "lastName": "Doe",
  "email": "john.doe@example.com",
  "password": "SecurePass123!"
}
```

**Validation Rules:**
- `firstName`: 2-50 characters, required
- `lastName`: 2-50 characters, required
- `email`: Valid email format, required, unique
- `password`: Min 8 chars, must contain uppercase, lowercase, number, special char

**Success Response (200 OK):**
```json
{
  "message": "Registration initiated successfully! Please check your email for the verification code."
}
```

**Error Responses:**
- `400 Bad Request`: Validation failed or email already exists
- `500 Internal Server Error`: Server error

---

### 1.2 Verify Email OTP

**Endpoint:** `POST /api/auth/verify-otp`  
**Auth Required:** No  
**Description:** Complete registration by verifying OTP sent to email

**Request Body:**
```json
{
  "email": "john.doe@example.com",
  "otp": "123456"
}
```

**Success Response (200 OK):**
```json
{
  "message": "Email verified successfully! Your account has been created. You can now login."
}
```

**Error Responses:**
- `400 Bad Request`: Invalid OTP or expired
- `500 Internal Server Error`: Server error

**OTP Rules:**
- Valid for 10 minutes
- Max 3 attempts per OTP
- After 3 failed attempts: 30-minute cooldown

---

### 1.3 Resend OTP

**Endpoint:** `POST /api/auth/resend-otp`  
**Auth Required:** No  
**Description:** Resend verification OTP to email

**Request Body:**
```json
{
  "email": "john.doe@example.com"
}
```

**Success Response (200 OK):**
```json
{
  "message": "Verification code has been resent to your email."
}
```

**Rate Limit:** 5 requests per hour per email

---

### 1.4 Login

**Endpoint:** `POST /api/auth/login`  
**Auth Required:** No  
**Description:** Authenticate user and receive JWT token in HttpOnly cookie. The raw token is also returned in the response body exclusively for WebSocket authentication (the Yjs service runs on a different origin so it cannot access Spring Boot's HttpOnly cookie).

**Request Body:**
```json
{
  "email": "john.doe@example.com",
  "password": "SecurePass123!"
}
```

**Success Response (200 OK):**
```json
{
  "id": 1,
  "email": "john.doe@example.com",
  "firstName": "John",
  "lastName": "Doe",
  "message": "Login successful. JWT Token is added to cookies.",
  "token": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9..."
}
```

> **Note on `token` field:** The JWT is set as an `HttpOnly` cookie for all Spring Boot API calls (browser sends it automatically). The `token` field in the response body exists **only** so the frontend can pass it to the Yjs WebSocket service (`ws://localhost:3000`), which runs on a different port and therefore cannot read the HttpOnly cookie. Store this token in memory/Zustand — do **not** put it in `localStorage`.

**Set-Cookie Header:**
```
jwt=eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...; HttpOnly; SameSite=Lax; Max-Age=86400; Path=/
```

**Error Responses:**
- `401 Unauthorized`: Invalid credentials or account disabled
- `500 Internal Server Error`: Server error

---

### 1.5 Get Current User

**Endpoint:** `GET /api/auth/me`  
**Auth Required:** Yes (JWT Cookie)  
**Description:** Get authenticated user information

**Success Response (200 OK):**
```json
{
  "id": 1,
  "email": "john.doe@example.com",
  "firstName": "John",
  "lastName": "Doe",
  "message": "User data retrieved successfully!"
}
```

**Error Responses:**
- `401 Unauthorized`: Not authenticated or invalid token

---

### 1.6 Logout

**Endpoint:** `POST /api/auth/logout`  
**Auth Required:** Yes  
**Description:** Clear authentication and remove JWT cookie

**Success Response (200 OK):**
```json
{
  "message": "Logout successful!"
}
```

**Set-Cookie Header:** (Clears cookie)
```
jwt=; HttpOnly; Secure; SameSite=Lax; Max-Age=0; Path=/
```

---

### 1.7 Refresh Token

**Endpoint:** `POST /api/auth/refresh`  
**Auth Required:** Yes  
**Description:** Generate new JWT token with extended expiration

**Success Response (200 OK):**
```json
{
  "message": "Token refreshed successfully!"
}
```

**Set-Cookie Header:** New JWT with fresh expiration

---

### 1.8 Validate Token

**Endpoint:** `POST /api/auth/validate`  
**Auth Required:** Yes  
**Description:** Check if current JWT token is valid

**Success Response (200 OK):**
```json
{
  "message": "Token is valid!"
}
```

**Error Responses:**
- `401 Unauthorized`: Invalid or expired token

---

### 1.9 Forgot Password

**Endpoint:** `POST /api/auth/forgot-password`  
**Auth Required:** No  
**Description:** Request password reset OTP via email

**Request Body:**
```json
{
  "email": "john.doe@example.com"
}
```

**Success Response (200 OK):**
```json
{
  "message": "Password reset instructions have been sent to your email address."
}
```

**Rate Limit:** 5 requests per hour per email

---

### 1.10 Reset Password

**Endpoint:** `POST /api/auth/reset-password`  
**Auth Required:** No  
**Description:** Reset password using OTP

**Request Body:**
```json
{
  "email": "john.doe@example.com",
  "otp": "123456",
  "newPassword": "NewSecurePass123!"
}
```

**Success Response (200 OK):**
```json
{
  "message": "Password has been reset successfully! You can now login with your new password."
}
```

**Error Responses:**
- `400 Bad Request`: Invalid OTP or password validation failed

---

## 📄 2. Document Management

### 2.1 Create New Document

**Endpoint:** `POST /api/documents/create`  
**Auth Required:** Yes  
**Description:** Create a new blank document with Yjs support

**Request Body:**
```json
{
  "title": "My Project Document",
  "visibility": "PRIVATE"
}
```

**Validation:**
- `title`: 1-255 characters, required
- `visibility`: PRIVATE | SHARED | PUBLIC (optional, default: PRIVATE)

**Success Response (201 Created):**
```json
{
  "id": 42,
  "title": "My Project Document",
  "yjsRoomId": "doc_1707825600_abc123def456",
  "ownerEmail": "john.doe@example.com",
  "ownerId": 1,
  "visibility": "PRIVATE",
  "createdAt": "2026-02-13T10:30:00",
  "updatedAt": "2026-02-13T10:30:00",
  "isDeleted": false,
  "fileSize": 0
}
```

**Key Fields:**
- `yjsRoomId`: Unique identifier for WebSocket collaboration
- `id`: Document database ID
- `visibility`: Access level (PRIVATE/SHARED/PUBLIC)

**Error Responses:**
- `400 Bad Request`: Validation failed
- `401 Unauthorized`: Not authenticated
- `500 Internal Server Error`: Server error

---

### 2.2 Upload Document (DOCX/PDF)

**Endpoint:** `POST /api/documents/upload`  
**Auth Required:** Yes  
**Content-Type:** `multipart/form-data`  
**Description:** Upload and convert DOCX/PDF to collaborative document

**Request (Form Data):**
```
file: [Binary file]
title: "Imported Document" (optional)
visibility: "PRIVATE" (optional)
```

**Supported Formats:**
- `.docx` (Microsoft Word)
- `.pdf` (Portable Document Format)
- Max size: 10MB

**Success Response (201 Created):**
```json
{
  "id": 43,
  "title": "Imported Document",
  "yjsRoomId": "doc_1707825700_xyz789",
  "ownerEmail": "john.doe@example.com",
  "ownerId": 1,
  "fileName": "document.docx",
  "contentType": "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
  "visibility": "PRIVATE",
  "fileSize": 1024000,
  "createdAt": "2026-02-13T10:35:00",
  "updatedAt": "2026-02-13T10:35:00"
}
```

**Error Responses:**
- `400 Bad Request`: Invalid file format or size exceeded
- `401 Unauthorized`: Not authenticated
- `413 Payload Too Large`: File size > 10MB

---

### 2.3 List User Documents

**Endpoint:** `GET /api/documents/list`  
**Auth Required:** Yes  
**Description:** Get paginated list of user's accessible documents

**Query Parameters:**
```
page: 0 (default: 0)
size: 20 (default: 20, max: 100)
sort: updatedAt (default: updatedAt)
direction: desc (default: desc, options: asc|desc)
visibility: PRIVATE (optional filter: PRIVATE|SHARED|PUBLIC)
search: "keyword" (optional: search in title)
```

**Example Request:**
```
GET /api/documents/list?page=0&size=20&sort=updatedAt&direction=desc&search=project
```

**Success Response (200 OK):**
```json
{
  "content": [
    {
      "id": 42,
      "title": "My Project Document",
      "yjsRoomId": "doc_1707825600_abc123def456",
      "ownerEmail": "john.doe@example.com",
      "ownerId": 1,
      "visibility": "PRIVATE",
      "createdAt": "2026-02-13T10:30:00",
      "updatedAt": "2026-02-13T11:45:00",
      "fileSize": 2048,
      "isDeleted": false
    }
  ],
  "pageable": {
    "pageNumber": 0,
    "pageSize": 20,
    "sort": {
      "sorted": true,
      "unsorted": false,
      "empty": false
    },
    "offset": 0,
    "paged": true,
    "unpaged": false
  },
  "totalElements": 15,
  "totalPages": 1,
  "last": true,
  "first": true,
  "size": 20,
  "number": 0,
  "numberOfElements": 15,
  "empty": false
}
```

---

### 2.4 Get Document Details

**Endpoint:** `GET /api/documents/{id}`  
**Auth Required:** Yes  
**Description:** Get detailed information about a specific document

**Path Parameters:**
- `id`: Document ID

**Success Response (200 OK):**
```json
{
  "id": 42,
  "title": "My Project Document",
  "yjsRoomId": "doc_1707825600_abc123def456",
  "ownerEmail": "john.doe@example.com",
  "ownerId": 1,
  "fileName": null,
  "contentType": "text/html",
  "visibility": "PRIVATE",
  "createdAt": "2026-02-13T10:30:00",
  "updatedAt": "2026-02-13T11:45:00",
  "fileSize": 2048,
  "isDeleted": false
}
```

**Error Responses:**
- `403 Forbidden`: No permission to access document
- `404 Not Found`: Document doesn't exist

---

### 2.5 Update Document

**Endpoint:** `PUT /api/documents/{id}`  
**Auth Required:** Yes  
**Permission Required:** EDITOR or OWNER  
**Description:** Update document metadata

**Request Body:**
```json
{
  "title": "Updated Title",
  "visibility": "SHARED"
}
```

**Success Response (200 OK):**
```json
{
  "id": 42,
  "title": "Updated Title",
  "visibility": "SHARED",
  "updatedAt": "2026-02-13T12:00:00"
}
```

---

### 2.6 Delete Document (Soft Delete)

**Endpoint:** `DELETE /api/documents/{id}`  
**Auth Required:** Yes  
**Permission Required:** OWNER  
**Description:** Soft delete document (mark as deleted)

**Success Response (204 No Content)**

**Error Responses:**
- `403 Forbidden`: Only owner can delete
- `404 Not Found`: Document doesn't exist

---

### 2.7 Save Yjs Snapshot (Internal)

**Endpoint:** `POST /api/documents/yjs-snapshot?yjsRoomId={yjsRoomId}`  
**Auth Required:** Internal Service  
**Content-Type:** `application/octet-stream`  
**Description:** Save binary Yjs CRDT snapshot (called by Node.js service)

**Request Body:** Binary Yjs state

**Success Response (200 OK):**
```json
{
  "message": "Snapshot saved successfully"
}
```

---

### 2.8 Load Yjs Snapshot (Internal)

**Endpoint:** `GET /api/documents/yjs-snapshot/{yjsRoomId}`  
**Auth Required:** Internal Service  
**Content-Type:** `application/octet-stream`  
**Description:** Load binary Yjs CRDT snapshot

**Success Response (200 OK):**
- Body: Binary Yjs state
- Header: `Content-Type: application/octet-stream`

**Response (404 No Content):** If no snapshot exists

---

## 🔄 3. Real-Time Collaboration (WebSocket)

### 3.1 WebSocket Connection

**Endpoint:** `ws://localhost:3000/ws/yjs/{yjsRoomId}`  
**Auth Required:** Yes (JWT as `?token=` query parameter)  
**Protocol:** Yjs CRDT over WebSocket  
**Description:** Real-time document collaboration

> **Why not the HttpOnly cookie?** The JWT cookie is marked `HttpOnly`, meaning JavaScript cannot read it — this is intentional XSS protection. Even if it were readable, the Yjs service runs on `localhost:3000` (a different origin from the Spring Boot backend at `localhost:8080`), so the browser would not send that cookie with WebSocket requests to port 3000. The solution is to use the `token` returned in the **login response body** and pass it as a query parameter.

**Connection via y-websocket (recommended):**
```javascript
// token comes from the login response body (stored in Zustand / app memory)
const { token } = useAuthStore.getState();

const provider = new WebsocketProvider(
  'ws://localhost:3000/ws/yjs',
  yjsRoomId,
  ydoc,
  { params: { token } }  // appended as ?token=<jwt>
);
```

**Raw WebSocket (alternative):**
```javascript
const ws = new WebSocket(
  `ws://localhost:3000/ws/yjs/doc_1707825600_abc123?token=${token}`
);
```

**Connection Success:**
- Server sends: `MESSAGE_SYNC_STEP1` (initial document state vector)
- Client sends: `MESSAGE_SYNC_STEP2` (client state)
- Server sends: `MESSAGE_SYNC_STEP2` (state diff / missing updates)

**Message Types:**
- `MESSAGE_SYNC` (0): Document synchronization
- `MESSAGE_AWARENESS` (1): User presence/cursor

**Heartbeat:**
- Ping every 30 seconds
- Pong response required
- Timeout: 60 seconds

**Error Responses:**
- `401 Unauthorized`: Invalid or missing JWT
- `403 Forbidden`: No permission to access document
- `400 Bad Request`: Invalid WebSocket path

---

### 3.2 TipTap + React Integration

**Key pattern (React/Zustand):** Create the `WebsocketProvider` inside a `useEffect` that waits for the token to be available. Do **not** create it in a `useState` initializer — Zustand `persist` rehydrates from localStorage after the first render, so the token would be `null` on that first call, causing a 401 and an infinite reconnect loop.

```javascript
import { useEffect, useRef, useState } from 'react';
import * as Y from 'yjs';
import { WebsocketProvider } from 'y-websocket';
import { useAuthStore } from '../store/authStore';

function TipTapEditor({ yjsRoomId }) {
  const { user, token } = useAuthStore();
  const [ydoc] = useState(() => new Y.Doc());
  const [provider, setProvider] = useState(null);
  const providerRef = useRef(null);

  // Create provider only after token is available
  useEffect(() => {
    if (!token || providerRef.current) return;

    const p = new WebsocketProvider(
      'ws://localhost:3000/ws/yjs',
      yjsRoomId,
      ydoc,
      { params: { token } }
    );
    providerRef.current = p;
    setProvider(p);
  }, [token, yjsRoomId, ydoc]);

  // Cleanup on unmount
  useEffect(() => {
    return () => providerRef.current?.disconnect();
  }, []);

  if (!provider) return <Spinner />;

  // Pass provider to TipTap CollaborationCursor extension...
}
```

---

### 3.3 Active Users API

**Endpoint:** `GET http://localhost:3000/api/documents/{yjsRoomId}/users`  
**Auth Required:** No (public stats)  
**Description:** Get list of currently connected users

**Success Response (200 OK):**
```json
{
  "documentId": "doc_1707825600_abc123",
  "activeUsers": 3,
  "users": [
    {
      "userId": 1,
      "email": "john.doe@example.com",
      "name": "John Doe",
      "connectedAt": "2026-02-13T10:30:00"
    },
    {
      "userId": 2,
      "email": "jane.smith@example.com",
      "name": "Jane Smith",
      "connectedAt": "2026-02-13T10:32:00"
    }
  ]
}
```

---

### 3.4 Service Health Check

**Endpoint:** `GET http://localhost:3000/health`  
**Auth Required:** No  
**Description:** Check Yjs service health and statistics

**Success Response (200 OK):**
```json
{
  "status": "healthy",
  "uptime": 86400,
  "activeDocuments": 15,
  "activeConnections": 42,
  "memoryUsage": {
    "heapUsed": 125829120,
    "heapTotal": 268435456
  },
  "timestamp": "2026-02-13T12:00:00"
}
```

---

## 📚 4. Document Versioning

### 4.1 Create Version

**Endpoint:** `POST /api/documents/{documentId}/versions`  
**Auth Required:** Yes  
**Permission Required:** EDITOR or OWNER  
**Description:** Create a manual snapshot/checkpoint of current document state

**Request Body:**
```json
{
  "versionName": "Final Draft",
  "comment": "Completed all revisions, ready for review"
}
```

**Validation:**
- `versionName`: 1-100 characters, required
- `comment`: Max 500 characters, optional

**Success Response (201 Created):**
```json
{
  "id": 5,
  "documentId": 42,
  "versionNumber": 5,
  "versionName": "Final Draft",
  "comment": "Completed all revisions, ready for review",
  "sizeBytes": 2048,
  "createdBy": "John Doe",
  "createdById": 1,
  "createdAt": "2026-02-13T12:00:00"
}
```

**Error Responses:**
- `400 Bad Request`: Validation failed or version limit reached
- `403 Forbidden`: No edit permission
- `404 Not Found`: Document doesn't exist

---

### 4.2 List Versions

**Endpoint:** `GET /api/documents/{documentId}/versions`  
**Auth Required:** Yes  
**Permission Required:** VIEWER or higher  
**Description:** Get all versions of a document

**Success Response (200 OK):**
```json
{
  "documentId": 42,
  "totalVersions": 5,
  "versions": [
    {
      "id": 5,
      "versionNumber": 5,
      "versionName": "Final Draft",
      "comment": "Completed all revisions",
      "sizeBytes": 2048,
      "createdBy": "John Doe",
      "createdById": 1,
      "createdAt": "2026-02-13T12:00:00"
    },
    {
      "id": 4,
      "versionNumber": 4,
      "versionName": "Second Review",
      "sizeBytes": 1920,
      "createdBy": "Jane Smith",
      "createdById": 2,
      "createdAt": "2026-02-12T15:30:00"
    }
  ]
}
```

---

### 4.3 Get Version Content

**Endpoint:** `GET /api/versions/{versionId}`  
**Auth Required:** Yes  
**Permission Required:** VIEWER or higher  
**Description:** Get specific version with content

**Success Response (200 OK):**
```json
{
  "id": 5,
  "versionNumber": 5,
  "versionName": "Final Draft",
  "comment": "Completed all revisions",
  "content": "<p>Document content here...</p>",
  "yjsSnapshot": "[Base64 encoded binary]",
  "sizeBytes": 2048,
  "createdBy": "John Doe",
  "createdAt": "2026-02-13T12:00:00"
}
```

**Frontend Usage:**
```javascript
// Load version in read-only mode
const version = await fetch(`/api/versions/${versionId}`).then(r => r.json());

// Display in editor (read-only)
editor.setOptions({ editable: false });
editor.commands.setContent(version.content);
```

---

### 4.4 Restore Version

**Endpoint:** `POST /api/versions/{versionId}/restore`  
**Auth Required:** Yes  
**Permission Required:** EDITOR or OWNER  
**Description:** Restore document to a previous version (creates new version)

**Success Response (200 OK):**
```json
{
  "message": "Document restored to version 3",
  "newVersionId": 6,
  "newVersionNumber": 6,
  "restoredFromVersion": 3
}
```

**Warning:** This creates a new version (doesn't delete history)

---

### 4.5 Delete Version

**Endpoint:** `DELETE /api/versions/{versionId}`  
**Auth Required:** Yes  
**Permission Required:** OWNER  
**Description:** Delete a specific version (cannot delete latest)

**Success Response (204 No Content)**

**Error Responses:**
- `400 Bad Request`: Cannot delete latest version
- `403 Forbidden`: Only owner can delete
- `404 Not Found`: Version doesn't exist

---

### 4.6 Get Version Statistics

**Endpoint:** `GET /api/documents/{documentId}/versions/stats`  
**Auth Required:** Yes  
**Permission Required:** VIEWER or higher  
**Description:** Get version count and storage statistics

**Success Response (200 OK):**
```json
{
  "documentId": 42,
  "totalVersions": 5,
  "totalSizeBytes": 10240,
  "totalSizeFormatted": "10.0 KB",
  "oldestVersion": {
    "versionNumber": 1,
    "createdAt": "2026-02-10T09:00:00"
  },
  "latestVersion": {
    "versionNumber": 5,
    "createdAt": "2026-02-13T12:00:00"
  }
}
```

---

## 👥 5. Collaborator Management

### 5.1 Add Collaborator

**Endpoint:** `POST /api/documents/{documentId}/collaborators`  
**Auth Required:** Yes  
**Permission Required:** EDITOR or OWNER  
**Description:** Grant access to another user

**Request Body:**
```json
{
  "userId": 2,
  "role": "EDITOR",
  "expiresInDays": 30
}
```

**Roles:**
- `VIEWER`: Read-only access
- `EDITOR`: Can edit document
- `OWNER`: Full control (transfer not allowed via this endpoint)

**Validation:**
- `userId`: Required, must exist
- `role`: Required, VIEWER | EDITOR
- `expiresInDays`: Optional, 1-365 days

**Success Response (201 Created):**
```json
{
  "id": 10,
  "documentId": 42,
  "userId": 2,
  "userEmail": "jane.smith@example.com",
  "userName": "Jane Smith",
  "role": "EDITOR",
  "grantedBy": "John Doe",
  "grantedAt": "2026-02-13T12:00:00",
  "expiresAt": "2026-03-15T12:00:00"
}
```

**Error Responses:**
- `400 Bad Request`: Validation failed or user already has access
- `403 Forbidden`: No permission to share
- `404 Not Found`: User or document not found

---

### 5.2 List Collaborators

**Endpoint:** `GET /api/documents/{documentId}/collaborators`  
**Auth Required:** Yes  
**Permission Required:** VIEWER or higher  
**Description:** Get all users with access to document

**Success Response (200 OK):**
```json
{
  "documentId": 42,
  "totalCollaborators": 3,
  "collaborators": [
    {
      "id": 1,
      "userId": 1,
      "userEmail": "john.doe@example.com",
      "userName": "John Doe",
      "role": "OWNER",
      "grantedAt": "2026-02-10T09:00:00",
      "expiresAt": null
    },
    {
      "id": 10,
      "userId": 2,
      "userEmail": "jane.smith@example.com",
      "userName": "Jane Smith",
      "role": "EDITOR",
      "grantedBy": "John Doe",
      "grantedAt": "2026-02-13T12:00:00",
      "expiresAt": "2026-03-15T12:00:00"
    }
  ]
}
```

---

### 5.3 Update Collaborator Role

**Endpoint:** `PUT /api/documents/{documentId}/collaborators/{userId}`  
**Auth Required:** Yes  
**Permission Required:** OWNER  
**Description:** Change collaborator's role or expiration

**Request Body:**
```json
{
  "role": "VIEWER",
  "expiresInDays": 60
}
```

**Success Response (200 OK):**
```json
{
  "id": 10,
  "role": "VIEWER",
  "expiresAt": "2026-04-13T12:00:00",
  "updatedAt": "2026-02-13T12:30:00"
}
```

---

### 5.4 Remove Collaborator

**Endpoint:** `DELETE /api/documents/{documentId}/collaborators/{userId}`  
**Auth Required:** Yes  
**Permission Required:** OWNER  
**Description:** Revoke user's access to document

**Success Response (204 No Content)**

**Error Responses:**
- `400 Bad Request`: Cannot remove last owner
- `403 Forbidden`: Only owner can remove
- `404 Not Found`: Collaborator not found

---

## 🔗 6. Document Sharing

### 6.1 Share Links

#### 6.1.1 Create Share Link

**Endpoint:** `POST /api/documents/{documentId}/share-links`  
**Auth Required:** Yes  
**Permission Required:** EDITOR or OWNER  
**Description:** Generate shareable URL with customizable access

**Request Body:**
```json
{
  "role": "VIEWER",
  "expiresInDays": 7,
  "maxUses": 10,
  "requiresAuth": false,
  "description": "Public view link for stakeholders"
}
```

**Validation:**
- `role`: VIEWER | EDITOR (required, cannot be OWNER)
- `expiresInDays`: 1-365 days (optional, null = no expiration)
- `maxUses`: 1-1000 (optional, null = unlimited)
- `requiresAuth`: boolean (default: false)
- `description`: Max 500 chars (optional)

**Success Response (201 Created):**
```json
{
  "id": 5,
  "token": "abc123xyz456def789ghi012",
  "shareUrl": "http://localhost:3000/share/abc123xyz456def789ghi012",
  "role": "VIEWER",
  "createdByName": "John Doe",
  "createdById": 1,
  "createdAt": "2026-02-13T12:00:00",
  "expiresAt": "2026-02-20T12:00:00",
  "maxUses": 10,
  "currentUses": 0,
  "remainingUses": 10,
  "isActive": true,
  "requiresAuth": false,
  "description": "Public view link for stakeholders",
  "isExpired": false,
  "isUsageLimitReached": false,
  "isValid": true
}
```

**Frontend Usage:**
```javascript
// Share via email, Slack, etc.
const shareUrl = response.shareUrl;
navigator.clipboard.writeText(shareUrl);
```

---

#### 6.1.2 List Share Links

**Endpoint:** `GET /api/documents/{documentId}/share-links?activeOnly=true`  
**Auth Required:** Yes  
**Permission Required:** VIEWER or higher  
**Description:** Get all share links for document

**Query Parameters:**
- `activeOnly`: true | false (default: false)

**Success Response (200 OK):**
```json
{
  "documentId": 42,
  "totalLinks": 3,
  "links": [
    {
      "id": 5,
      "token": "abc123xyz456",
      "shareUrl": "http://localhost:3000/share/abc123xyz456",
      "role": "VIEWER",
      "createdByName": "John Doe",
      "createdAt": "2026-02-13T12:00:00",
      "expiresAt": "2026-02-20T12:00:00",
      "currentUses": 3,
      "remainingUses": 7,
      "isActive": true,
      "isValid": true
    }
  ]
}
```

---

#### 6.1.3 Validate Share Link (Public)

**Endpoint:** `GET /api/share/{token}/validate`  
**Auth Required:** No (Public endpoint)  
**Description:** Check if share link is valid without granting access

**Success Response (200 OK):**
```json
{
  "token": "abc123xyz456",
  "role": "VIEWER",
  "requiresAuth": false,
  "isValid": true,
  "isExpired": false,
  "isUsageLimitReached": false,
  "expiresAt": "2026-02-20T12:00:00",
  "createdByName": "John Doe",
  "description": "Public view link"
}
```

**Error Responses:**
- `400 Bad Request`: Link expired or usage limit reached
- `404 Not Found`: Link doesn't exist

---

#### 6.1.4 Access Via Share Link

**Endpoint:** `POST /api/share/{token}/access`  
**Auth Required:** Yes  
**Description:** Grant permanent access using share link (Force Registration)

**Flow:**
1. Unauthenticated user clicks link
2. Frontend validates link (`GET /api/share/{token}/validate`)
3. Frontend shows login/register page
4. After authentication, call this endpoint
5. Backend creates permanent DocumentPermission
6. Returns document details

**Success Response (200 OK):**
```json
{
  "documentId": 42,
  "title": "My Project Document",
  "yjsRoomId": "doc_1707825600_abc123",
  "role": "VIEWER",
  "isAnonymous": false,
  "hasPermissionGranted": true,
  "message": "Access granted! You can now collaborate on this document",
  "ownerName": "John Doe",
  "accessExpiresAt": null
}
```

**Error Responses:**
- `401 Unauthorized`: User must login/register
  ```json
  {
    "error": "Authentication required",
    "message": "Please login or register to access this document",
    "requiresAuth": true,
    "loginUrl": "/api/auth/login",
    "registerUrl": "/api/auth/register"
  }
  ```

**Frontend Implementation:**
```javascript
// Step 1: Validate link
const validation = await fetch(`/api/share/${token}/validate`);
const linkInfo = await validation.json();

if (!linkInfo.isValid) {
  showError('This link is expired or invalid');
  return;
}

// Step 2: Check if user is logged in
if (!isLoggedIn()) {
  showLoginModal({
    documentTitle: linkInfo.documentTitle,
    owner: linkInfo.createdByName
  });
  return;
}

// Step 3: Grant access
const access = await fetch(`/api/share/${token}/access`, {
  method: 'POST',
  credentials: 'include'
});

const docInfo = await access.json();

// Step 4: Redirect to editor
window.location.href = `/document/${docInfo.documentId}`;
```

---

#### 6.1.5 Revoke Share Link

**Endpoint:** `POST /api/share-links/{linkId}/revoke`  
**Auth Required:** Yes  
**Permission Required:** Link creator or OWNER  
**Description:** Deactivate share link (soft delete for audit trail)

**Success Response (200 OK):**
```json
{
  "message": "Share link revoked successfully"
}
```

---

#### 6.1.6 Delete Share Link

**Endpoint:** `DELETE /api/share-links/{linkId}`  
**Auth Required:** Yes  
**Permission Required:** Link creator or OWNER  
**Description:** Permanently delete share link

**Success Response (204 No Content)**

---

### 6.2 Email Invitations

#### 6.2.1 Send Invitation

**Endpoint:** `POST /api/documents/{documentId}/invitations`  
**Auth Required:** Yes  
**Permission Required:** EDITOR or OWNER  
**Description:** Send collaboration invitation via email

**Request Body:**
```json
{
  "email": "colleague@example.com",
  "role": "EDITOR",
  "message": "Let's collaborate on this document!"
}
```

**Validation:**
- `email`: Valid email, required
- `role`: VIEWER | EDITOR (required, cannot be OWNER)
- `message`: Max 1000 chars (optional)

**Success Response (201 Created):**
```json
{
  "id": 15,
  "documentId": 42,
  "invitedEmail": "colleague@example.com",
  "invitedUserName": "Alice Johnson",
  "role": "EDITOR",
  "invitedBy": "John Doe",
  "invitedById": 1,
  "invitedAt": "2026-02-13T12:00:00",
  "expiresAt": "2026-02-20T12:00:00",
  "status": "PENDING",
  "message": "Let's collaborate on this document!",
  "token": "inv_abc123xyz456"
}
```

**Email Sent:**
```
Subject: John Doe invited you to collaborate on "My Project Document"

Hello,

John Doe has invited you to collaborate on the document "My Project Document" with EDITOR access.

Message: Let's collaborate on this document!

To accept this invitation:
http://localhost:3000/invitations/accept/inv_abc123xyz456

To decline:
http://localhost:3000/invitations/decline/inv_abc123xyz456

This invitation expires on February 20, 2026.

Best regards,
Collab-Docs Team
```

**Error Responses:**
- `400 Bad Request`: Duplicate invitation or validation failed
- `403 Forbidden`: No permission to share

---

#### 6.2.2 List Document Invitations

**Endpoint:** `GET /api/documents/{documentId}/invitations`  
**Auth Required:** Yes  
**Permission Required:** VIEWER or higher  
**Description:** Get all invitations for document

**Success Response (200 OK):**
```json
{
  "documentId": 42,
  "totalInvitations": 2,
  "invitations": [
    {
      "id": 15,
      "invitedEmail": "colleague@example.com",
      "role": "EDITOR",
      "invitedBy": "John Doe",
      "invitedAt": "2026-02-13T12:00:00",
      "status": "PENDING",
      "expiresAt": "2026-02-20T12:00:00"
    },
    {
      "id": 14,
      "invitedEmail": "friend@example.com",
      "role": "VIEWER",
      "invitedBy": "John Doe",
      "invitedAt": "2026-02-12T10:00:00",
      "respondedAt": "2026-02-12T11:30:00",
      "status": "ACCEPTED"
    }
  ]
}
```

---

#### 6.2.3 Get My Pending Invitations

**Endpoint:** `GET /api/invitations/pending`  
**Auth Required:** Yes  
**Description:** Get all pending invitations for current user

**Success Response (200 OK):**
```json
{
  "totalInvitations": 1,
  "invitations": [
    {
      "id": 15,
      "documentId": 42,
      "documentTitle": "My Project Document",
      "role": "EDITOR",
      "invitedBy": "John Doe",
      "invitedAt": "2026-02-13T12:00:00",
      "expiresAt": "2026-02-20T12:00:00",
      "message": "Let's collaborate!",
      "token": "inv_abc123xyz456"
    }
  ]
}
```

---

#### 6.2.4 Accept Invitation

**Endpoint:** `POST /api/invitations/{token}/accept`  
**Auth Required:** Yes  
**Description:** Accept invitation and gain document access

**Success Response (200 OK):**
```json
{
  "message": "Invitation accepted successfully",
  "documentId": 42,
  "documentTitle": "My Project Document",
  "role": "EDITOR",
  "yjsRoomId": "doc_1707825600_abc123"
}
```

**Side Effects:**
- Creates DocumentPermission record
- Updates invitation status to ACCEPTED
- User can now access document

---

#### 6.2.5 Decline Invitation

**Endpoint:** `POST /api/invitations/{token}/decline`  
**Auth Required:** Yes  
**Description:** Decline invitation

**Success Response (200 OK):**
```json
{
  "message": "Invitation declined"
}
```

---

#### 6.2.6 Revoke Invitation

**Endpoint:** `DELETE /api/invitations/{invitationId}`  
**Auth Required:** Yes  
**Permission Required:** Sender or OWNER  
**Description:** Cancel pending invitation

**Success Response (204 No Content)**

---

## ⚠️ 7. Error Handling

### 7.1 Standard Error Response

All error responses follow this format:

```json
{
  "timestamp": "2026-02-13T12:00:00",
  "status": 400,
  "error": "Bad Request",
  "message": "Validation failed: Title must be between 1 and 255 characters",
  "path": "/api/documents/create"
}
```

### 7.2 HTTP Status Codes

| Code | Meaning | Usage |
|------|---------|-------|
| 200 | OK | Successful GET/PUT/POST request |
| 201 | Created | Resource created successfully |
| 204 | No Content | Successful DELETE request |
| 400 | Bad Request | Validation error or invalid input |
| 401 | Unauthorized | Missing or invalid authentication |
| 403 | Forbidden | Authenticated but no permission |
| 404 | Not Found | Resource doesn't exist |
| 409 | Conflict | Duplicate resource (e.g., email exists) |
| 413 | Payload Too Large | File size exceeds limit |
| 429 | Too Many Requests | Rate limit exceeded |
| 500 | Internal Server Error | Server error |

### 7.3 Validation Errors

```json
{
  "timestamp": "2026-02-13T12:00:00",
  "status": 400,
  "error": "Bad Request",
  "message": "Validation failed",
  "errors": [
    {
      "field": "email",
      "message": "Invalid email format"
    },
    {
      "field": "password",
      "message": "Password must be at least 8 characters"
    }
  ],
  "path": "/api/auth/register"
}
```

### 7.4 Permission Errors

```json
{
  "timestamp": "2026-02-13T12:00:00",
  "status": 403,
  "error": "Forbidden",
  "message": "You don't have permission to edit this document. Required role: EDITOR",
  "path": "/api/documents/42"
}
```

---

## 🚦 8. Rate Limiting

### 8.1 Rate Limit Headers

All responses include rate limit headers:

```
X-RateLimit-Limit: 100
X-RateLimit-Remaining: 95
X-RateLimit-Reset: 1707826800
```

### 8.2 Rate Limits by Endpoint Type

| Endpoint Type | Limit | Window | Scope |
|---------------|-------|--------|-------|
| OTP requests | 5 | 1 hour | Per email |
| Login attempts | 10 | 1 hour | Per IP |
| Share link creation | 20 | 1 hour | Per user |
| Document creation | 50 | 1 hour | Per user |
| API calls (general) | 100 | 1 minute | Per user |

### 8.3 Rate Limit Exceeded Response

```json
{
  "timestamp": "2026-02-13T12:00:00",
  "status": 429,
  "error": "Too Many Requests",
  "message": "Rate limit exceeded. Please try again in 45 seconds.",
  "retryAfter": 45
}
```

---

## 🎨 9. Frontend Integration Guide

### 9.1 Authentication Flow

```javascript
// 1. Register
async function register(userData) {
  const response = await fetch('/api/auth/register', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(userData)
  });
  
  if (response.ok) {
    showOTPModal(userData.email);
  }
}

// 2. Verify OTP
async function verifyOTP(email, otp) {
  const response = await fetch('/api/auth/verify-otp', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    credentials: 'include', // Important: saves JWT cookie
    body: JSON.stringify({ email, otp })
  });
  
  if (response.ok) {
    redirectToLogin();
  }
}

// 3. Login
async function login(credentials) {
  const response = await fetch('/api/auth/login', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    credentials: 'include', // Important: saves JWT cookie
    body: JSON.stringify(credentials)
  });
  
  if (response.ok) {
    const user = await response.json();
    redirectToDashboard();
  }
}

// 4. Check Auth Status
async function checkAuth() {
  const response = await fetch('/api/auth/validate', {
    method: 'POST',
    credentials: 'include'
  });
  
  return response.ok;
}

// 5. Logout
async function logout() {
  await fetch('/api/auth/logout', {
    method: 'POST',
    credentials: 'include'
  });
  
  redirectToLogin();
}
```

### 9.2 Document Management

```javascript
// Create document
async function createDocument(title) {
  const response = await fetch('/api/documents/create', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    credentials: 'include',
    body: JSON.stringify({ title, visibility: 'PRIVATE' })
  });
  
  const doc = await response.json();
  openEditor(doc.id, doc.yjsRoomId);
}

// List documents with pagination
async function listDocuments(page = 0, search = '') {
  const params = new URLSearchParams({
    page,
    size: 20,
    sort: 'updatedAt',
    direction: 'desc',
    search
  });
  
  const response = await fetch(`/api/documents/list?${params}`, {
    credentials: 'include'
  });
  
  return await response.json();
}

// Upload document
async function uploadDocument(file) {
  const formData = new FormData();
  formData.append('file', file);
  formData.append('visibility', 'PRIVATE');
  
  const response = await fetch('/api/documents/upload', {
    method: 'POST',
    credentials: 'include',
    body: formData
  });
  
  return await response.json();
}
```

### 9.3 Real-Time Collaboration Setup

> **Important:** The JWT cookie is `HttpOnly` — JavaScript cannot read it with `document.cookie`. Use the `token` field from the **login response body** instead. Store it in Zustand/memory (not `localStorage`). See Section 3.2 for the full React pattern with deferred provider creation.

```javascript
import { Editor } from '@tiptap/core';
import StarterKit from '@tiptap/starter-kit';
import Collaboration from '@tiptap/extension-collaboration';
import CollaborationCursor from '@tiptap/extension-collaboration-cursor';
import * as Y from 'yjs';
import { WebsocketProvider } from 'y-websocket';

async function initializeEditor(yjsRoomId, token, currentUser) {
  // token comes from the login response body (stored in app state / Zustand)
  // Do NOT try: document.cookie.split('; ').find(row => row.startsWith('jwt='))
  // The jwt cookie is HttpOnly — it is invisible to JavaScript by design.

  if (!token) {
    redirectToLogin();
    return;
  }

  // Create Yjs document
  const ydoc = new Y.Doc();

  // Connect to Yjs WebSocket service (port 3000) using token query param
  const provider = new WebsocketProvider(
    'ws://localhost:3000/ws/yjs',
    yjsRoomId,
    ydoc,
    { params: { token } }   // appended as ?token=<jwt>
  );

  // Initialize editor
  const editor = new Editor({
    element: document.querySelector('#editor'),
    extensions: [
      StarterKit.configure({
        history: false, // Yjs handles undo/redo
      }),
      Collaboration.configure({
        document: ydoc,
      }),
      CollaborationCursor.configure({
        provider: provider,
        user: {
          name: `${currentUser.firstName} ${currentUser.lastName}`,
          color: generateUserColor(currentUser.id),
        },
      }),
    ],
  });

  // Handle connection status
  provider.on('status', event => {
    updateConnectionStatus(event.status);
  });

  provider.on('sync', isSynced => {
    if (isSynced) hideLoadingSpinner();
  });

  // Cleanup on unmount
  return () => {
    provider.disconnect();
    editor.destroy();
  };
}

function generateUserColor(userId) {
  const colors = [
    '#FF6B6B', '#4ECDC4', '#45B7D1', '#FFA07A',
    '#98D8C8', '#F7DC6F', '#BB8FCE', '#85C1E2'
  ];
  return colors[userId % colors.length];
}
```

### 9.4 Version Management

```javascript
// Create version
async function createVersion(documentId, versionName, comment) {
  const response = await fetch(`/api/documents/${documentId}/versions`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    credentials: 'include',
    body: JSON.stringify({ versionName, comment })
  });
  
  return await response.json();
}

// List versions
async function listVersions(documentId) {
  const response = await fetch(`/api/documents/${documentId}/versions`, {
    credentials: 'include'
  });
  
  return await response.json();
}

// View version (read-only)
async function viewVersion(versionId) {
  const response = await fetch(`/api/versions/${versionId}`, {
    credentials: 'include'
  });
  
  const version = await response.json();
  
  // Display in read-only editor
  editor.setOptions({ editable: false });
  editor.commands.setContent(version.content);
  
  return version;
}

// Restore version
async function restoreVersion(versionId) {
  const confirmed = confirm('This will create a new version from this checkpoint. Continue?');
  if (!confirmed) return;
  
  const response = await fetch(`/api/versions/${versionId}/restore`, {
    method: 'POST',
    credentials: 'include'
  });
  
  const result = await response.json();
  
  // Reload editor
  location.reload();
}
```

### 9.5 Share Link Flow

```javascript
// Create share link
async function createShareLink(documentId, options) {
  const response = await fetch(`/api/documents/${documentId}/share-links`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    credentials: 'include',
    body: JSON.stringify({
      role: options.role || 'VIEWER',
      expiresInDays: options.expiresInDays || 7,
      maxUses: options.maxUses || null,
      requiresAuth: options.requiresAuth || false,
      description: options.description || ''
    })
  });
  
  const link = await response.json();
  
  // Copy to clipboard
  navigator.clipboard.writeText(link.shareUrl);
  showNotification('Link copied to clipboard!');
  
  return link;
}

// Handle share link access (recipient side)
async function handleShareLink(token) {
  // Step 1: Validate link
  const validation = await fetch(`/api/share/${token}/validate`);
  const linkInfo = await validation.json();
  
  if (!linkInfo.isValid) {
    showError('This link is expired or invalid');
    return;
  }
  
  // Step 2: Show document preview with login prompt
  showDocumentPreview({
    title: linkInfo.documentTitle,
    owner: linkInfo.createdByName,
    role: linkInfo.role,
    requiresLogin: true
  });
  
  // Step 3: After login/register, grant access
  const accessResponse = await fetch(`/api/share/${token}/access`, {
    method: 'POST',
    credentials: 'include'
  });
  
  if (accessResponse.status === 401) {
    // Not logged in
    const error = await accessResponse.json();
    showLoginModal(error);
    return;
  }
  
  const docInfo = await accessResponse.json();
  
  // Step 4: Redirect to editor
  window.location.href = `/editor/${docInfo.documentId}`;
}
```

### 9.6 Error Handling

```javascript
// Global error handler
async function handleAPIError(response) {
  if (response.status === 401) {
    // Unauthorized - redirect to login
    localStorage.setItem('redirectAfterLogin', window.location.pathname);
    window.location.href = '/login';
    return;
  }
  
  const error = await response.json();
  
  switch (response.status) {
    case 400:
      showValidationErrors(error.errors || [error.message]);
      break;
    case 403:
      showError('You don\'t have permission to perform this action');
      break;
    case 404:
      showError('Resource not found');
      break;
    case 429:
      showError(`Too many requests. Please try again in ${error.retryAfter} seconds`);
      break;
    case 500:
      showError('Server error. Please try again later');
      break;
    default:
      showError(error.message || 'An error occurred');
  }
}

// Example usage
async function saveDocument() {
  try {
    const response = await fetch('/api/documents/42', {
      method: 'PUT',
      headers: { 'Content-Type': 'application/json' },
      credentials: 'include',
      body: JSON.stringify({ title: 'New Title' })
    });
    
    if (!response.ok) {
      await handleAPIError(response);
      return;
    }
    
    const doc = await response.json();
    showSuccess('Document saved successfully');
  } catch (error) {
    showError('Network error. Please check your connection');
  }
}
```

---

## 📝 Notes for Frontend Developers

### Authentication
- **Always include** `credentials: 'include'` in fetch requests for JWT cookie
- JWT expires after 24 hours - use `/api/auth/refresh` to extend
- Check auth status with `/api/auth/validate` before sensitive operations
- Store `redirectAfterLogin` in localStorage for post-login navigation

### WebSocket Connection
- The `jwt` cookie is **HttpOnly** — JavaScript cannot read it (`document.cookie` won't return it)
- Use the **`token` field from the login response body** for WebSocket authentication
- Store the token in Zustand/app memory (not `localStorage`)
- Pass it via query parameter: `new WebsocketProvider(wsUrl, roomId, ydoc, { params: { token } })`
- Create the provider in a `useEffect` (not `useState` initializer) so it runs after Zustand rehydrates
- Handle connection states: `connected`, `disconnected`, `syncing`
- y-websocket handles reconnection automatically with exponential backoff

### Real-Time Collaboration
- Use `y-websocket` provider for Yjs
- Disable TipTap's built-in history (Yjs handles undo/redo)
- Generate consistent user colors based on user ID
- Show connection status and active users count

### Error Handling
- Implement global error handler for consistent UX
- Handle 401 specially: redirect to login with return URL
- Show user-friendly messages for validation errors
- Implement retry logic for 429 (rate limit) errors

### Performance
- Implement pagination for document lists (default: 20 per page)
- Lazy load editor components
- Debounce search inputs (300ms recommended)
- Cache user info from `/api/auth/me`

### Security
- The JWT cookie is `HttpOnly` — never readable by JavaScript (XSS protection, already implemented)
- The `token` field in the login response is for WebSocket use only — store it in Zustand/memory, **not** `localStorage`
- All Spring Boot API calls use the cookie automatically (`credentials: 'include'`)
- Validate user permissions before showing UI controls
- Sanitize user input before rendering

---

## 🔄 Changelog

### v2.1 (February 22, 2026)
- **Login response now includes `token` field** for WebSocket authentication
- Clarified why `HttpOnly` cookie cannot be used for Yjs WebSocket (different origin + JS inaccessible)
- Updated Section 3.1 WebSocket connection docs to use token from response body, not cookie
- Updated Section 3.2 TipTap integration example with correct React/Zustand deferred provider pattern
- Updated Section 9.3 frontend integration guide with corrected token usage
- Updated Security and WebSocket notes sections accordingly

### v2.0 (February 13, 2026)
- Added complete document sharing system (share links + email invitations)
- Implemented Option 2: Force Registration for share links
- Added DocumentAccessResponse DTO
- Updated all endpoints with comprehensive examples
- Added frontend integration guide
- Documented error handling patterns
- Added rate limiting information

### v1.0 (February 12, 2026)
- Initial API contract
- Authentication system
- Document management
- Real-time collaboration
- Document versioning
- Collaborator management

---

**API Version:** 2.1  
**Last Updated:** February 22, 2026  
**Maintained by:** Collab-Docs Team  
**Contact:** support@collab-docs.example.com

