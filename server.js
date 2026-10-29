const http = require('http');
const fs = require('fs');
const path = require('path');
const crypto = require('crypto');
const url = require('url');
const net = require('net');
const tls = require('tls');

const PORT = 3000;
const WEB_DIR = path.join(__dirname, 'web');
const DATA_DIR = path.join(__dirname, 'data');
const USERS_FILE = path.join(DATA_DIR, 'users.json');
const EMAILS_FILE = path.join(DATA_DIR, 'emails.json');
const MAIL_CONFIG_FILE = path.join(DATA_DIR, 'mail-config.json');

// Ensure data directory exists
if (!fs.existsSync(DATA_DIR)) {
  fs.mkdirSync(DATA_DIR, { recursive: true });
}

// -----------------------------------------------------------------------------
// DATA PERSISTENCE HELPERS
// -----------------------------------------------------------------------------
function loadUsers() {
  try {
    if (fs.existsSync(USERS_FILE)) {
      return JSON.parse(fs.readFileSync(USERS_FILE, 'utf8'));
    }
  } catch (err) {
    console.error('Error loading users.json:', err.message);
  }
  return [];
}

function saveUsers(users) {
  try {
    fs.writeFileSync(USERS_FILE, JSON.stringify(users, null, 2), 'utf8');
  } catch (err) {
    console.error('Error saving users.json:', err.message);
  }
}

function loadEmails() {
  try {
    if (fs.existsSync(EMAILS_FILE)) {
      return JSON.parse(fs.readFileSync(EMAILS_FILE, 'utf8'));
    }
  } catch (err) {
    console.error('Error loading emails.json:', err.message);
  }
  return [];
}

function saveEmails(emails) {
  try {
    fs.writeFileSync(EMAILS_FILE, JSON.stringify(emails, null, 2), 'utf8');
  } catch (err) {
    console.error('Error saving emails.json:', err.message);
  }
}

// -----------------------------------------------------------------------------
// CRYPTO HELPERS
// -----------------------------------------------------------------------------
function hashWithSalt(password, salt) {
  return crypto.createHash('sha256').update(password + ':' + salt).digest('hex');
}

function generateRandomPin() {
  return Math.floor(100000 + Math.random() * 900000).toString();
}

function generateToken() {
  return crypto.randomBytes(24).toString('hex');
}

// Initialize default admin user if not present
(function initDefaultAdmin() {
  const users = loadUsers();
  const adminExists = users.some(u => u.email.toLowerCase() === 'admin@onestop.co.uk');
  if (!adminExists) {
    const salt = crypto.randomBytes(16).toString('hex');
    const passwordHash = hashWithSalt('Admin123!', salt);
    users.push({
      id: 'usr_admin',
      name: 'Canterbury Store Admin',
      email: 'admin@onestop.co.uk',
      salt: salt,
      passwordHash: passwordHash,
      role: 'Admin',
      permissions: ['manage_tasks', 'audit_approvals', 'manage_users', 'assign_permissions', 'store_inventory'],
      emailVerified: true,
      registeredAt: new Date().toISOString()
    });
    saveUsers(users);
    console.log('👑 Initialized default Administrator account: admin@onestop.co.uk');
  }
})();

// -----------------------------------------------------------------------------
// DYNAMIC MAIL CONFIGURATION & LIVE SMTP DISPATCHER
// -----------------------------------------------------------------------------
function loadMailConfig() {
  const defaults = {
    provider: 'ionos',
    from_email: process.env.IONOS_EMAIL_USER || 'noreply@onestop-canterbury.co.uk',
    from_name: 'One Stop Canterbury',
    imap_host: 'imap.ionos.co.uk',
    imap_port: 993,
    imap_secure: 'ssl',
    smtp_host: process.env.IONOS_SMTP_HOST || 'smtp.ionos.co.uk',
    smtp_port: parseInt(process.env.IONOS_SMTP_PORT, 10) || 587,
    smtp_secure: 'tls',
    smtp_auth: true,
    smtp_user: process.env.IONOS_EMAIL_USER || '',
    smtp_pass: process.env.IONOS_EMAIL_PASS || '',
    timeout: 15
  };
  try {
    if (fs.existsSync(MAIL_CONFIG_FILE)) {
      const data = JSON.parse(fs.readFileSync(MAIL_CONFIG_FILE, 'utf8'));
      return { ...defaults, ...data };
    }
  } catch (err) {
    console.error('Error loading mail-config.json:', err.message);
  }
  return defaults;
}

function saveMailConfig(cfg) {
  try {
    fs.writeFileSync(MAIL_CONFIG_FILE, JSON.stringify(cfg, null, 2), 'utf8');
    syncPhpMailConfig(cfg);
  } catch (err) {
    console.error('Error saving mail-config.json:', err.message);
  }
}

