# IONOS Web Hosting Deployment Guide
## One Stop Canterbury Website & ShiftTask Tracker Web App

This guide explains how to upload and deploy your new blue-and-white One Stop Canterbury website and ShiftTask Tracker web application to **IONOS**.

---

## 📦 What Is Inside the Package

Your zip archive (`ionos-onestop-website.zip` and `standalone-web-app.zip`) contains:
- `index.html` - The new blue & white One Stop website with encrypted client-side SHA-256 account login, confirmation email verification, administrator user & permission management, and member "Coming Soon" portal.
- `tasks.html` - The complete ShiftTask Tracker web app (with PIN setup, real-time checklist, probe temperature logs, audit photo groupings, and night-shift theme).
- `.htaccess` - Optimized Apache web server configuration for IONOS:
  - Forces secure HTTPS connection automatically.
  - Rewrites clean URLs (e.g. `yourdomain.co.uk/tasks` directly serves `tasks.html`).
  - Sets browser caching headers and MIME types for fast PWA loading.
- `assets/` - Contains the storefront photo (`storefront.jpeg`) and brand icons.
- `manifest.json` & PWA icons - iOS and Android home screen install support.
- `404.html` - Error fallback.

---

## 🚀 Option 1: Upload via IONOS Webspace Explorer (Easiest - No Software Needed)

1. Log into your **[IONOS Control Panel](https://my.ionos.co.uk)** (or `my.ionos.com`).
2. Go to **Hosting** (or **Webhosting**).
3. Under your hosting contract, click on **Webspace** or **File Manager** (Webspace Explorer).
4. Navigate to the root directory where your domain points (typically `/` or `/var/www/vhosts/...` or the folder assigned to your domain in the **Domains & SSL** section).
5. Click **Upload**:
   - You can upload `ionos-onestop-website.zip`.
   - Once uploaded, select the zip file in Webspace Explorer and choose **Extract** (or **Unpack**).
   - If Webspace Explorer does not support direct unzipping, simply unzip `ionos-onestop-website.zip` on your computer first, then drag and drop all the extracted files into the Webspace Explorer window.
6. Make sure `index.html` and `.htaccess` are directly in the root folder of your website.
7. Open your domain in any browser (e.g. `https://your-domain.co.uk`). Your new site is live!

---

## 🛰️ Option 2: Upload via SFTP / FTP (FileZilla / Cyberduck)

1. In your IONOS Control Panel, go to **Hosting** > **FTP Details** (or **SFTP / SSH**).
2. Note your:
   - **Host / Server**: (e.g. `accessXXXXXX.webspace-data.io`)
   - **Username**: (e.g. `u12345678`)
   - **Password**: Your FTP password (or set a new one if you haven't yet)
   - **Port**: `22` for SFTP, or `21` for FTP.
3. Open **FileZilla**:
   - Enter Host, Username, Password, and Port, then click **Quickconnect**.
4. In the **Remote Site** panel (right side), navigate to your website root directory.
5. In the **Local Site** panel (left side), navigate to the unzipped contents of `ionos-onestop-website.zip`.
6. Select all files and folders (`index.html`, `tasks.html`, `.htaccess`, `assets/`, `manifest.json`, etc.) and drag them to the remote folder.
7. Wait for the transfer to complete (100% successful transfers).

---

## 🔒 Enabling Free SSL Certificate on IONOS

1. In the IONOS Control Panel, go to **Domains & SSL**.
2. Click the gear icon next to your domain name and select **Manage SSL**.
3. Activate the free included **Wildcard SSL / Standard SSL certificate**.
4. Thanks to the provided `.htaccess` file, all HTTP requests will automatically redirect to `https://`.

---

## 🧪 Testing Your Live Site on IONOS

- **Homepage**: `https://yourdomain.co.uk/`
  - Blue & White design featuring the storefront picture.
  - "Create Account" with salted SHA-256 password hashing.
  - Confirmation email verification with interactive inbox widget.
  - Regular users see the "Coming Soon" member dashboard.
  - Store administrators see the user directory & permission assignment panel.
- **Task Manager App**: `https://yourdomain.co.uk/tasks` (or `https://yourdomain.co.uk/tasks.html`)
  - Direct access to the ShiftTask Tracker.
  - Header has a `"← WEBSITE"` button to jump back to the homepage.
