# ShiftTask Tracker - Standalone Web Application

This is the standalone web version of **ShiftTask Tracker**. It contains the complete web application without any Android Gradle files.

## Features Included
- **Tablet & Mobile Responsive Kiosk UI**: Optimized for in-store tablet mounts and staff phones.
- **Secure PIN Authentication**: Salted SHA-256 PIN authentication.
- **First-Time Sign-In PIN Setup**: Automatically prompts new staff members to choose and confirm their own 4-digit PIN on their first login.
- **Daniel Shaw Dedicated PIN**: Exclusively authenticated with PIN `2314`.
- **Live Checklist & Sign-Offs**: Real-time task completion with audit logging, photo uploads, and notes.
- **Admin Management**: Full layout editor, category management, task reordering, user management, and completion reporting.
- **Firebase Firestore Live Sync**: Cloud persistence with real-time listeners and offline fallbacks.

## Quick Start (Local or Cloud Server)
1. Install dependencies:
   ```bash
   npm install
   ```
2. Start the web server:
   ```bash
   npm start
   ```
3. Open your browser at `http://localhost:3000`.

## How to Create a New AI Studio Project with this Web App
1. In Google AI Studio, click **New Project** and choose **Web App** (or blank template).
2. Copy `index.html`, `server.js`, and `package.json` into your new project.
3. Your new web app will immediately launch and sync with the same cloud database!

## Deploying to Firebase Hosting
```bash
firebase deploy --only hosting
```