function syncPhpMailConfig(cfg) {
  const phpTargets = [
    path.join(__dirname, 'web', 'api', 'config.php'),
    path.join(__dirname, 'docs', 'api', 'config.php'),
    path.join(__dirname, 'standalone-web-app', 'api', 'config.php'),
    path.join(__dirname, 'ionos-package', 'api', 'config.php'),
  ];
  const escapedFromName = (cfg.from_name || 'One Stop Canterbury').replace(/'/g, "\\'");
  const escapedPass = (cfg.smtp_pass || '').replace(/'/g, "\\'");
  const smtpPort = parseInt(cfg.smtp_port, 10) || 587;
  const phpContent = `<?php
/**
 * IONOS Email & Verification Configuration (Auto-synced from Admin Settings)
 * Official IONOS Settings:
 * - Incoming: imap.ionos.co.uk : 993 (SSL)
 * - Outgoing: smtp.ionos.co.uk : 587 (TLS, Auth required)
 */
return [
    'from_email' => '${cfg.from_email || cfg.smtp_user || 'noreply@yourdomain.co.uk'}',
    'from_name'  => '${escapedFromName}',
    'mailer'     => '${cfg.smtp_user && cfg.smtp_pass ? 'smtp' : 'mail'}',
    'imap' => [
        'host'     => '${cfg.imap_host || 'imap.ionos.co.uk'}',
        'port'     => ${cfg.imap_port || 993},
        'secure'   => 'ssl'
    ],
    'smtp' => [
        'host'     => '${cfg.smtp_host || 'smtp.ionos.co.uk'}',
        'port'     => ${smtpPort},
        'secure'   => '${smtpPort === 465 ? 'ssl' : 'tls'}',
        'auth'     => true,
        'username' => '${cfg.smtp_user || ''}',
        'password' => '${escapedPass}',
        'timeout'  => ${cfg.timeout || 15}
    ]
];
`;
  phpTargets.forEach(target => {
    try {
      const dir = path.dirname(target);
      if (fs.existsSync(dir)) {
        fs.writeFileSync(target, phpContent, 'utf8');
      }
    } catch (e) {
      console.error('Error syncing config.php to ' + target + ':', e.message);
    }
  });
}

/**
 * Live SMTP Client for IONOS / standard SMTP servers (Zero external dependencies)
 */
function sendLiveSmtp({ host, port, user, pass, from, fromName, to, subject, bodyHtml, timeout = 15 }) {
  return new Promise((resolve, reject) => {
    const portNum = parseInt(port, 10);
    const isDirectSsl = (portNum === 465);
    let sock;
    let stage = 0;
    let buffer = '';
    const logs = [];

    const appendLog = (msg) => {
      logs.push(msg);
      console.log(`[SMTP] ${msg}`);
    };

    const cleanup = () => {
      if (sock && !sock.destroyed) {
        sock.destroy();
      }
    };

    const send = (cmd) => {
      const display = cmd.startsWith('AUTH') || stage === 3 || stage === 4 ? '[AUTH DATA]' : cmd;
      appendLog(`> ${display}`);
      sock.write(cmd + '\r\n');
    };

    const handleSmtpLine = (code, line) => {
      if (stage === 0 && code === '220') {
        stage = 1;
        send('EHLO onestop-canterbury.co.uk');
      } else if (stage === 1 && code === '250') {
        if (!isDirectSsl && portNum === 587) {
          // Port 587 requires STARTTLS before AUTH
          stage = 15;
          send('STARTTLS');
        } else if (user && pass) {
          stage = 2;
          send('AUTH LOGIN');
        } else {
          stage = 5;
          send(`MAIL FROM:<${from}>`);
        }
      } else if (stage === 15 && code === '220') {
        // Upgrade plain socket to TLS
        appendLog('Upgrading connection to TLS for port 587...');
        sock.removeAllListeners('data');
        const secureSock = tls.connect({
          socket: sock,
          host: host,
          rejectUnauthorized: false
        }, () => {
          appendLog('TLS handshake established successfully on port 587.');
          sock = secureSock;
          sock.on('data', onData);
          stage = 16;
          send('EHLO onestop-canterbury.co.uk');
        });
        secureSock.on('error', (err) => {
          cleanup();
          reject(new Error(`TLS upgrade error on ${host}:${port}: ${err.message}`));
        });
      } else if (stage === 16 && code === '250') {
        if (user && pass) {
          stage = 2;
          send('AUTH LOGIN');
        } else {
          stage = 5;
          send(`MAIL FROM:<${from}>`);
        }
      } else if (stage === 2 && code === '334') {
        stage = 3;
        send(Buffer.from(user).toString('base64'));
      } else if (stage === 3 && code === '334') {
        stage = 4;
        send(Buffer.from(pass).toString('base64'));
      } else if (stage === 4 && code === '235') {
        stage = 5;
        send(`MAIL FROM:<${from}>`);
      } else if (stage === 5 && code === '250') {
        stage = 6;
        send(`RCPT TO:<${to}>`);
      } else if (stage === 6 && code === '250') {
        stage = 7;
        send('DATA');
      } else if (stage === 7 && code === '354') {
        stage = 8;
        const msgHeaders = [
          `From: "${fromName}" <${from}>`,
          `To: <${to}>`,
          `Subject: ${subject}`,
          'MIME-Version: 1.0',
          'Content-Type: text/html; charset=UTF-8',
          `Date: ${new Date().toUTCString()}`,
          `Message-ID: <${Date.now()}.${Math.random().toString(36).substr(2, 8)}@${host}>`
        ];
        const rawMsg = msgHeaders.join('\r\n') + '\r\n\r\n' + bodyHtml + '\r\n.';
        sock.write(rawMsg + '\r\n');
      } else if (stage === 8 && code === '250') {
        stage = 9;
        send('QUIT');
        sock.end();
        resolve({ success: true, message: `Email delivered to ${to}`, logs });
      } else if (code.startsWith('4') || code.startsWith('5')) {
        cleanup();
        reject(new Error(`SMTP server rejected: ${line}`));
      }
    };

    const onData = (data) => {
      buffer += data.toString();
      const lines = buffer.split('\r\n');
      buffer = lines.pop();

      for (const line of lines) {
        if (!line) continue;
        appendLog(`< ${line}`);
        const code = line.substring(0, 3);
        const isLast = line.charAt(3) === ' ' || line.length === 3;

        if (!isLast) continue; // Multi-line response like 250-SIZE, wait for final line
        handleSmtpLine(code, line);
      }
    };

    try {
      if (isDirectSsl) {
        sock = tls.connect(port, host, { rejectUnauthorized: false }, () => {
          appendLog(`Connected securely via TLS to ${host}:${port}`);
        });
      } else {
        sock = net.createConnection(port, host, () => {
          appendLog(`Connected via TCP to ${host}:${port}`);
        });
      }

      sock.on('data', onData);
      sock.on('error', (err) => {
        cleanup();
        reject(new Error(`Connection error to ${host}:${port}: ${err.message}`));
      });
      sock.setTimeout(timeout * 1000, () => {
        cleanup();
        reject(new Error(`Connection timed out after ${timeout} seconds`));
      });
    } catch (err) {
      reject(err);
    }
  });
}

async function dispatchVerificationEmail(to, name, pin, token, reqHost) {
  const host = reqHost || 'localhost:3000';
  const protocol = host.includes('localhost') ? 'http' : 'https';
  const verificationLink = `${protocol}://${host}/?action=verify&token=${token}&email=${encodeURIComponent(to)}`;
  const expiresAt = Date.now() + 15 * 60 * 1000; // 15 mins

  const mailCfg = loadMailConfig();
  const fromEmail = mailCfg.from_email || mailCfg.smtp_user || 'noreply@onestop-canterbury.co.uk';
  const fromName = mailCfg.from_name || 'One Stop Canterbury';
  const subject = `Your One Stop Verification Code: ${pin}`;

  const emailRecord = {
    id: 'msg_' + Date.now() + '_' + Math.random().toString(36).substr(2, 5),
    to: to,
    from: fromEmail,
    subject: subject,
    name: name,
    pin: pin,
    token: token,
    verificationLink: verificationLink,
    expiresAt: expiresAt,
    expiresAtReadable: new Date(expiresAt).toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' }),
    sentAt: new Date().toISOString(),
    displayTime: new Date().toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' }),
    delivered: false,
    deliveryMethod: 'PENDING',
    deliveryError: null,
    bodyHtml: `
      <div style="font-family: Arial, sans-serif; max-width: 520px; margin: 0 auto; padding: 24px; border: 1px solid #BFDBFE; border-radius: 12px; background: #FFFFFF;">
        <div style="text-align: center; margin-bottom: 20px;">
          <div style="background: #002B49; color: #FFFFFF; display: inline-block; padding: 6px 14px; border-radius: 20px; font-weight: bold; font-size: 14px;">one stop</div>
          <h2 style="color: #002B49; margin-top: 10px; font-size: 22px;">Verify Your Email Address</h2>
        </div>
        <p style="color: #334155; font-size: 14px; line-height: 1.6;">Hello <strong>${name}</strong>,</p>
        <p style="color: #334155; font-size: 14px; line-height: 1.6;">Thank you for registering your One Stop Canterbury account. Please use the 6-digit confirmation code below to verify your email address:</p>
        <div style="background: #F0F7FF; border: 2px dashed #0A58CA; border-radius: 10px; padding: 18px; text-align: center; margin: 20px 0;">
          <div style="font-size: 11px; font-weight: bold; color: #64748B; text-transform: uppercase; letter-spacing: 1px;">Your Verification Code</div>
          <div style="font-size: 32px; font-weight: 900; color: #0A58CA; letter-spacing: 6px; margin: 8px 0;">${pin}</div>
          <div style="font-size: 12px; color: #64748B;">Valid for 15 minutes (expires at ${new Date(expiresAt).toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' })})</div>
        </div>
        <div style="text-align: center; margin: 20px 0;">
          <a href="${verificationLink}" style="background: #0A58CA; color: #FFFFFF; padding: 12px 24px; border-radius: 8px; text-decoration: none; font-weight: bold; font-size: 14px; display: inline-block;">VERIFY EMAIL WITH 1-CLICK</a>
        </div>
        <p style="font-size: 11px; color: #94A3B8; text-align: center; margin-top: 24px; border-top: 1px solid #E2E8F0; padding-top: 14px;">
          If you did not request this verification, you can safely ignore this email.<br>
          One Stop Stores Ltd • Canterbury High Street Branch
        </p>
      </div>
    `
  };

  // Attempt live outbound SMTP delivery if credentials are provided
  if (mailCfg.smtp_user && mailCfg.smtp_pass && mailCfg.smtp_pass !== 'YourIonosEmailPasswordHere') {
    try {
      console.log(`📤 Dispatching live verification email to ${to} via ${mailCfg.smtp_host}:${mailCfg.smtp_port}...`);
      await sendLiveSmtp({
        host: mailCfg.smtp_host,
        port: mailCfg.smtp_port,
        user: mailCfg.smtp_user,
        pass: mailCfg.smtp_pass,
        from: fromEmail,
        fromName: fromName,
        to: to,
        subject: subject,
        bodyHtml: emailRecord.bodyHtml,
        timeout: mailCfg.timeout || 15
      });
      emailRecord.delivered = true;
      emailRecord.deliveryMethod = 'IONOS_SMTP';
      console.log(`✅ Live verification email delivered successfully to ${to}`);
    } catch (smtpErr) {
      emailRecord.delivered = false;
      emailRecord.deliveryMethod = 'IONOS_SMTP_FAILED';
      emailRecord.deliveryError = smtpErr.message;
      console.error(`❌ Live SMTP delivery failed: ${smtpErr.message}`);
    }
  } else {
    emailRecord.delivered = false;
    emailRecord.deliveryMethod = 'UNCONFIGURED';
    emailRecord.deliveryError = 'Mail credentials not set in Admin Settings. Please configure your IONOS email and password.';
    console.warn(`⚠️ Mail API not configured with credentials. Verification code recorded to audit log.`);
  }

  const emails = loadEmails();
  emails.unshift(emailRecord);
  if (emails.length > 50) emails.length = 50;
  saveEmails(emails);

  return emailRecord;
}

// -----------------------------------------------------------------------------
// HTTP PARSING & API HANDLERS
// -----------------------------------------------------------------------------
const MIME_TYPES = {
  '.html': 'text/html; charset=utf-8',
  '.css': 'text/css; charset=utf-8',
  '.js': 'text/javascript; charset=utf-8',
  '.json': 'application/json; charset=utf-8',
  '.png': 'image/png',
  '.jpg': 'image/jpeg',
  '.jpeg': 'image/jpeg',
  '.svg': 'image/svg+xml',
  '.ico': 'image/x-icon',
  '.apk': 'application/vnd.android.package-archive',
  '.zip': 'application/zip'
};

function parseJsonBody(req) {
  return new Promise((resolve, reject) => {
    let body = '';
    req.on('data', chunk => {
      body += chunk.toString();
      if (body.length > 1e6) { // 1MB limit
        req.destroy();
        reject(new Error('Body too large'));
      }
    });
    req.on('end', () => {
      try {
        resolve(body ? JSON.parse(body) : {});
      } catch (err) {
        reject(err);
      }
    });
    req.on('error', reject);
  });
}

function sendJson(res, statusCode, data) {
  const jsonStr = JSON.stringify(data);
  res.writeHead(statusCode, {
    'Content-Type': 'application/json; charset=utf-8',
    'Content-Length': Buffer.byteLength(jsonStr),
    'Access-Control-Allow-Origin': '*',
    'Access-Control-Allow-Headers': 'Content-Type, Authorization',
    'Access-Control-Allow-Methods': 'GET, POST, PUT, DELETE, OPTIONS'
  });
  res.end(jsonStr);
}

// API ROUTE DISPATCHER
async function handleApiRequest(req, res, parsedUrl) {
  const pathname = parsedUrl.pathname;
  const method = req.method.toUpperCase();

  // CORS Preflight
  if (method === 'OPTIONS') {
    res.writeHead(204, {
      'Access-Control-Allow-Origin': '*',
      'Access-Control-Allow-Headers': 'Content-Type, Authorization',
      'Access-Control-Allow-Methods': 'GET, POST, PUT, DELETE, OPTIONS'
    });
    res.end();
    return;
  }

  // 1. Health check
  if (pathname === '/api/health') {
    sendJson(res, 200, { status: 'ok', serverTime: new Date().toISOString() });
    return;
  }

  // 2. Register: POST /api/auth/register
  if (pathname === '/api/auth/register' && method === 'POST') {
    try {
      const body = await parseJsonBody(req);
      const { name, email, salt, passwordHash, role } = body;

      if (!name || !email || !passwordHash || !salt) {
        sendJson(res, 400, { success: false, error: 'Missing required registration fields.' });
        return;
      }

      const cleanEmail = email.trim().toLowerCase();
      const users = loadUsers();

      if (users.some(u => u.email.toLowerCase() === cleanEmail)) {
        sendJson(res, 409, { success: false, error: 'An account with this email address already exists.' });
        return;
      }

      const pin = generateRandomPin();
      const token = generateToken();
      const expiresAt = Date.now() + 15 * 60 * 1000;

      const newUser = {
        id: 'usr_' + Date.now() + '_' + Math.random().toString(36).substr(2, 5),
        name: name.trim(),
        email: cleanEmail,
        salt: salt,
        passwordHash: passwordHash,
        role: role || 'Customer',
        permissions: role === 'Staff' ? ['manage_tasks'] : [],
        emailVerified: false,
        verificationPin: pin,
        verificationToken: token,
        verificationExpiresAt: expiresAt,
        registeredAt: new Date().toISOString()
      };

      users.push(newUser);
      saveUsers(users);

      // Dispatch Email
      const emailRecord = await dispatchVerificationEmail(cleanEmail, name, pin, token, req.headers.host);

      sendJson(res, 201, {
        success: true,
        message: 'Account created. Verification code sent to your email.',
        email: cleanEmail,
        expiresAt: expiresAt,
        emailId: emailRecord.id,
        emailDispatched: emailRecord.delivered,
        deliveryMethod: emailRecord.deliveryMethod,
        deliveryError: emailRecord.deliveryError
      });
    } catch (err) {
      console.error('Register API error:', err);
      sendJson(res, 500, { success: false, error: 'Registration server error: ' + err.message });
    }
    return;
  }

  // 3. Verify Code: POST /api/auth/verify-code
  if (pathname === '/api/auth/verify-code' && method === 'POST') {
    try {
      const body = await parseJsonBody(req);
      const { email, code } = body;

      if (!email || !code) {
        sendJson(res, 400, { success: false, error: 'Email and verification code are required.' });
        return;
      }

      const cleanEmail = email.trim().toLowerCase();
      const cleanCode = code.toString().trim();
      const users = loadUsers();
      const user = users.find(u => u.email.toLowerCase() === cleanEmail);

      if (!user) {
        sendJson(res, 404, { success: false, error: 'No account found for this email address.' });
        return;
      }

      if (user.emailVerified) {
        sendJson(res, 200, {
          success: true,
          message: 'Account is already verified.',
          user: {
            id: user.id,
            name: user.name,
            email: user.email,
            role: user.role,
            permissions: user.permissions,
            emailVerified: true
          }
        });
        return;
      }

      // Check expiration
      if (user.verificationExpiresAt && Date.now() > user.verificationExpiresAt) {
        sendJson(res, 400, {
          success: false,
          error: 'Verification code has expired. Please request a new code.',
          expired: true
        });
        return;
      }

      // Check PIN match
      if (user.verificationPin !== cleanCode) {
        sendJson(res, 400, {
          success: false,
          error: 'Incorrect verification code. Please check your email and try again.'
        });
        return;
      }

      // Mark verified
      user.emailVerified = true;
      delete user.verificationPin;
      delete user.verificationToken;
      delete user.verificationExpiresAt;
      user.verifiedAt = new Date().toISOString();

      saveUsers(users);

      const sessionToken = crypto.randomBytes(32).toString('hex');
      sendJson(res, 200, {
        success: true,
        message: 'Email verified successfully! Welcome to One Stop Canterbury.',
        token: sessionToken,
        user: {
          id: user.id,
          name: user.name,
          email: user.email,
          role: user.role,
          permissions: user.permissions,
          emailVerified: true
        }
      });
    } catch (err) {
      console.error('Verify Code API error:', err);
      sendJson(res, 500, { success: false, error: 'Verification server error: ' + err.message });
    }
    return;
  }

  // 4. Resend Code: POST /api/auth/resend-code
  if (pathname === '/api/auth/resend-code' && method === 'POST') {
    try {
      const body = await parseJsonBody(req);
      const { email } = body;

      if (!email) {
        sendJson(res, 400, { success: false, error: 'Email address is required.' });
        return;
      }

      const cleanEmail = email.trim().toLowerCase();
      const users = loadUsers();
      const user = users.find(u => u.email.toLowerCase() === cleanEmail);

      if (!user) {
        sendJson(res, 404, { success: false, error: 'No account registered with this email.' });
        return;
      }

      if (user.emailVerified) {
        sendJson(res, 200, { success: true, message: 'Account is already verified. You can log in directly.' });
        return;
      }

      const newPin = generateRandomPin();
      const newToken = generateToken();
      const newExpiresAt = Date.now() + 15 * 60 * 1000;

      user.verificationPin = newPin;
      user.verificationToken = newToken;
      user.verificationExpiresAt = newExpiresAt;
      saveUsers(users);

      const emailRecord = await dispatchVerificationEmail(cleanEmail, user.name, newPin, newToken, req.headers.host);

      sendJson(res, 200, {
        success: true,
        message: 'A fresh verification code has been dispatched to your email.',
        expiresAt: newExpiresAt,
        emailId: emailRecord.id,
        emailDispatched: emailRecord.delivered,
        deliveryMethod: emailRecord.deliveryMethod,
        deliveryError: emailRecord.deliveryError
      });
    } catch (err) {
      console.error('Resend Code API error:', err);
      sendJson(res, 500, { success: false, error: 'Resend server error: ' + err.message });
    }
    return;
  }

  // 5. Login: POST /api/auth/login
  if (pathname === '/api/auth/login' && method === 'POST') {
    try {
      const body = await parseJsonBody(req);
      const { email, passwordHash, rawPassword } = body;

      if (!email) {
        sendJson(res, 400, { success: false, error: 'Email address is required.' });
        return;
      }

      const cleanEmail = email.trim().toLowerCase();
      const users = loadUsers();
      const user = users.find(u => u.email.toLowerCase() === cleanEmail);

      if (!user) {
        sendJson(res, 404, { success: false, error: 'No account found with this email address.' });
        return;
      }

      // Password comparison: supports both client-hashed comparison and rawPassword fallback
      let isMatch = false;
      if (passwordHash) {
        isMatch = (user.passwordHash === passwordHash);
      } else if (rawPassword) {
        const computedHash = hashWithSalt(rawPassword, user.salt);
        isMatch = (user.passwordHash === computedHash);
      }

      if (!isMatch) {
        sendJson(res, 401, { success: false, error: 'Invalid password. Please check and try again.' });
        return;
      }

      // Check if email is verified
      if (!user.emailVerified) {
        sendJson(res, 403, {
          success: false,
          requiresVerification: true,
          email: user.email,
          message: 'Your email address is not yet verified. Please enter the verification code sent to your email.'
        });
        return;
      }

      const sessionToken = crypto.randomBytes(32).toString('hex');
      sendJson(res, 200, {
        success: true,
        message: 'Signed in successfully.',
        token: sessionToken,
        user: {
          id: user.id,
          name: user.name,
          email: user.email,
          role: user.role,
          permissions: user.permissions,
          emailVerified: true
        }
      });
    } catch (err) {
      console.error('Login API error:', err);
      sendJson(res, 500, { success: false, error: 'Login server error: ' + err.message });
    }
    return;
  }

  // 6. Direct Link Verification: GET /api/auth/verify?token=...&email=...
  if (pathname === '/api/auth/verify' && method === 'GET') {
    const query = parsedUrl.query;
    const token = query.token;
    const email = query.email ? query.email.toLowerCase() : null;

    if (!token) {
      sendJson(res, 400, { success: false, error: 'Verification token is missing.' });
      return;
    }

    const users = loadUsers();
    const user = users.find(u => u.verificationToken === token || (email && u.email.toLowerCase() === email && u.verificationToken === token));

    if (!user) {
      res.writeHead(302, { Location: '/?verified=false&reason=invalid_token' });
      res.end();
      return;
    }

    user.emailVerified = true;
    delete user.verificationPin;
    delete user.verificationToken;
    delete user.verificationExpiresAt;
    user.verifiedAt = new Date().toISOString();
    saveUsers(users);

    res.writeHead(302, { Location: `/?verified=true&email=${encodeURIComponent(user.email)}` });
    res.end();
    return;
  }

  // 7. Get Recent Inbox: GET /api/auth/inbox
  if (pathname === '/api/auth/inbox' && method === 'GET') {
    const emails = loadEmails();
    sendJson(res, 200, { success: true, emails: emails });
    return;
  }

  // 8. Admin Get All Users: GET /api/admin/users
  if (pathname === '/api/admin/users' && method === 'GET') {
    const users = loadUsers();
    // Strip sensitive fields like salt and hash
    const sanitized = users.map(u => ({
      id: u.id,
      name: u.name,
      email: u.email,
      role: u.role,
      permissions: u.permissions || [],
      emailVerified: !!u.emailVerified,
      registeredAt: u.registeredAt,
      verifiedAt: u.verifiedAt
    }));
    sendJson(res, 200, { success: true, users: sanitized });
    return;
  }

  // 9. Admin Update User: POST /api/admin/update-permissions
  if (pathname === '/api/admin/update-permissions' && method === 'POST') {
    try {
      const body = await parseJsonBody(req);
      const { email, role, permissions, emailVerified } = body;

      if (!email) {
        sendJson(res, 400, { success: false, error: 'User email is required.' });
        return;
      }

      const users = loadUsers();
      const user = users.find(u => u.email.toLowerCase() === email.trim().toLowerCase());

      if (!user) {
        sendJson(res, 404, { success: false, error: 'User not found.' });
        return;
      }

      if (role) user.role = role;
      if (Array.isArray(permissions)) user.permissions = permissions;
      if (typeof emailVerified === 'boolean') user.emailVerified = emailVerified;

      saveUsers(users);
      sendJson(res, 200, { success: true, message: 'Permissions updated successfully.', user });
    } catch (err) {
      console.error('Update permissions API error:', err);
      sendJson(res, 500, { success: false, error: 'Failed to update permissions: ' + err.message });
    }
    return;
  }

  // 10. Admin Delete User: DELETE /api/admin/delete-user
  if (pathname === '/api/admin/delete-user' && method === 'DELETE') {
    try {
      const body = await parseJsonBody(req);
      const { email } = body;

      if (!email) {
        sendJson(res, 400, { success: false, error: 'User email is required.' });
        return;
      }

      let users = loadUsers();
      if (email.toLowerCase() === 'admin@onestop.co.uk') {
        sendJson(res, 403, { success: false, error: 'Cannot delete primary Administrator account.' });
        return;
      }

      users = users.filter(u => u.email.toLowerCase() !== email.toLowerCase());
      saveUsers(users);
      sendJson(res, 200, { success: true, message: 'User deleted.' });
    } catch (err) {
      sendJson(res, 500, { success: false, error: err.message });
    }
    return;
  }

  // 11. Admin Get Mail Config: GET /api/admin/mail-config
  if (pathname === '/api/admin/mail-config' && method === 'GET') {
    const cfg = loadMailConfig();
    sendJson(res, 200, {
      success: true,
      config: {
        provider: cfg.provider || 'ionos',
        from_email: cfg.from_email || '',
        from_name: cfg.from_name || 'One Stop Canterbury',
        imap_host: cfg.imap_host || 'imap.ionos.co.uk',
        imap_port: cfg.imap_port || 993,
        smtp_host: cfg.smtp_host || 'smtp.ionos.co.uk',
        smtp_port: cfg.smtp_port || 587,
        smtp_user: cfg.smtp_user || '',
        has_password: !!(cfg.smtp_pass && cfg.smtp_pass !== 'YourIonosEmailPasswordHere'),
        timeout: cfg.timeout || 15
      }
    });
    return;
  }

  // 12. Admin Save Mail Config: POST /api/admin/mail-config
  if (pathname === '/api/admin/mail-config' && method === 'POST') {
    try {
      const body = await parseJsonBody(req);
      const current = loadMailConfig();
      const updated = {
        ...current,
        provider: body.provider || current.provider || 'ionos',
        from_email: body.from_email ? body.from_email.trim() : current.from_email,
        from_name: body.from_name ? body.from_name.trim() : current.from_name,
        imap_host: body.imap_host ? body.imap_host.trim() : (current.imap_host || 'imap.ionos.co.uk'),
        imap_port: parseInt(body.imap_port, 10) || current.imap_port || 993,
        smtp_host: body.smtp_host ? body.smtp_host.trim() : current.smtp_host,
        smtp_port: parseInt(body.smtp_port, 10) || current.smtp_port || 587,
        smtp_user: body.smtp_user ? body.smtp_user.trim() : current.smtp_user,
        timeout: parseInt(body.timeout, 10) || 15
      };
      if (body.smtp_pass && body.smtp_pass !== '••••••••') {
        updated.smtp_pass = body.smtp_pass;
      }
      saveMailConfig(updated);
      sendJson(res, 200, { success: true, message: 'Mail configuration saved successfully.', config: {
        provider: updated.provider,
        from_email: updated.from_email,
        from_name: updated.from_name,
        imap_host: updated.imap_host,
        imap_port: updated.imap_port,
        smtp_host: updated.smtp_host,
        smtp_port: updated.smtp_port,
        smtp_user: updated.smtp_user,
        has_password: !!(updated.smtp_pass && updated.smtp_pass !== 'YourIonosEmailPasswordHere')
      }});
    } catch (err) {
      sendJson(res, 500, { success: false, error: err.message });
    }
    return;
  }

  // 13. Admin Test Mail Delivery: POST /api/admin/test-email
  if (pathname === '/api/admin/test-email' && method === 'POST') {
    try {
      const body = await parseJsonBody(req);
      const targetEmail = (body.toEmail || '').trim().toLowerCase();
      if (!targetEmail) {
        sendJson(res, 400, { success: false, error: 'Recipient email address is required.' });
        return;
      }

      let cfg = loadMailConfig();
      if ((!cfg.smtp_user || !cfg.smtp_pass || cfg.smtp_pass === 'YourIonosEmailPasswordHere') && body.clientConfig) {
        if (body.clientConfig.smtp_user && body.clientConfig.smtp_pass) {
          cfg = {
            ...cfg,
            smtp_host: body.clientConfig.smtp_host || cfg.smtp_host || 'smtp.ionos.co.uk',
            smtp_port: parseInt(body.clientConfig.smtp_port, 10) || cfg.smtp_port || 465,
            smtp_user: body.clientConfig.smtp_user,
            smtp_pass: body.clientConfig.smtp_pass,
            from_name: body.clientConfig.from_name || cfg.from_name || 'One Stop Canterbury',
            from_email: body.clientConfig.from_email || body.clientConfig.smtp_user
          };
          saveMailConfig(cfg);
        }
      }

      if (!cfg.smtp_user || !cfg.smtp_pass || cfg.smtp_pass === 'YourIonosEmailPasswordHere') {
        sendJson(res, 400, {
          success: false,
          error: 'Please enter and save your IONOS email address and mailbox password first.'
        });
        return;
      }

      const testPin = Math.floor(100000 + Math.random() * 900000).toString();
      const testHtml = `
        <div style="font-family: Arial, sans-serif; max-width: 520px; margin: 0 auto; padding: 24px; border: 1px solid #BFDBFE; border-radius: 12px; background: #FFFFFF;">
          <div style="text-align: center; margin-bottom: 20px;">
            <div style="background: #002B49; color: #FFFFFF; display: inline-block; padding: 6px 14px; border-radius: 20px; font-weight: bold; font-size: 14px;">one stop</div>
            <h2 style="color: #002B49; margin-top: 10px; font-size: 22px;">IONOS Mail API Connection Test</h2>
          </div>
          <p style="color: #334155; font-size: 14px; line-height: 1.6;">Hello,</p>
          <p style="color: #334155; font-size: 14px; line-height: 1.6;">This test confirms that your <strong>IONOS SMTP connection</strong> is active and delivering emails to real inboxes:</p>
          <div style="background: #F0F7FF; border: 2px dashed #0A58CA; border-radius: 10px; padding: 18px; text-align: center; margin: 20px 0;">
            <div style="font-size: 11px; font-weight: bold; color: #64748B; text-transform: uppercase; letter-spacing: 1px;">TEST CODE</div>
            <div style="font-size: 32px; font-weight: 900; color: #0A58CA; letter-spacing: 6px; margin: 8px 0;">${testPin}</div>
            <div style="font-size: 12px; color: #64748B;">Dispatched via ${cfg.smtp_host}:${cfg.smtp_port} at ${new Date().toLocaleTimeString()}</div>
          </div>
          <p style="font-size: 12px; color: #10B981; font-weight: bold; text-align: center;">✅ Live delivery confirmed.</p>
        </div>
      `;

      console.log(`🧪 Sending test email to ${targetEmail} via ${cfg.smtp_host}:${cfg.smtp_port}...`);
      const testRes = await sendLiveSmtp({
        host: cfg.smtp_host,
        port: cfg.smtp_port,
        user: cfg.smtp_user,
        pass: cfg.smtp_pass,
        from: cfg.from_email || cfg.smtp_user,
        fromName: cfg.from_name || 'One Stop Canterbury',
        to: targetEmail,
        subject: `[TEST] One Stop Canterbury Mail Verification: ${testPin}`,
        bodyHtml: testHtml,
        timeout: cfg.timeout || 15
      });

      sendJson(res, 200, {
        success: true,
        message: `Test email successfully delivered to ${targetEmail}! Please check your inbox and spam folder.`,
        logs: testRes.logs
      });
    } catch (err) {
      console.error('Test email delivery error:', err.message);
      sendJson(res, 500, {
        success: false,
        error: `SMTP connection failed: ${err.message}. Please check your host, port (465 SSL recommended), username, and password.`
      });
    }
    return;
  }

  // Not found
  sendJson(res, 404, { success: false, error: 'API route not found' });
}

// -----------------------------------------------------------------------------
// MAIN REQUEST HANDLER (STATIC + API)
// -----------------------------------------------------------------------------
function handleRequest(req, res) {
  const parsedUrl = url.parse(req.url, true);

  // Check if API endpoint
  if (parsedUrl.pathname.startsWith('/api/')) {
    handleApiRequest(req, res, parsedUrl);
    return;
  }

  let reqPath = parsedUrl.pathname;
  if (reqPath === '/' || reqPath === '') {
    reqPath = '/index.html';
  } else if (reqPath === '/tasks' || reqPath === '/tasks/' || reqPath.startsWith('/tasks/')) {
    reqPath = '/tasks.html';
  }

  // Prevent directory traversal
  const safePath = path.normalize(reqPath).replace(/^(\.\.[\/\\])+/, '');
  let filePath = path.join(WEB_DIR, safePath);

  // If requesting root-level APK, asset, or zip, check root dir
  if (!fs.existsSync(filePath)) {
    const rootCandidate = path.join(__dirname, safePath);
    if (fs.existsSync(rootCandidate) && !fs.statSync(rootCandidate).isDirectory()) {
      filePath = rootCandidate;
    }
  }

  fs.stat(filePath, (err, stats) => {
    if (err || stats.isDirectory()) {
      // Fallback to tasks.html or index.html for SPA routes
      const fallbackFile = reqPath.startsWith('/tasks') ? 'tasks.html' : 'index.html';
      const fallbackPath = path.join(WEB_DIR, fallbackFile);
      fs.readFile(fallbackPath, (err2, pageData) => {
        if (err2) {
          res.writeHead(404, { 'Content-Type': 'text/plain' });
          res.end('404 Not Found');
        } else {
          res.writeHead(200, {
            'Content-Type': 'text/html; charset=utf-8',
            'Content-Length': Buffer.byteLength(pageData)
          });
          res.end(pageData);
        }
      });
      return;
    }

    const ext = path.extname(filePath).toLowerCase();
    const contentType = MIME_TYPES[ext] || 'application/octet-stream';
    const headers = {
      'Content-Type': contentType,
      'Content-Length': stats.size,
      'Accept-Ranges': 'bytes'
    };

    if (ext === '.apk' || ext === '.zip') {
      headers['Content-Disposition'] = `attachment; filename="${path.basename(filePath)}"`;
    }

    const stream = fs.createReadStream(filePath);
    stream.on('error', (streamErr) => {
      console.error('File stream error:', streamErr);
      if (!res.headersSent) {
        res.writeHead(500, { 'Content-Type': 'text/plain' });
        res.end('500 Internal Server Error');
      }
    });
    stream.pipe(res);
  });
}

const server = http.createServer(handleRequest);

server.on('clientError', (err, socket) => {
  if (err.code === 'ECONNRESET' || !socket.writable) {
    return;
  }
  socket.end('HTTP/1.1 400 Bad Request\r\n\r\n');
});

server.listen(PORT, '0.0.0.0', () => {
  console.log(`🚀 One Stop Canterbury Server with Email Verification API listening on port ${PORT}`);
});

process.on('uncaughtException', (err) => {
  console.error('Server uncaughtException:', err);
});

process.on('unhandledRejection', (reason) => {
  console.error('Server unhandledRejection:', reason);
});
