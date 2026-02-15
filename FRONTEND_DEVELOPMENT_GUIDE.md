# Collab-Docs Frontend Development Guide

**Project:** Real-Time Collaborative Document Editor  
**Stack:** React + TipTap + Yjs + Tailwind CSS  
**Date:** February 13, 2026  
**Target:** Production-Ready Frontend Application

---

## 📋 Table of Contents

1. [Project Overview](#1-project-overview)
2. [Tech Stack](#2-tech-stack)
3. [Application Structure](#3-application-structure)
4. [Pages & Routes](#4-pages--routes)
5. [User Journey & Flows](#5-user-journey--flows)
6. [UI/UX Design System](#6-uiux-design-system)
7. [Component Architecture](#7-component-architecture)
8. [State Management](#8-state-management)
9. [Real-Time Collaboration](#9-real-time-collaboration)
10. [Features Implementation](#10-features-implementation)
11. [Edge Cases & Error Handling](#11-edge-cases--error-handling)
12. [Performance Optimization](#12-performance-optimization)
13. [Future Extensibility](#13-future-extensibility)

---

## 1. Project Overview

### 1.1 What You're Building

A **Google Docs-like collaborative editor** with:
- Real-time multi-user editing
- Document version control
- Team collaboration with permissions
- Share links and email invitations
- Rich text editing with TipTap
- Responsive, modern UI

### 1.2 Core User Personas

1. **Document Owner** - Creates, manages, shares documents
2. **Collaborator** - Edits shared documents
3. **Viewer** - Read-only access via share links
4. **Guest** - Accesses via share link (must register)

### 1.3 Key Features

- ✅ User authentication (email + OTP)
- ✅ Document CRUD operations
- ✅ Real-time collaborative editing
- ✅ Version history with restore
- ✅ Team collaboration (OWNER/EDITOR/VIEWER roles)
- ✅ Share links (public/private, expirable)
- ✅ Email invitations
- ✅ Active user presence
- ✅ Document export (handled client-side)

---

## 2. Tech Stack

### 2.1 Core Dependencies

```json
{
  "dependencies": {
    "react": "^18.2.0",
    "react-dom": "^18.2.0",
    "react-router-dom": "^6.20.0",
    "@tiptap/react": "^2.1.13",
    "@tiptap/starter-kit": "^2.1.13",
    "@tiptap/extension-collaboration": "^2.1.13",
    "@tiptap/extension-collaboration-cursor": "^2.1.13",
    "@tiptap/extension-placeholder": "^2.1.13",
    "yjs": "^13.6.10",
    "y-websocket": "^1.5.0",
    "zustand": "^4.4.7",
    "axios": "^1.6.2",
    "react-query": "^5.17.0",
    "date-fns": "^3.0.0",
    "react-hot-toast": "^2.4.1",
    "tailwindcss": "^3.4.0",
    "headlessui": "^1.7.17",
    "heroicons": "^2.1.1",
    "clsx": "^2.0.0"
  }
}
```

### 2.2 Why This Stack?

- **React 18**: Concurrent rendering, better performance
- **TipTap**: Headless editor (full customization)
- **Yjs**: Industry-standard CRDT for collaboration
- **Zustand**: Lightweight state management
- **React Query**: Server state management, caching
- **Tailwind CSS**: Utility-first, rapid development
- **HeadlessUI**: Accessible components (modals, dropdowns)

---

## 3. Application Structure

### 3.1 Folder Structure

```
src/
├── components/           # Reusable UI components
│   ├── auth/            # Auth-related (LoginForm, RegisterForm)
│   ├── common/          # Shared components (Button, Input, Modal)
│   ├── document/        # Document components (DocumentCard, DocumentList)
│   ├── editor/          # TipTap editor components
│   │   ├── Editor.jsx
│   │   ├── MenuBar.jsx
│   │   ├── CollaborationCursor.jsx
│   │   └── extensions/
│   ├── layout/          # Layout components (Header, Sidebar)
│   ├── sharing/         # Share link, invitations
│   └── version/         # Version history components
│
├── pages/               # Page components (route components)
│   ├── auth/
│   │   ├── LoginPage.jsx
│   │   ├── RegisterPage.jsx
│   │   ├── VerifyOTPPage.jsx
│   │   └── ForgotPasswordPage.jsx
│   ├── dashboard/
│   │   └── DashboardPage.jsx
│   ├── editor/
│   │   └── EditorPage.jsx
│   ├── share/
│   │   └── ShareLinkPage.jsx
│   └── NotFoundPage.jsx
│
├── hooks/               # Custom React hooks
│   ├── useAuth.js
│   ├── useDocument.js
│   ├── useCollaboration.js
│   ├── useVersions.js
│   └── useShareLink.js
│
├── services/            # API services
│   ├── api.js           # Axios instance
│   ├── authService.js
│   ├── documentService.js
│   ├── collaborationService.js
│   ├── versionService.js
│   └── shareService.js
│
├── store/               # Zustand stores
│   ├── authStore.js
│   ├── documentStore.js
│   └── editorStore.js
│
├── utils/               # Utility functions
│   ├── constants.js
│   ├── validators.js
│   ├── formatters.js
│   └── colors.js
│
├── styles/              # Global styles
│   └── globals.css
│
├── App.jsx              # Main app component
├── Router.jsx           # Route configuration
└── main.jsx             # Entry point
```

---

## 4. Pages & Routes

### 4.1 Complete Page List (15 Pages)

| # | Route | Component | Auth Required | Description |
|---|-------|-----------|---------------|-------------|
| 1 | `/` | LandingPage | No | Marketing page, features, pricing |
| 2 | `/login` | LoginPage | No | Email + password login |
| 3 | `/register` | RegisterPage | No | Sign up form |
| 4 | `/verify-otp` | VerifyOTPPage | No | OTP verification |
| 5 | `/forgot-password` | ForgotPasswordPage | No | Password reset request |
| 6 | `/reset-password` | ResetPasswordPage | No | Password reset with OTP |
| 7 | `/dashboard` | DashboardPage | Yes | Document list, create new |
| 8 | `/editor/:documentId` | EditorPage | Yes | Main collaborative editor |
| 9 | `/document/:documentId/versions` | VersionHistoryPage | Yes | Version timeline |
| 10 | `/document/:documentId/settings` | DocumentSettingsPage | Yes | Title, visibility, collaborators |
| 11 | `/document/:documentId/share` | ShareManagementPage | Yes | Share links, invitations |
| 12 | `/share/:token` | ShareLinkPage | No | Public share link landing |
| 13 | `/invitations` | InvitationsPage | Yes | Pending invitations list |
| 14 | `/profile` | ProfilePage | Yes | User settings, account |
| 15 | `/404` | NotFoundPage | No | 404 error page |

### 4.2 Route Configuration

```jsx
// Router.jsx
import { BrowserRouter, Routes, Route, Navigate } from 'react-router-dom';
import { useAuth } from './hooks/useAuth';

function ProtectedRoute({ children }) {
  const { isAuthenticated } = useAuth();
  return isAuthenticated ? children : <Navigate to="/login" replace />;
}

function Router() {
  return (
    <BrowserRouter>
      <Routes>
        {/* Public Routes */}
        <Route path="/" element={<LandingPage />} />
        <Route path="/login" element={<LoginPage />} />
        <Route path="/register" element={<RegisterPage />} />
        <Route path="/verify-otp" element={<VerifyOTPPage />} />
        <Route path="/forgot-password" element={<ForgotPasswordPage />} />
        <Route path="/reset-password" element={<ResetPasswordPage />} />
        <Route path="/share/:token" element={<ShareLinkPage />} />
        
        {/* Protected Routes */}
        <Route path="/dashboard" element={
          <ProtectedRoute><DashboardPage /></ProtectedRoute>
        } />
        <Route path="/editor/:documentId" element={
          <ProtectedRoute><EditorPage /></ProtectedRoute>
        } />
        <Route path="/document/:documentId/versions" element={
          <ProtectedRoute><VersionHistoryPage /></ProtectedRoute>
        } />
        <Route path="/document/:documentId/settings" element={
          <ProtectedRoute><DocumentSettingsPage /></ProtectedRoute>
        } />
        <Route path="/document/:documentId/share" element={
          <ProtectedRoute><ShareManagementPage /></ProtectedRoute>
        } />
        <Route path="/invitations" element={
          <ProtectedRoute><InvitationsPage /></ProtectedRoute>
        } />
        <Route path="/profile" element={
          <ProtectedRoute><ProfilePage /></ProtectedRoute>
        } />
        
        {/* 404 */}
        <Route path="*" element={<NotFoundPage />} />
      </Routes>
    </BrowserRouter>
  );
}
```

---

## 5. User Journey & Flows

### 5.1 New User Journey (First-Time User)

```
1. Landing Page (/)
   ↓ Click "Get Started"
   
2. Register Page (/register)
   - Enter: First Name, Last Name, Email, Password
   - Submit form
   ↓ API: POST /api/auth/register
   
3. Verify OTP Page (/verify-otp)
   - Enter 6-digit OTP from email
   - Submit
   ↓ API: POST /api/auth/verify-otp
   
4. Auto-redirect to Login Page (/login)
   - Enter credentials
   - Submit
   ↓ API: POST /api/auth/login (JWT cookie set)
   
5. Dashboard Page (/dashboard)
   - See empty state: "Create your first document"
   - Click "New Document"
   ↓ API: POST /api/documents/create
   
6. Editor Page (/editor/:documentId)
   - See blank document with TipTap editor
   - Start typing → Real-time sync active
   - See own cursor/name
```

### 5.2 Document Creation Flow

```
Dashboard → Click "New Document" → Modal Opens

Modal Contents:
┌─────────────────────────────────────┐
│ Create New Document                 │
├─────────────────────────────────────┤
│ Title: [___________________]        │
│                                     │
│ Visibility:                         │
│ ○ Private (Only you)                │
│ ○ Shared (People you invite)        │
│ ○ Public (Anyone with link)         │
│                                     │
│ [Cancel]  [Create Document] ←──    │
└─────────────────────────────────────┘

After Create:
→ API: POST /api/documents/create
→ Redirect to /editor/{documentId}
→ WebSocket connects to Yjs service
→ Editor ready for input
```

### 5.3 Collaboration Flow (Inviting Others)

```
Editor Page → Click "Share" button in top-right

Share Modal Opens:
┌─────────────────────────────────────────────┐
│ Share "Project Proposal"              [X]   │
├─────────────────────────────────────────────┤
│ Tabs: [Invite People] [Share Link]         │
│                                             │
│ Invite People Tab:                          │
│ ┌─────────────────────────────────────┐    │
│ │ Email: [_______________________]    │    │
│ │ Role: [Editor ▼]                    │    │
│ │ Message: [___________________]      │    │
│ │ [Send Invitation]                   │    │
│ └─────────────────────────────────────┘    │
│                                             │
│ Current Collaborators:                      │
│ • You (Owner)                               │
│ • Alice Smith (Editor) [Change▼] [Remove]  │
│ • Bob Jones (Viewer) [Change▼] [Remove]    │
└─────────────────────────────────────────────┘

After Send:
→ API: POST /api/documents/{id}/invitations
→ Email sent to invitee
→ Toast: "Invitation sent to alice@example.com"
```

### 5.4 Share Link Flow (Recipient Side)

```
Recipient clicks: https://collab-docs.com/share/abc123xyz

1. Share Link Page (/share/abc123xyz)
   → API: GET /api/share/abc123xyz/validate (public)
   
   If valid:
   ┌─────────────────────────────────────┐
   │ John Doe shared a document with you │
   │                                     │
   │ "Q4 Marketing Strategy"             │
   │ Permission: Viewer                  │
   │                                     │
   │ [Login] [Register]                  │
   └─────────────────────────────────────┘
   
2. After Login/Register:
   → API: POST /api/share/abc123xyz/access
   → Permission granted
   → Redirect to /editor/{documentId}
   → Document opens (read-only if VIEWER)
```

### 5.5 Real-Time Collaboration Experience

```
User A opens document:
┌────────────────────────────────────────┐
│ Editor connected                       │
│ • You (John Doe) - Green cursor       │
└────────────────────────────────────────┘

User B joins (different browser):
┌────────────────────────────────────────┐
│ Editor connected                       │
│ • You (John Doe) - Green cursor       │
│ • Alice Smith - Blue cursor           │← New!
└────────────────────────────────────────┘

User A types "Hello":
→ Text appears in User B's editor instantly
→ User B sees "John Doe is typing..." indicator

User B types " World":
→ Text appears in User A's editor instantly
→ Both see: "Hello World"
→ Auto-saved to server every 5 minutes
```

### 5.6 Version Control Flow

```
Editor Page → Click "Versions" in menu

Version History Page (/document/{id}/versions):
┌─────────────────────────────────────────────┐
│ Version History                             │
├─────────────────────────────────────────────┤
│ [Create Checkpoint]                         │
│                                             │
│ Timeline:                                   │
│ ● Version 5 - Final Draft                  │
│   Feb 13, 2026 2:30 PM by You             │
│   [View] [Restore]                         │
│                                             │
│ ● Version 4 - Second Review                │
│   Feb 12, 2026 3:15 PM by Alice           │
│   [View] [Restore]                         │
│                                             │
│ ● Version 3 - Initial Draft                │
│   Feb 10, 2026 10:00 AM by You            │
│   [View] [Restore]                         │
└─────────────────────────────────────────────┘

Click "Restore":
→ Confirmation modal: "Restore to Version 3?"
→ API: POST /api/versions/{versionId}/restore
→ Creates new Version 6 (from Version 3 content)
→ Redirect back to editor
→ Toast: "Document restored to Version 3"
```

---

## 6. UI/UX Design System

### 6.1 Design Philosophy

**Inspiration:** Google Docs + Notion + Linear  
**Style:** Clean, minimal, focus on content  
**Colors:** Professional with personality  
**Spacing:** Generous whitespace, breathing room

### 6.2 Color Palette

```css
/* Primary Colors */
--primary-50: #eff6ff;
--primary-100: #dbeafe;
--primary-500: #3b82f6;  /* Main brand color */
--primary-600: #2563eb;
--primary-700: #1d4ed8;

/* Neutral Colors */
--gray-50: #f9fafb;
--gray-100: #f3f4f6;
--gray-200: #e5e7eb;
--gray-300: #d1d5db;
--gray-500: #6b7280;
--gray-700: #374151;
--gray-900: #111827;

/* Semantic Colors */
--success: #10b981;  /* Green */
--warning: #f59e0b;  /* Amber */
--error: #ef4444;    /* Red */
--info: #3b82f6;     /* Blue */

/* Editor Cursor Colors (for collaboration) */
--cursor-colors: [
  '#FF6B6B', '#4ECDC4', '#45B7D1', '#FFA07A',
  '#98D8C8', '#F7DC6F', '#BB8FCE', '#85C1E2'
]
```

### 6.3 Typography

```css
/* Font Family */
font-family: 'Inter', -apple-system, BlinkMacSystemFont, sans-serif;

/* Font Sizes */
--text-xs: 0.75rem;    /* 12px */
--text-sm: 0.875rem;   /* 14px */
--text-base: 1rem;     /* 16px */
--text-lg: 1.125rem;   /* 18px */
--text-xl: 1.25rem;    /* 20px */
--text-2xl: 1.5rem;    /* 24px */
--text-3xl: 1.875rem;  /* 30px */
--text-4xl: 2.25rem;   /* 36px */

/* Font Weights */
--font-normal: 400;
--font-medium: 500;
--font-semibold: 600;
--font-bold: 700;
```

### 6.4 Component Styling Guide

#### Buttons
```jsx
/* Primary Button */
className="px-4 py-2 bg-primary-500 text-white rounded-lg 
           hover:bg-primary-600 transition-colors
           focus:ring-2 focus:ring-primary-500 focus:ring-offset-2"

/* Secondary Button */
className="px-4 py-2 bg-gray-100 text-gray-700 rounded-lg
           hover:bg-gray-200 transition-colors"

/* Danger Button */
className="px-4 py-2 bg-red-500 text-white rounded-lg
           hover:bg-red-600 transition-colors"

/* Ghost Button */
className="px-4 py-2 text-gray-700 hover:bg-gray-100 rounded-lg"
```

#### Input Fields
```jsx
className="w-full px-4 py-2 border border-gray-300 rounded-lg
           focus:ring-2 focus:ring-primary-500 focus:border-transparent
           placeholder:text-gray-400"
```

#### Cards
```jsx
className="bg-white border border-gray-200 rounded-lg p-6
           hover:shadow-lg transition-shadow cursor-pointer"
```

### 6.5 Layout Guidelines

**Dashboard Page Layout:**
```
┌─────────────────────────────────────────────────────┐
│ Header (64px height)                                │
│ Logo | Search | [New Document] | Profile            │
├─────────────────────────────────────────────────────┤
│                                                     │
│ Sidebar (240px)    │    Main Content               │
│                    │                                │
│ • My Documents     │    Document Grid              │
│ • Shared with me   │    ┌────┐ ┌────┐ ┌────┐     │
│ • Recent           │    │Doc │ │Doc │ │Doc │     │
│ • Starred          │    │ 1  │ │ 2  │ │ 3  │     │
│ • Trash            │    └────┘ └────┘ └────┘     │
│                    │                                │
│ [Invitations (2)]  │    Pagination                 │
│                    │    ← 1 2 3 →                  │
└────────────────────┴────────────────────────────────┘
```

**Editor Page Layout:**
```
┌─────────────────────────────────────────────────────┐
│ Header (56px)                                       │
│ ← Back | Document Title | [Share] [•••] | Profile  │
├─────────────────────────────────────────────────────┤
│ Menu Bar (48px)                                     │
│ B I U | H1 H2 | • 1. | ↶ ↷ | 👤 Alice, Bob       │
├─────────────────────────────────────────────────────┤
│                                                     │
│                                                     │
│          Editor Canvas (centered, 700px)           │
│                                                     │
│          [Typing area with cursor]                 │
│                                                     │
│                                                     │
│                                                     │
└─────────────────────────────────────────────────────┘
```

### 6.6 Modal Design

**Standard Modal Template:**
```
┌─────────────────────────────────────────┐
│ Modal Title                       [X]   │
├─────────────────────────────────────────┤
│                                         │
│ Modal content goes here                 │
│                                         │
│ Form fields, text, etc.                 │
│                                         │
├─────────────────────────────────────────┤
│                  [Cancel]  [Confirm]    │
└─────────────────────────────────────────┘

/* Sizes */
- Small: max-w-md (448px)
- Medium: max-w-lg (512px)
- Large: max-w-2xl (672px)
- Full: max-w-4xl (896px)
```

---

## 7. Component Architecture

### 7.1 Key Components Overview

#### Authentication Components
1. **LoginForm** - Email/password with validation
2. **RegisterForm** - Multi-step with OTP
3. **OTPInput** - 6-digit code input with auto-focus
4. **ForgotPasswordForm** - Email submission
5. **ResetPasswordForm** - OTP + new password

#### Dashboard Components
1. **DocumentCard** - Preview with title, date, collaborators
2. **DocumentGrid** - Responsive grid layout
3. **CreateDocumentModal** - Title + visibility
4. **DocumentListEmpty** - Empty state with CTA
5. **SearchBar** - Debounced search input
6. **DocumentFilters** - Filter by visibility, date
7. **PaginationControls** - Page navigation

#### Editor Components
1. **TipTapEditor** - Main editor with all extensions
2. **EditorMenuBar** - Formatting toolbar
3. **CollaborationCursors** - Other users' cursors
4. **ActiveUsersList** - Avatar stack with names
5. **ConnectionStatus** - Online/offline indicator
6. **AutoSaveIndicator** - "Saving..." / "All changes saved"
7. **CommentsSidebar** - Comments on selections (future)

#### Sharing Components
1. **ShareModal** - Tabs for invites and links
2. **InvitePeopleForm** - Email + role selector
3. **CollaboratorList** - Manage existing collaborators
4. **ShareLinkGenerator** - Create link with options
5. **ShareLinkList** - Active/expired links
6. **InvitationCard** - Accept/decline invitation

#### Version Components
1. **VersionTimeline** - Chronological version list
2. **VersionCard** - Version details + actions
3. **CreateVersionModal** - Name + comment
4. **VersionDiffViewer** - Compare versions (future)
5. **RestoreConfirmModal** - Restore warning

#### Common Components
1. **Button** - All variants (primary, secondary, danger)
2. **Input** - Text, email, password with validation
3. **Select** - Dropdown with search
4. **Modal** - HeadlessUI Dialog wrapper
5. **Toast** - Success/error notifications
6. **Avatar** - User profile picture
7. **Badge** - Role badges (Owner, Editor, Viewer)
8. **Spinner** - Loading indicator
9. **EmptyState** - No data placeholder
10. **ConfirmDialog** - Confirm destructive actions

### 7.2 Sample Component: DocumentCard

```jsx
// components/document/DocumentCard.jsx
import { CalendarIcon, UserGroupIcon, StarIcon } from '@heroicons/react/24/outline';
import { StarIcon as StarIconSolid } from '@heroicons/react/24/solid';
import { formatDistanceToNow } from 'date-fns';
import { useNavigate } from 'react-router-dom';
import { useDocumentStore } from '../../store/documentStore';

export function DocumentCard({ document }) {
  const navigate = useNavigate();
  const { toggleStar } = useDocumentStore();

  const handleClick = () => {
    navigate(`/editor/${document.id}`);
  };

  const handleStar = (e) => {
    e.stopPropagation();
    toggleStar(document.id);
  };

  return (
    <div
      onClick={handleClick}
      className="group relative bg-white border border-gray-200 rounded-lg p-6
                 hover:shadow-lg hover:border-primary-300 transition-all cursor-pointer"
    >
      {/* Star Button */}
      <button
        onClick={handleStar}
        className="absolute top-4 right-4 text-gray-400 hover:text-yellow-500"
      >
        {document.isStarred ? (
          <StarIconSolid className="w-5 h-5 text-yellow-500" />
        ) : (
          <StarIcon className="w-5 h-5" />
        )}
      </button>

      {/* Document Title */}
      <h3 className="text-lg font-semibold text-gray-900 mb-2 pr-8">
        {document.title}
      </h3>

      {/* Metadata */}
      <div className="flex items-center gap-4 text-sm text-gray-500 mb-4">
        <div className="flex items-center gap-1">
          <CalendarIcon className="w-4 h-4" />
          <span>{formatDistanceToNow(new Date(document.updatedAt), { addSuffix: true })}</span>
        </div>
        
        {document.collaboratorCount > 0 && (
          <div className="flex items-center gap-1">
            <UserGroupIcon className="w-4 h-4" />
            <span>{document.collaboratorCount} collaborator{document.collaboratorCount > 1 ? 's' : ''}</span>
          </div>
        )}
      </div>

      {/* Owner Badge */}
      {document.role && (
        <span className={`inline-block px-2 py-1 text-xs rounded-full ${
          document.role === 'OWNER' 
            ? 'bg-primary-100 text-primary-700'
            : document.role === 'EDITOR'
            ? 'bg-green-100 text-green-700'
            : 'bg-gray-100 text-gray-700'
        }`}>
          {document.role}
        </span>
      )}

      {/* Hover Effect */}
      <div className="absolute inset-0 border-2 border-primary-500 rounded-lg opacity-0 
                      group-hover:opacity-100 transition-opacity pointer-events-none" />
    </div>
  );
}
```

---

## 8. State Management

### 8.1 Zustand Store Structure

#### Auth Store
```javascript
// store/authStore.js
import create from 'zustand';
import { persist } from 'zustand/middleware';

export const useAuthStore = create(
  persist(
    (set, get) => ({
      user: null,
      isAuthenticated: false,
      
      setUser: (user) => set({ user, isAuthenticated: true }),
      
      logout: () => set({ user: null, isAuthenticated: false }),
      
      updateUser: (updates) => set((state) => ({
        user: { ...state.user, ...updates }
      })),
    }),
    {
      name: 'auth-storage',
      getStorage: () => localStorage,
    }
  )
);
```

#### Document Store
```javascript
// store/documentStore.js
import create from 'zustand';

export const useDocumentStore = create((set, get) => ({
  documents: [],
  currentDocument: null,
  isLoading: false,
  
  setDocuments: (documents) => set({ documents }),
  
  setCurrentDocument: (document) => set({ currentDocument: document }),
  
  addDocument: (document) => set((state) => ({
    documents: [document, ...state.documents]
  })),
  
  updateDocument: (id, updates) => set((state) => ({
    documents: state.documents.map(doc =>
      doc.id === id ? { ...doc, ...updates } : doc
    ),
    currentDocument: state.currentDocument?.id === id
      ? { ...state.currentDocument, ...updates }
      : state.currentDocument
  })),
  
  deleteDocument: (id) => set((state) => ({
    documents: state.documents.filter(doc => doc.id !== id)
  })),
  
  toggleStar: (id) => set((state) => ({
    documents: state.documents.map(doc =>
      doc.id === id ? { ...doc, isStarred: !doc.isStarred } : doc
    )
  })),
}));
```

#### Editor Store
```javascript
// store/editorStore.js
import create from 'zustand';

export const useEditorStore = create((set) => ({
  editor: null,
  yjsProvider: null,
  activeUsers: [],
  connectionStatus: 'disconnected', // disconnected | connecting | connected
  isSaving: false,
  lastSaved: null,
  
  setEditor: (editor) => set({ editor }),
  setYjsProvider: (provider) => set({ yjsProvider }),
  setActiveUsers: (users) => set({ activeUsers: users }),
  setConnectionStatus: (status) => set({ connectionStatus: status }),
  setIsSaving: (isSaving) => set({ isSaving }),
  setLastSaved: (timestamp) => set({ lastSaved: timestamp }),
  
  cleanup: () => set({
    editor: null,
    yjsProvider: null,
    activeUsers: [],
    connectionStatus: 'disconnected',
  }),
}));
```

### 8.2 React Query Usage

```javascript
// hooks/useDocuments.js
import { useQuery, useMutation, useQueryClient } from 'react-query';
import { documentService } from '../services/documentService';

export function useDocuments(page = 0, size = 20) {
  return useQuery(
    ['documents', page, size],
    () => documentService.listDocuments({ page, size }),
    {
      staleTime: 5 * 60 * 1000, // 5 minutes
      cacheTime: 10 * 60 * 1000, // 10 minutes
      keepPreviousData: true,
    }
  );
}

export function useCreateDocument() {
  const queryClient = useQueryClient();
  
  return useMutation(
    (data) => documentService.createDocument(data),
    {
      onSuccess: () => {
        queryClient.invalidateQueries(['documents']);
      },
    }
  );
}

export function useDocument(documentId) {
  return useQuery(
    ['document', documentId],
    () => documentService.getDocument(documentId),
    {
      enabled: !!documentId,
      staleTime: 2 * 60 * 1000,
    }
  );
}
```

---

## 9. Real-Time Collaboration

### 9.1 TipTap Editor Setup

```javascript
// components/editor/Editor.jsx
import { useEditor, EditorContent } from '@tiptap/react';
import StarterKit from '@tiptap/starter-kit';
import Collaboration from '@tiptap/extension-collaboration';
import CollaborationCursor from '@tiptap/extension-collaboration-cursor';
import Placeholder from '@tiptap/extension-placeholder';
import * as Y from 'yjs';
import { WebsocketProvider } from 'y-websocket';
import { useEffect, useState } from 'react';
import { useAuthStore } from '../../store/authStore';
import { useEditorStore } from '../../store/editorStore';
import { generateUserColor } from '../../utils/colors';

export function Editor({ documentId, yjsRoomId, role }) {
  const { user } = useAuthStore();
  const { setEditor, setYjsProvider, setConnectionStatus, cleanup } = useEditorStore();
  const [ydoc] = useState(() => new Y.Doc());

  const editor = useEditor({
    extensions: [
      StarterKit.configure({
        history: false, // Yjs handles history
      }),
      Placeholder.configure({
        placeholder: 'Start typing...',
      }),
      Collaboration.configure({
        document: ydoc,
      }),
      CollaborationCursor.configure({
        provider: null, // Set later
        user: {
          name: `${user.firstName} ${user.lastName}`,
          color: generateUserColor(user.id),
        },
      }),
    ],
    editable: role === 'OWNER' || role === 'EDITOR',
    editorProps: {
      attributes: {
        class: 'prose prose-lg max-w-none focus:outline-none min-h-screen p-8',
      },
    },
  });

  useEffect(() => {
    if (!editor) return;

    // Get JWT from cookie
    const jwt = document.cookie
      .split('; ')
      .find(row => row.startsWith('jwt='))
      ?.split('=')[1];

    if (!jwt) {
      console.error('No JWT token found');
      return;
    }

    // Connect to WebSocket
    const provider = new WebsocketProvider(
      process.env.REACT_APP_WEBSOCKET_URL || 'ws://localhost:3000/ws/yjs',
      yjsRoomId,
      ydoc,
      {
        params: { token: jwt },
      }
    );

    // Set provider for collaboration cursor
    editor.extensionManager.extensions.find(
      ext => ext.name === 'collaborationCursor'
    ).options.provider = provider;

    // Handle connection status
    provider.on('status', ({ status }) => {
      setConnectionStatus(status);
      console.log('WebSocket status:', status);
    });

    provider.on('sync', (isSynced) => {
      if (isSynced) {
        console.log('Document synced');
      }
    });

    // Store in global state
    setEditor(editor);
    setYjsProvider(provider);

    // Cleanup on unmount
    return () => {
      provider.disconnect();
      editor.destroy();
      cleanup();
    };
  }, [editor, yjsRoomId, ydoc]);

  if (!editor) {
    return <div className="flex items-center justify-center h-screen">Loading editor...</div>;
  }

  return <EditorContent editor={editor} />;
}
```

### 9.2 Active Users Component

```javascript
// components/editor/ActiveUsersList.jsx
import { useEffect } from 'react';
import { useEditorStore } from '../../store/editorStore';
import axios from 'axios';

export function ActiveUsersList({ yjsRoomId }) {
  const { activeUsers, setActiveUsers } = useEditorStore();

  useEffect(() => {
    // Fetch active users
    const fetchActiveUsers = async () => {
      try {
        const response = await axios.get(
          `${process.env.REACT_APP_WEBSOCKET_URL}/api/documents/${yjsRoomId}/users`
        );
        setActiveUsers(response.data.users || []);
      } catch (error) {
        console.error('Failed to fetch active users:', error);
      }
    };

    fetchActiveUsers();
    const interval = setInterval(fetchActiveUsers, 10000); // Poll every 10s

    return () => clearInterval(interval);
  }, [yjsRoomId]);

  if (activeUsers.length === 0) return null;

  return (
    <div className="flex items-center gap-2 px-4">
      <span className="text-sm text-gray-600">Active:</span>
      <div className="flex -space-x-2">
        {activeUsers.slice(0, 5).map((user) => (
          <div
            key={user.userId}
            className="w-8 h-8 rounded-full bg-primary-500 text-white 
                       flex items-center justify-center text-xs font-medium
                       border-2 border-white"
            title={user.name}
          >
            {user.name.charAt(0).toUpperCase()}
          </div>
        ))}
        {activeUsers.length > 5 && (
          <div className="w-8 h-8 rounded-full bg-gray-300 text-gray-700
                          flex items-center justify-center text-xs font-medium
                          border-2 border-white">
            +{activeUsers.length - 5}
          </div>
        )}
      </div>
    </div>
  );
}
```

### 9.3 Connection Status Indicator

```javascript
// components/editor/ConnectionStatus.jsx
import { useEditorStore } from '../../store/editorStore';
import { CheckCircleIcon, ExclamationCircleIcon } from '@heroicons/react/24/solid';

export function ConnectionStatus() {
  const { connectionStatus, isSaving, lastSaved } = useEditorStore();

  if (connectionStatus === 'connected' && !isSaving) {
    return (
      <div className="flex items-center gap-2 text-sm text-green-600">
        <CheckCircleIcon className="w-5 h-5" />
        <span>All changes saved</span>
      </div>
    );
  }

  if (isSaving) {
    return (
      <div className="flex items-center gap-2 text-sm text-gray-600">
        <div className="w-5 h-5 border-2 border-gray-300 border-t-primary-500 rounded-full animate-spin" />
        <span>Saving...</span>
      </div>
    );
  }

  if (connectionStatus === 'disconnected') {
    return (
      <div className="flex items-center gap-2 text-sm text-red-600">
        <ExclamationCircleIcon className="w-5 h-5" />
        <span>Disconnected - Changes not saved</span>
      </div>
    );
  }

  return (
    <div className="flex items-center gap-2 text-sm text-gray-600">
      <div className="w-5 h-5 border-2 border-gray-300 border-t-primary-500 rounded-full animate-spin" />
      <span>Connecting...</span>
    </div>
  );
}
```

---

## 10. Features Implementation

### 10.1 Authentication Features

#### Login Page
```jsx
// pages/auth/LoginPage.jsx
import { useState } from 'react';
import { useNavigate, Link } from 'react-router-dom';
import { useAuthStore } from '../../store/authStore';
import { authService } from '../../services/authService';
import { toast } from 'react-hot-toast';

export function LoginPage() {
  const navigate = useNavigate();
  const { setUser } = useAuthStore();
  const [formData, setFormData] = useState({ email: '', password: '' });
  const [isLoading, setIsLoading] = useState(false);

  const handleSubmit = async (e) => {
    e.preventDefault();
    setIsLoading(true);

    try {
      const response = await authService.login(formData);
      setUser(response.data);
      toast.success('Login successful!');
      navigate('/dashboard');
    } catch (error) {
      toast.error(error.response?.data?.message || 'Login failed');
    } finally {
      setIsLoading(false);
    }
  };

  return (
    <div className="min-h-screen flex items-center justify-center bg-gray-50 px-4">
      <div className="max-w-md w-full bg-white rounded-lg shadow-lg p-8">
        <h1 className="text-3xl font-bold text-center mb-8">Sign In</h1>
        
        <form onSubmit={handleSubmit} className="space-y-6">
          <div>
            <label className="block text-sm font-medium text-gray-700 mb-2">
              Email
            </label>
            <input
              type="email"
              required
              value={formData.email}
              onChange={(e) => setFormData({ ...formData, email: e.target.value })}
              className="w-full px-4 py-2 border border-gray-300 rounded-lg
                         focus:ring-2 focus:ring-primary-500 focus:border-transparent"
              placeholder="john@example.com"
            />
          </div>

          <div>
            <label className="block text-sm font-medium text-gray-700 mb-2">
              Password
            </label>
            <input
              type="password"
              required
              value={formData.password}
              onChange={(e) => setFormData({ ...formData, password: e.target.value })}
              className="w-full px-4 py-2 border border-gray-300 rounded-lg
                         focus:ring-2 focus:ring-primary-500 focus:border-transparent"
              placeholder="••••••••"
            />
          </div>

          <div className="flex items-center justify-between">
            <Link to="/forgot-password" className="text-sm text-primary-600 hover:underline">
              Forgot password?
            </Link>
          </div>

          <button
            type="submit"
            disabled={isLoading}
            className="w-full px-4 py-2 bg-primary-500 text-white rounded-lg
                       hover:bg-primary-600 transition-colors disabled:opacity-50"
          >
            {isLoading ? 'Signing in...' : 'Sign In'}
          </button>
        </form>

        <p className="mt-8 text-center text-sm text-gray-600">
          Don't have an account?{' '}
          <Link to="/register" className="text-primary-600 hover:underline font-medium">
            Sign up
          </Link>
        </p>
      </div>
    </div>
  );
}
```

### 10.2 Dashboard Features

#### Document Grid with Search
```jsx
// pages/dashboard/DashboardPage.jsx
import { useState } from 'react';
import { useQuery } from 'react-query';
import { DocumentCard } from '../../components/document/DocumentCard';
import { CreateDocumentModal } from '../../components/document/CreateDocumentModal';
import { SearchBar } from '../../components/common/SearchBar';
import { documentService } from '../../services/documentService';
import { PlusIcon } from '@heroicons/react/24/outline';

export function DashboardPage() {
  const [page, setPage] = useState(0);
  const [search, setSearch] = useState('');
  const [showCreateModal, setShowCreateModal] = useState(false);

  const { data, isLoading } = useQuery(
    ['documents', page, search],
    () => documentService.listDocuments({ page, size: 20, search }),
    { keepPreviousData: true }
  );

  return (
    <div className="min-h-screen bg-gray-50">
      {/* Header */}
      <header className="bg-white border-b border-gray-200">
        <div className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8 py-4">
          <div className="flex items-center justify-between">
            <h1 className="text-2xl font-bold text-gray-900">My Documents</h1>
            <button
              onClick={() => setShowCreateModal(true)}
              className="flex items-center gap-2 px-4 py-2 bg-primary-500 text-white
                         rounded-lg hover:bg-primary-600 transition-colors"
            >
              <PlusIcon className="w-5 h-5" />
              New Document
            </button>
          </div>
        </div>
      </header>

      {/* Main Content */}
      <main className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8 py-8">
        {/* Search Bar */}
        <SearchBar
          value={search}
          onChange={setSearch}
          placeholder="Search documents..."
          className="mb-8"
        />

        {/* Document Grid */}
        {isLoading ? (
          <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-6">
            {[...Array(6)].map((_, i) => (
              <div key={i} className="h-48 bg-gray-200 rounded-lg animate-pulse" />
            ))}
          </div>
        ) : data?.content?.length === 0 ? (
          <div className="text-center py-12">
            <p className="text-gray-500 mb-4">No documents yet</p>
            <button
              onClick={() => setShowCreateModal(true)}
              className="px-4 py-2 bg-primary-500 text-white rounded-lg"
            >
              Create your first document
            </button>
          </div>
        ) : (
          <>
            <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-6">
              {data?.content?.map((doc) => (
                <DocumentCard key={doc.id} document={doc} />
              ))}
            </div>

            {/* Pagination */}
            {data?.totalPages > 1 && (
              <div className="flex justify-center gap-2 mt-8">
                <button
                  onClick={() => setPage(p => Math.max(0, p - 1))}
                  disabled={page === 0}
                  className="px-4 py-2 border border-gray-300 rounded-lg disabled:opacity-50"
                >
                  Previous
                </button>
                <span className="px-4 py-2">
                  Page {page + 1} of {data.totalPages}
                </span>
                <button
                  onClick={() => setPage(p => p + 1)}
                  disabled={page >= data.totalPages - 1}
                  className="px-4 py-2 border border-gray-300 rounded-lg disabled:opacity-50"
                >
                  Next
                </button>
              </div>
            )}
          </>
        )}
      </main>

      {/* Create Document Modal */}
      {showCreateModal && (
        <CreateDocumentModal onClose={() => setShowCreateModal(false)} />
      )}
    </div>
  );
}
```

### 10.3 Editor Page Complete

```jsx
// pages/editor/EditorPage.jsx
import { useParams, useNavigate } from 'react-router-dom';
import { useQuery } from 'react-query';
import { Editor } from '../../components/editor/Editor';
import { EditorMenuBar } from '../../components/editor/EditorMenuBar';
import { ActiveUsersList } from '../../components/editor/ActiveUsersList';
import { ConnectionStatus } from '../../components/editor/ConnectionStatus';
import { ShareButton } from '../../components/editor/ShareButton';
import { documentService } from '../../services/documentService';
import { ArrowLeftIcon, EllipsisVerticalIcon } from '@heroicons/react/24/outline';
import { useState } from 'react';

export function EditorPage() {
  const { documentId } = useParams();
  const navigate = useNavigate();
  const [showMenu, setShowMenu] = useState(false);

  const { data: document, isLoading } = useQuery(
    ['document', documentId],
    () => documentService.getDocument(documentId)
  );

  if (isLoading) {
    return (
      <div className="h-screen flex items-center justify-center">
        <div className="text-center">
          <div className="w-12 h-12 border-4 border-primary-500 border-t-transparent 
                          rounded-full animate-spin mx-auto mb-4" />
          <p className="text-gray-600">Loading document...</p>
        </div>
      </div>
    );
  }

  if (!document) {
    return (
      <div className="h-screen flex items-center justify-center">
        <div className="text-center">
          <p className="text-red-600 mb-4">Document not found</p>
          <button
            onClick={() => navigate('/dashboard')}
            className="px-4 py-2 bg-primary-500 text-white rounded-lg"
          >
            Back to Dashboard
          </button>
        </div>
      </div>
    );
  }

  return (
    <div className="h-screen flex flex-col bg-gray-50">
      {/* Header */}
      <header className="bg-white border-b border-gray-200 px-4 py-3">
        <div className="flex items-center justify-between">
          <div className="flex items-center gap-4 flex-1">
            <button
              onClick={() => navigate('/dashboard')}
              className="p-2 hover:bg-gray-100 rounded-lg"
            >
              <ArrowLeftIcon className="w-5 h-5" />
            </button>

            <h1 className="text-xl font-semibold truncate max-w-md">
              {document.title}
            </h1>

            <ConnectionStatus />
          </div>

          <div className="flex items-center gap-4">
            <ActiveUsersList yjsRoomId={document.yjsRoomId} />
            <ShareButton documentId={document.id} />
            
            <button
              onClick={() => setShowMenu(!showMenu)}
              className="p-2 hover:bg-gray-100 rounded-lg relative"
            >
              <EllipsisVerticalIcon className="w-5 h-5" />
              
              {showMenu && (
                <div className="absolute right-0 top-full mt-2 w-48 bg-white border
                                border-gray-200 rounded-lg shadow-lg py-2 z-10">
                  <button
                    onClick={() => navigate(`/document/${documentId}/versions`)}
                    className="w-full px-4 py-2 text-left hover:bg-gray-100"
                  >
                    Version History
                  </button>
                  <button
                    onClick={() => navigate(`/document/${documentId}/settings`)}
                    className="w-full px-4 py-2 text-left hover:bg-gray-100"
                  >
                    Document Settings
                  </button>
                </div>
              )}
            </button>
          </div>
        </div>
      </header>

      {/* Menu Bar */}
      <EditorMenuBar />

      {/* Editor */}
      <div className="flex-1 overflow-y-auto">
        <div className="max-w-4xl mx-auto py-8">
          <Editor
            documentId={document.id}
            yjsRoomId={document.yjsRoomId}
            role={document.role}
          />
        </div>
      </div>
    </div>
  );
}
```

---

## 11. Edge Cases & Error Handling

### 11.1 Network Errors

```javascript
// utils/errorHandler.js
import { toast } from 'react-hot-toast';

export function handleAPIError(error) {
  if (!error.response) {
    // Network error
    toast.error('Network error. Please check your connection.');
    return;
  }

  const { status, data } = error.response;

  switch (status) {
    case 401:
      toast.error('Session expired. Please login again.');
      // Redirect to login with return URL
      const returnUrl = window.location.pathname;
      localStorage.setItem('returnUrl', returnUrl);
      window.location.href = '/login';
      break;

    case 403:
      toast.error("You don't have permission to perform this action.");
      break;

    case 404:
      toast.error('Resource not found.');
      break;

    case 429:
      toast.error(`Too many requests. Please try again in ${data.retryAfter || 60} seconds.`);
      break;

    case 500:
      toast.error('Server error. Our team has been notified.');
      break;

    default:
      toast.error(data?.message || 'An error occurred. Please try again.');
  }
}
```

### 11.2 WebSocket Reconnection

```javascript
// hooks/useWebSocketReconnect.js
import { useEffect, useRef } from 'react';
import { useEditorStore } from '../store/editorStore';
import { toast } from 'react-hot-toast';

export function useWebSocketReconnect() {
  const { connectionStatus } = useEditorStore();
  const reconnectAttempts = useRef(0);
  const maxAttempts = 5;

  useEffect(() => {
    if (connectionStatus === 'disconnected') {
      if (reconnectAttempts.current < maxAttempts) {
        const delay = Math.min(1000 * Math.pow(2, reconnectAttempts.current), 30000);
        
        const timer = setTimeout(() => {
          toast.loading('Reconnecting...', { id: 'reconnect' });
          reconnectAttempts.current += 1;
          // Provider will auto-reconnect
        }, delay);

        return () => clearTimeout(timer);
      } else {
        toast.error('Connection lost. Please refresh the page.', {
          duration: Infinity,
          id: 'reconnect'
        });
      }
    } else if (connectionStatus === 'connected') {
      reconnectAttempts.current = 0;
      toast.success('Connected', { id: 'reconnect' });
    }
  }, [connectionStatus]);
}
```

### 11.3 Unsaved Changes Warning

```javascript
// hooks/useUnsavedChangesWarning.js
import { useEffect } from 'react';
import { useEditorStore } from '../store/editorStore';

export function useUnsavedChangesWarning() {
  const { isSaving, connectionStatus } = useEditorStore();

  useEffect(() => {
    const handleBeforeUnload = (e) => {
      if (isSaving || connectionStatus !== 'connected') {
        e.preventDefault();
        e.returnValue = 'You have unsaved changes. Are you sure you want to leave?';
        return e.returnValue;
      }
    };

    window.addEventListener('beforeunload', handleBeforeUnload);
    return () => window.removeEventListener('beforeunload', handleBeforeUnload);
  }, [isSaving, connectionStatus]);
}
```

### 11.4 Stale Document Detection

```javascript
// components/editor/StaleDocumentDetector.jsx
import { useEffect, useState } from 'react';
import { useQuery } from 'react-query';
import { documentService } from '../../services/documentService';

export function StaleDocumentDetector({ documentId, currentUpdatedAt }) {
  const [isStale, setIsStale] = useState(false);

  const { data } = useQuery(
    ['document-freshness', documentId],
    () => documentService.getDocument(documentId),
    {
      refetchInterval: 30000, // Check every 30 seconds
      enabled: !!documentId,
    }
  );

  useEffect(() => {
    if (data && data.updatedAt !== currentUpdatedAt) {
      setIsStale(true);
    }
  }, [data, currentUpdatedAt]);

  if (!isStale) return null;

  return (
    <div className="fixed top-16 left-1/2 transform -translate-x-1/2 z-50
                    bg-yellow-50 border border-yellow-300 rounded-lg p-4 shadow-lg">
      <p className="text-sm text-yellow-800 mb-2">
        This document was updated by another user. Refresh to see the latest version.
      </p>
      <button
        onClick={() => window.location.reload()}
        className="px-4 py-2 bg-yellow-500 text-white rounded-lg hover:bg-yellow-600"
      >
        Refresh Now
      </button>
    </div>
  );
}
```

### 11.5 Permission Denied Handler

```javascript
// components/common/PermissionDenied.jsx
import { useNavigate } from 'react-router-dom';
import { ShieldExclamationIcon } from '@heroicons/react/24/outline';

export function PermissionDenied({ message = "You don't have permission to access this resource." }) {
  const navigate = useNavigate();

  return (
    <div className="min-h-screen flex items-center justify-center bg-gray-50">
      <div className="text-center">
        <ShieldExclamationIcon className="w-24 h-24 text-red-500 mx-auto mb-4" />
        <h1 className="text-2xl font-bold text-gray-900 mb-2">Access Denied</h1>
        <p className="text-gray-600 mb-6">{message}</p>
        <button
          onClick={() => navigate('/dashboard')}
          className="px-6 py-2 bg-primary-500 text-white rounded-lg hover:bg-primary-600"
        >
          Back to Dashboard
        </button>
      </div>
    </div>
  );
}
```

---

## 12. Performance Optimization

### 12.1 Code Splitting

```javascript
// Router.jsx with lazy loading
import { lazy, Suspense } from 'react';
import { BrowserRouter, Routes, Route } from 'react-router-dom';

const DashboardPage = lazy(() => import('./pages/dashboard/DashboardPage'));
const EditorPage = lazy(() => import('./pages/editor/EditorPage'));
const VersionHistoryPage = lazy(() => import('./pages/version/VersionHistoryPage'));

function LoadingFallback() {
  return (
    <div className="h-screen flex items-center justify-center">
      <div className="w-12 h-12 border-4 border-primary-500 border-t-transparent rounded-full animate-spin" />
    </div>
  );
}

function Router() {
  return (
    <BrowserRouter>
      <Suspense fallback={<LoadingFallback />}>
        <Routes>
          <Route path="/dashboard" element={<DashboardPage />} />
          <Route path="/editor/:documentId" element={<EditorPage />} />
          <Route path="/document/:documentId/versions" element={<VersionHistoryPage />} />
        </Routes>
      </Suspense>
    </BrowserRouter>
  );
}
```

### 12.2 Virtualized Lists

```javascript
// For large document lists
import { FixedSizeGrid } from 'react-window';

function VirtualizedDocumentGrid({ documents }) {
  const columnCount = 3;
  const rowCount = Math.ceil(documents.length / columnCount);

  return (
    <FixedSizeGrid
      columnCount={columnCount}
      columnWidth={350}
      height={800}
      rowCount={rowCount}
      rowHeight={250}
      width={1100}
    >
      {({ columnIndex, rowIndex, style }) => {
        const index = rowIndex * columnCount + columnIndex;
        const doc = documents[index];
        if (!doc) return null;
        return (
          <div style={style}>
            <DocumentCard document={doc} />
          </div>
        );
      }}
    </FixedSizeGrid>
  );
}
```

### 12.3 Debounced Search

```javascript
// hooks/useDebounce.js
import { useState, useEffect } from 'react';

export function useDebounce(value, delay = 500) {
  const [debouncedValue, setDebouncedValue] = useState(value);

  useEffect(() => {
    const timer = setTimeout(() => {
      setDebouncedValue(value);
    }, delay);

    return () => clearTimeout(timer);
  }, [value, delay]);

  return debouncedValue;
}

// Usage in SearchBar
function SearchBar({ onSearch }) {
  const [query, setQuery] = useState('');
  const debouncedQuery = useDebounce(query, 300);

  useEffect(() => {
    onSearch(debouncedQuery);
  }, [debouncedQuery, onSearch]);

  return (
    <input
      value={query}
      onChange={(e) => setQuery(e.target.value)}
      placeholder="Search..."
    />
  );
}
```

### 12.4 Image Optimization

```javascript
// Use next-gen formats and lazy loading
function DocumentThumbnail({ src, alt }) {
  return (
    <img
      src={src}
      alt={alt}
      loading="lazy"
      className="w-full h-48 object-cover rounded-t-lg"
      srcSet={`${src}?w=400 400w, ${src}?w=800 800w`}
      sizes="(max-width: 400px) 400px, 800px"
    />
  );
}
```

---

## 13. Future Extensibility

### 13.1 Plugin Architecture

```javascript
// utils/plugins.js
// Define plugin interface for future extensions

export const pluginRegistry = new Map();

export function registerPlugin(name, plugin) {
  if (typeof plugin.initialize !== 'function') {
    throw new Error('Plugin must have an initialize method');
  }
  pluginRegistry.set(name, plugin);
}

export function initializePlugins(context) {
  pluginRegistry.forEach((plugin) => {
    plugin.initialize(context);
  });
}

// Example plugin: Comments
const commentsPlugin = {
  name: 'comments',
  initialize: (context) => {
    // Add comments extension to TipTap
    // Register comment UI components
    // Set up comment API endpoints
  },
};

registerPlugin('comments', commentsPlugin);
```

### 13.2 Theme System

```javascript
// contexts/ThemeContext.jsx
import { createContext, useContext, useState } from 'react';

const ThemeContext = createContext();

export function ThemeProvider({ children }) {
  const [theme, setTheme] = useState('light'); // light | dark | auto

  const toggleTheme = () => {
    setTheme(current => current === 'light' ? 'dark' : 'light');
  };

  return (
    <ThemeContext.Provider value={{ theme, toggleTheme }}>
      <div className={theme === 'dark' ? 'dark' : ''}>
        {children}
      </div>
    </ThemeContext.Provider>
  );
}

export const useTheme = () => useContext(ThemeContext);
```

### 13.3 Feature Flags

```javascript
// utils/features.js
const features = {
  comments: false,
  aiAssistant: false,
  advancedExport: false,
  templates: false,
  offlineMode: false,
};

export function isFeatureEnabled(featureName) {
  return features[featureName] || false;
}

// Usage
import { isFeatureEnabled } from './utils/features';

function EditorMenuBar() {
  return (
    <div>
      {/* Always visible */}
      <FormatButtons />
      
      {/* Conditional features */}
      {isFeatureEnabled('comments') && <CommentsButton />}
      {isFeatureEnabled('aiAssistant') && <AIAssistantButton />}
    </div>
  );
}
```

### 13.4 API Versioning Support

```javascript
// services/api.js
import axios from 'axios';

const api = axios.create({
  baseURL: process.env.REACT_APP_API_URL || 'http://localhost:8080',
  withCredentials: true,
  headers: {
    'Content-Type': 'application/json',
    'X-API-Version': 'v2', // Support for API versioning
  },
});

// Add request interceptor for future token refresh
api.interceptors.request.use((config) => {
  // Future: Add token to header if needed
  return config;
});

// Add response interceptor for error handling
api.interceptors.response.use(
  (response) => response,
  (error) => {
    // Future: Handle token refresh
    // Future: Handle rate limiting with retry
    return Promise.reject(error);
  }
);

export default api;
```

### 13.5 Analytics Integration (Prepared)

```javascript
// utils/analytics.js
class Analytics {
  track(event, properties = {}) {
    // Future: Send to analytics service (Google Analytics, Mixpanel, etc.)
    console.log('Track event:', event, properties);
  }

  identify(userId, traits = {}) {
    // Future: Identify user
    console.log('Identify user:', userId, traits);
  }

  page(name, properties = {}) {
    // Future: Track page view
    console.log('Page view:', name, properties);
  }
}

export const analytics = new Analytics();

// Usage
import { analytics } from './utils/analytics';

function DocumentCard({ document }) {
  const handleClick = () => {
    analytics.track('Document Opened', {
      documentId: document.id,
      documentTitle: document.title,
    });
    navigate(`/editor/${document.id}`);
  };

  return <div onClick={handleClick}>...</div>;
}
```

---

## 📝 Development Checklist

### Phase 1: Foundation (Week 1)
- [ ] Project setup (Create React App / Vite)
- [ ] Install dependencies
- [ ] Configure Tailwind CSS
- [ ] Set up routing
- [ ] Create folder structure
- [ ] Set up API client (Axios)
- [ ] Set up state management (Zustand)
- [ ] Create common components (Button, Input, Modal)

### Phase 2: Authentication (Week 2)
- [ ] Login page
- [ ] Register page
- [ ] OTP verification page
- [ ] Forgot password flow
- [ ] Auth store
- [ ] Protected route wrapper
- [ ] JWT cookie handling
- [ ] Session persistence

### Phase 3: Dashboard (Week 3)
- [ ] Dashboard layout
- [ ] Document list/grid
- [ ] Create document modal
- [ ] Document card component
- [ ] Search functionality
- [ ] Pagination
- [ ] Empty states
- [ ] Loading states

### Phase 4: Editor (Week 4-5)
- [ ] TipTap editor setup
- [ ] Yjs integration
- [ ] WebSocket connection
- [ ] Menu bar (formatting)
- [ ] Collaboration cursors
- [ ] Active users list
- [ ] Connection status
- [ ] Auto-save indicator
- [ ] Keyboard shortcuts

### Phase 5: Sharing (Week 6)
- [ ] Share modal
- [ ] Invite people form
- [ ] Collaborator list
- [ ] Share link generator
- [ ] Share link page (public)
- [ ] Invitation acceptance page
- [ ] Role management
- [ ] Permission display

### Phase 6: Versions (Week 7)
- [ ] Version history page
- [ ] Version timeline
- [ ] Create version modal
- [ ] View version (read-only)
- [ ] Restore version
- [ ] Delete version
- [ ] Version comparison (future)

### Phase 7: Polish (Week 8)
- [ ] Error handling
- [ ] Loading states
- [ ] Toast notifications
- [ ] Responsive design
- [ ] Mobile optimization
- [ ] Accessibility (a11y)
- [ ] Performance optimization
- [ ] Testing

---

## 🚀 Quick Start Commands

```bash
# Create React app
npx create-react-app collab-docs-frontend
cd collab-docs-frontend

# Install dependencies
npm install @tiptap/react @tiptap/starter-kit @tiptap/extension-collaboration \
            @tiptap/extension-collaboration-cursor @tiptap/extension-placeholder \
            yjs y-websocket zustand axios react-query react-router-dom \
            date-fns react-hot-toast @headlessui/react @heroicons/react \
            clsx tailwindcss

# Initialize Tailwind
npx tailwindcss init -p

# Start development server
npm start

# Build for production
npm run build
```

---

**Document Version:** 1.0  
**Last Updated:** February 13, 2026  
**Ready for Development:** ✅ YES

This guide provides everything a frontend developer needs to build a production-ready collaborative document editor!

