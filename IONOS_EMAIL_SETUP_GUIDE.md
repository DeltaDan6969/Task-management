# IONOS Email Verification Setup Guide
### One Stop Canterbury — Account Registration & Real Email Dispatch

This guide explains how to connect your **IONOS email account** to your One Stop website so that when any customer or staff member registers an account, a real confirmation email containing a **6-digit verification code** and a **1-click activation link** is delivered directly to their inbox (Gmail, Outlook, iCloud, etc.).

---

## 🚀 Two Sending Methods Supported

The website backend includes a dual-engine email dispatcher:

| Feature | Method 1: Native IONOS Relay (`mail`) | Method 2: Authenticated SMTP (`smtp`) — Recommended |
|---|---|---|
| **Setup Time** | Instant (0 minutes) | 2 minutes |
| **Password Required** | No password needed | Mailbox password needed |
| **Deliverability** | High (routed via IONOS server) | **Maximum (SPF, DKIM, DMARC compliant)** |
| **SPAM Protection** | Good | **Best (Passes Gmail & Outlook DMARC checks)** |
| **Configuration** | `'mailer' => 'mail'` | `'mailer' => 'smtp'` |

---

## 🛠️ Step 1: Create a Mailbox in your IONOS Account

If you haven't already created an email address under your domain:

1. Log in to your [IONOS Cloud / Webhosting Control Panel](https://my.ionos.co.uk) (or [my.ionos.com](https://my.ionos.com)).
2. In the top navigation, click on **Email**.
3. Click **Create Email Address** (or **Mailbox**).
4. Choose **Mailbox Basic** (standard included mailbox).
5. Enter your preferred address, for example:
   - `noreply@yourdomain.co.uk` (or `accounts@yourdomain.co.uk`)
6. Set a strong password and save it securely.
7. Click **Save**.

---

### 📋 Official IONOS Manual Setup Specifications

Below are the exact settings provided by IONOS for UK mailboxes:

| Parameter | Setting | Notes |
|---|---|---|
| **User name** | `<Your full IONOS email address>` | e.g. `noreply@yourdomain.co.uk` |
| **Password** | `<Your chosen mailbox password>` | Stored securely on server |
| **Incoming Server (IMAP)** | `imap.ionos.co.uk` | Recommended protocol |
| **Incoming Port** | **`993`** | **SSL must be activated** |
| **Outgoing Server (SMTP)** | `smtp.ionos.co.uk` | Outgoing mail dispatch |
| **Outgoing Port** | **`587`** | **TLS must be activated** |
| **Outgoing Server Requires Auth** | **Yes** | Uses same username & password |

---

## ⚙️ Step 2: Configure `api/config.php` (or use Admin UI)

Inside the website root directory (or extracted from `ionos-onestop-website.zip`), you will find the file **`api/config.php`**. You can also edit these settings directly in the website **Admin Menu** under **IONOS Mail Server Settings (IMAP & SMTP)**!

```php
<?php
/**
 * IONOS Email & Verification Configuration
 */
return [
    'from_email' => 'noreply@yourdomain.co.uk',
    'from_name'  => 'One Stop Canterbury',
    'mailer'     => 'smtp',
    'imap' => [
        'host'     => 'imap.ionos.co.uk',
        'port'     => 993,
        'secure'   => 'ssl'
    ],
    'smtp' => [
        'host'     => 'smtp.ionos.co.uk',
        'port'     => 587,                      // Port 587 (TLS activated - Official IONOS Outgoing Port)
        'secure'   => 'tls',
        'auth'     => true,
        'username' => 'noreply@yourdomain.co.uk',
        'password' => 'YourSecretMailboxPasswordHere',
        'timeout'  => 15
    ]
];
```

> 💡 **Quick Tip for Method 1 (`mail`)**:  
> If you set `'mailer' => 'mail'`, IONOS Linux Webhosting will automatically route emails through the internal sendmail relay using the `-f envelope sender` flag configured in `api/verify.php`. You only need to set `'from_email'` to match your domain!

---

## 🛡️ Step 3: Enable SPF & DKIM in IONOS (Prevents Emails Going to Spam)

Google, Yahoo, and Microsoft now require SPF and DKIM records to accept incoming emails without routing them to Spam/Junk folders. IONOS configures this automatically with 1 click:

1. In your IONOS Control Panel, navigate to **Domains & SSL**.
2. Click your domain name, then click the **DNS** tab.
3. Scroll to **Email Settings** (or **Mail Records**).
4. Verify that:
   - **SPF Record** is active (IONOS default: `v=spf1 include:_spf.ionos.co.uk ~all`).
   - **DKIM (DomainKeys)** is enabled (Click **Enable DKIM** if prompted).
5. DNS changes propagate within 5–15 minutes.

---

## 📂 Step 4: Ensure Data Directory is Writable

The verification backend stores registered users and verification tokens in the `data/` folder (`data/users.json` and `data/emails.json`).

On IONOS Webhosting:
1. In the **IONOS Webspace Explorer**, right-click the `data` folder.
2. Select **Change Permissions** (or `chmod`).
3. Set the permission to **`755`** (Read & Write for Owner, Read for Group and Public).
4. The PHP script will automatically create `data/users.json` and `data/emails.json` if they do not exist.

---

## 🧪 Step 5: Test the System Live

1. Open your published website on your domain:  
   `https://yourdomain.co.uk`
2. Click **Create Account**.
3. Fill in your name, a real test email address (e.g. your personal Gmail or Outlook), and password.
4. Select **Customer** or **Staff**.
5. Click **Create Account**.
6. **Check your inbox**: You will receive a branded email:
   - **Subject**: `Your One Stop Verification Code: 123456`
   - **Body**: Beautiful One Stop blue/red layout with the 6-digit PIN and a clickable **VERIFY EMAIL WITH 1-CLICK** button.
7. Enter the 6-digit code into the prompt or click the button. Your account is immediately activated!

---

## 🔍 Diagnostics & Verification Log

If you ever need to inspect sent emails or test without checking your inbox:
- Click **"Verification Email Dispatch Log"** on the website registration screen to see real-time dispatch diagnostics, codes, and delivery statuses.
- You can also view `data/emails.json` on your server for the complete audit trail.
