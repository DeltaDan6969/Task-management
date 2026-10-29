<?php
/**
 * One Stop Canterbury - Email Verification Backend (PHP / IONOS Webhosting Edition)
 * Provides REST API endpoints for:
 * - register (salted SHA-256 password hash, 6-digit PIN generation, confirmation email)
 * - verify-code (6-digit verification code check)
 * - resend-code (fresh 6-digit code dispatch)
 * - login (credential check and email verification gate)
 * - inbox (retrieval of dispatched emails)
 * - admin/users (user directory and permission management)
 */

header('Content-Type: application/json; charset=utf-8');
header('Access-Control-Allow-Origin: *');
header('Access-Control-Allow-Headers: Content-Type, Authorization');
header('Access-Control-Allow-Methods: GET, POST, PUT, DELETE, OPTIONS');

if ($_SERVER['REQUEST_METHOD'] === 'OPTIONS') {
    http_response_code(204);
    exit;
}

// Prevent PHP notices/warnings from breaking JSON API responses
@ini_set('display_errors', '0');
error_reporting(E_ALL & ~E_NOTICE & ~E_WARNING);

// Resolve data directory: works for both web/api and root api/ deployments on IONOS
$DATA_DIR = null;
$possibleDataDirs = [
    __DIR__ . '/../data',
    __DIR__ . '/../../data',
    dirname(__DIR__) . '/data'
];
foreach ($possibleDataDirs as $dirCandidate) {
    if (is_dir($dirCandidate)) {
        $DATA_DIR = $dirCandidate;
        break;
    }
}
if (!$DATA_DIR) {
    $DATA_DIR = __DIR__ . '/../data';
    @mkdir($DATA_DIR, 0755, true);
}

$USERS_FILE = $DATA_DIR . '/users.json';
$EMAILS_FILE = $DATA_DIR . '/emails.json';

function loadUsers($file) {
    if (file_exists($file)) {
        $data = json_decode(file_get_contents($file), true);
        if (is_array($data)) return $data;
    }
    return [];
}

function saveUsers($file, $users) {
    file_put_contents($file, json_encode($users, JSON_PRETTY_PRINT));
}

function loadEmails($file) {
    if (file_exists($file)) {
        $data = json_decode(file_get_contents($file), true);
        if (is_array($data)) return $data;
    }
    return [];
}

function saveEmails($file, $emails) {
    file_put_contents($file, json_encode($emails, JSON_PRETTY_PRINT));
}

// Seed default admin if missing
$users = loadUsers($USERS_FILE);
$hasAdmin = false;
foreach ($users as $u) {
    if (strtolower($u['email']) === 'admin@onestop.co.uk') {
        $hasAdmin = true;
        break;
    }
}
if (!$hasAdmin) {
    $salt = bin2hex(random_bytes(16));
    $hash = hash('sha256', 'Admin123!:' . $salt);
    $users[] = [
        'id' => 'usr_admin',
        'name' => 'Canterbury Store Admin',
        'email' => 'admin@onestop.co.uk',
        'salt' => $salt,
        'passwordHash' => $hash,
        'role' => 'Admin',
        'permissions' => ['manage_tasks', 'audit_approvals', 'manage_users', 'assign_permissions', 'store_inventory'],
        'emailVerified' => true,
        'registeredAt' => date('c')
    ];
    saveUsers($USERS_FILE, $users);
}

$input = json_decode(file_get_contents('php://input'), true) ?? [];
$action = $_GET['action'] ?? $_GET['endpoint'] ?? '';
$method = $_SERVER['REQUEST_METHOD'];

// Route parsing
if (empty($action) && isset($_SERVER['REQUEST_URI'])) {
    $path = parse_url($_SERVER['REQUEST_URI'], PHP_URL_PATH);
    if (preg_match('#/api/auth/([a-zA-Z0-9_-]+)#', $path, $m)) {
        $action = $m[1];
    } elseif (preg_match('#/api/admin/([a-zA-Z0-9_-]+)#', $path, $m)) {
        $action = 'admin-' . $m[1];
    }
}

// Load IONOS Email Configuration (Syncs with JSON config and PHP config)
$JSON_CONFIG = $DATA_DIR . '/mail-config.json';
$CONFIG_FILE = __DIR__ . '/config.php';
$emailConfig = [
    'from_email' => 'noreply@onestop-canterbury.co.uk',
    'from_name'  => 'One Stop Canterbury',
    'mailer'     => 'mail',
    'smtp'       => [
        'host'     => 'smtp.ionos.co.uk',
        'port'     => 465,
        'secure'   => 'ssl',
        'username' => '',
        'password' => '',
        'timeout'  => 15
    ]
];

if (file_exists($JSON_CONFIG)) {
    $j = json_decode(file_get_contents($JSON_CONFIG), true);
    if (is_array($j)) {
        if (!empty($j['from_email'])) $emailConfig['from_email'] = $j['from_email'];
        if (!empty($j['from_name'])) $emailConfig['from_name'] = $j['from_name'];
        if (!empty($j['smtp_host'])) $emailConfig['smtp']['host'] = $j['smtp_host'];
        if (!empty($j['smtp_port'])) $emailConfig['smtp']['port'] = (int)$j['smtp_port'];
        if (!empty($j['smtp_user'])) $emailConfig['smtp']['username'] = $j['smtp_user'];
        if (!empty($j['smtp_pass'])) $emailConfig['smtp']['password'] = $j['smtp_pass'];
        $emailConfig['smtp']['secure'] = ($emailConfig['smtp']['port'] === 465) ? 'ssl' : 'tls';
        $emailConfig['mailer'] = (!empty($j['smtp_user']) && !empty($j['smtp_pass'])) ? 'smtp' : 'mail';
    }
} elseif (file_exists($CONFIG_FILE)) {
    $cfg = include $CONFIG_FILE;
    if (is_array($cfg)) {
        $emailConfig = array_merge($emailConfig, $cfg);
    }
}

/**
 * Pure PHP SMTP Client for IONOS SMTP Server (Supports Port 465 SSL and Port 587 STARTTLS)
 */
function sendSmtpIonos($smtp, $fromEmail, $fromName, $to, $subject, $bodyHtml) {
    $host = $smtp['host'] ?? 'smtp.ionos.co.uk';
    $port = (int)($smtp['port'] ?? 465);
    $user = $smtp['username'] ?? '';
    $pass = $smtp['password'] ?? '';
    $timeout = (int)($smtp['timeout'] ?? 15);
    $isSsl = ($port === 465 || ($smtp['secure'] ?? '') === 'ssl');

    $ctx = stream_context_create([
        'ssl' => [
            'verify_peer' => false,
            'verify_peer_name' => false,
            'allow_self_signed' => true
        ]
    ]);

    $remote = $isSsl ? "ssl://{$host}:{$port}" : "tcp://{$host}:{$port}";
    $socket = @stream_socket_client($remote, $errno, $errstr, $timeout, STREAM_CLIENT_CONNECT, $ctx);
    if (!$socket) {
        return ['success' => false, 'error' => "Cannot connect to IONOS SMTP ({$remote}): $errstr ($errno)"];
    }

    $read = function() use ($socket) {
        $response = '';
        while ($line = fgets($socket, 515)) {
            $response .= $line;
            if (substr($line, 3, 1) === ' ') break;
        }
        return $response;
    };

    $send = function($cmd) use ($socket) {
        fputs($socket, $cmd . "\r\n");
    };

    $res = $read();
    if (substr($res, 0, 3) !== '220') {
        fclose($socket);
        return ['success' => false, 'error' => "Unexpected welcome: $res"];
    }

    $send("EHLO " . ($_SERVER['SERVER_NAME'] ?? 'localhost'));
    $res = $read();

    // If port 587 and not direct SSL, issue STARTTLS
    if ($port == 587 && !$isSsl) {
        $send("STARTTLS");
        $res = $read();
        if (substr($res, 0, 3) === '220') {
            if (!stream_socket_enable_crypto($socket, true, STREAM_CRYPTO_METHOD_TLS_CLIENT)) {
                fclose($socket);
                return ['success' => false, 'error' => 'STARTTLS negotiation failed.'];
            }
            $send("EHLO " . ($_SERVER['SERVER_NAME'] ?? 'localhost'));
            $res = $read();
        }
    }

    // AUTH LOGIN
    if (!empty($user) && !empty($pass)) {
        $send("AUTH LOGIN");
        $res = $read();
        if (substr($res, 0, 3) !== '334') {
            fclose($socket);
            return ['success' => false, 'error' => "AUTH not accepted: $res"];
        }

        $send(base64_encode($user));
        $res = $read();
        $send(base64_encode($pass));
        $res = $read();
        if (substr($res, 0, 3) !== '235') {
            fclose($socket);
            return ['success' => false, 'error' => "Authentication failed: $res"];
        }
    }

    // MAIL FROM & RCPT TO
    $send("MAIL FROM:<$fromEmail>");
    $res = $read();
    $send("RCPT TO:<$to>");
    $res = $read();
    if (substr($res, 0, 3) !== '250') {
        fclose($socket);
        return ['success' => false, 'error' => "Recipient rejected: $res"];
    }

    // DATA
    $send("DATA");
    $res = $read();
    if (substr($res, 0, 3) !== '354') {
        fclose($socket);
        return ['success' => false, 'error' => "DATA rejected: $res"];
    }

    $headers = [
        "From: $fromName <$fromEmail>",
        "To: <$to>",
        "Subject: $subject",
        "MIME-Version: 1.0",
        "Content-Type: text/html; charset=UTF-8",
        "Date: " . date('r'),
        "Message-ID: <" . time() . "." . md5($to) . "@" . ($smtp['host'] ?? 'ionos.com') . ">"
    ];

    $emailData = implode("\r\n", $headers) . "\r\n\r\n" . $bodyHtml . "\r\n.";
    $send($emailData);
    $res = $read();
    $send("QUIT");
    fclose($socket);

    if (substr($res, 0, 3) === '250') {
        return ['success' => true];
    }
    return ['success' => false, 'error' => "Sending failed: $res"];
}

// Dispatch Email Function (Dual Mode: IONOS SMTP or Native PHP Mail with -f Envelope Sender)
function dispatchEmailPHP($to, $name, $pin, $token, $emailsFile, $config = []) {
    $host = $_SERVER['HTTP_HOST'] ?? 'localhost';
    $protocol = isset($_SERVER['HTTPS']) && $_SERVER['HTTPS'] === 'on' ? 'https' : 'http';
    $link = "{$protocol}://{$host}/?action=verify&token={$token}&email=" . urlencode($to);
    $expiresAt = time() + (15 * 60);

    $fromEmail = $config['from_email'] ?? 'noreply@onestop-canterbury.co.uk';
    $fromName = $config['from_name'] ?? 'One Stop Canterbury';
    $mailer = strtolower($config['mailer'] ?? 'mail');

    $subject = "Your One Stop Verification Code: {$pin}";
    $bodyHtml = "
      <div style='font-family: Arial, sans-serif; max-width: 520px; margin: 0 auto; padding: 24px; border: 1px solid #BFDBFE; border-radius: 12px; background: #FFFFFF;'>
        <div style='text-align: center; margin-bottom: 20px;'>
          <div style='background: #002B49; color: #FFFFFF; display: inline-block; padding: 6px 14px; border-radius: 20px; font-weight: bold; font-size: 14px;'>one stop</div>
          <h2 style='color: #002B49; margin-top: 10px; font-size: 22px;'>Verify Your Email Address</h2>
        </div>
        <p style='color: #334155; font-size: 14px; line-height: 1.6;'>Hello <strong>{$name}</strong>,</p>
        <p style='color: #334155; font-size: 14px; line-height: 1.6;'>Thank you for creating your One Stop account. Please use the 6-digit confirmation code below to activate your account:</p>
        <div style='background: #F0F7FF; border: 2px dashed #0A58CA; border-radius: 10px; padding: 18px; text-align: center; margin: 20px 0;'>
          <div style='font-size: 11px; font-weight: bold; color: #64748B; text-transform: uppercase; letter-spacing: 1px;'>Your Verification Code</div>
          <div style='font-size: 32px; font-weight: 900; color: #0A58CA; letter-spacing: 6px; margin: 8px 0;'>{$pin}</div>
          <div style='font-size: 12px; color: #64748B;'>Valid for 15 minutes (expires at " . date('H:i', $expiresAt) . ")</div>
        </div>
        <div style='text-align: center; margin: 20px 0;'>
          <a href='{$link}' style='background: #0A58CA; color: #FFFFFF; padding: 12px 24px; border-radius: 8px; text-decoration: none; font-weight: bold; font-size: 14px; display: inline-block;'>VERIFY EMAIL WITH 1-CLICK</a>
        </div>
        <p style='font-size: 11px; color: #94A3B8; text-align: center; margin-top: 24px; border-top: 1px solid #E2E8F0; padding-top: 14px;'>
          If you did not request this verification, you can safely ignore this email.<br>
          One Stop Stores Ltd • Canterbury High Street Branch
        </p>
      </div>
    ";

    $delivered = false;
    $deliveryMethod = 'simulated';
    $deliveryError = null;

    // Try IONOS SMTP first if configured
    if ($mailer === 'smtp' && !empty($config['smtp']['username']) && !empty($config['smtp']['password']) && $config['smtp']['password'] !== 'YourIonosEmailPasswordHere') {
        $smtpResult = sendSmtpIonos($config['smtp'], $fromEmail, $fromName, $to, $subject, $bodyHtml);
        if ($smtpResult['success']) {
            $delivered = true;
            $deliveryMethod = 'IONOS_SMTP';
        } else {
            $deliveryError = $smtpResult['error'] ?? 'SMTP Error';
        }
    }

    // If not SMTP or SMTP failed, send via native IONOS Webhosting mail() with -f flag
    if (!$delivered) {
        $headers = "From: {$fromName} <{$fromEmail}>\r\n";
        $headers .= "Reply-To: {$fromEmail}\r\n";
        $headers .= "MIME-Version: 1.0\r\n";
        $headers .= "Content-Type: text/html; charset=UTF-8\r\n";
        // On IONOS, passing '-f ' . $fromEmail sets the authenticated envelope sender
        $sent = @mail($to, $subject, $bodyHtml, $headers, "-f " . $fromEmail);
        if ($sent) {
            $delivered = true;
            $deliveryMethod = 'IONOS_PHP_MAIL';
        }
    }

    $emailRecord = [
        'id' => 'msg_' . time() . '_' . substr(md5(rand()), 0, 5),
        'to' => $to,
        'from' => $fromEmail,
        'subject' => $subject,
        'name' => $name,
        'pin' => $pin,
        'token' => $token,
        'verificationLink' => $link,
        'expiresAt' => $expiresAt * 1000,
        'expiresAtReadable' => date('H:i', $expiresAt),
        'sentAt' => date('c'),
        'displayTime' => date('H:i'),
        'delivered' => $delivered,
        'deliveryMethod' => $deliveryMethod,
        'deliveryError' => $deliveryError
    ];

    $emails = loadEmails($emailsFile);
    array_unshift($emails, $emailRecord);
    if (count($emails) > 50) array_splice($emails, 50);
    saveEmails($emailsFile, $emails);

    return $emailRecord;
}

// 1. REGISTER
if ($action === 'register' && $method === 'POST') {
    $name = trim($input['name'] ?? '');
    $email = strtolower(trim($input['email'] ?? ''));
    $salt = $input['salt'] ?? '';
    $passwordHash = $input['passwordHash'] ?? '';
    $role = $input['role'] ?? 'Customer';

    if (!$name || !$email || !$passwordHash || !$salt) {
        http_response_code(400);
        echo json_encode(['success' => false, 'error' => 'Missing required fields']);
        exit;
    }

    $users = loadUsers($USERS_FILE);
    foreach ($users as $u) {
        if (strtolower($u['email']) === $email) {
            http_response_code(409);
            echo json_encode(['success' => false, 'error' => 'An account with this email already exists.']);
            exit;
        }
    }

    $pin = (string)rand(100000, 999999);
    $token = bin2hex(random_bytes(24));
    $expiresAt = (time() + 15 * 60) * 1000;

    $newUser = [
        'id' => 'usr_' . time() . '_' . substr(md5(rand()), 0, 5),
        'name' => $name,
        'email' => $email,
        'salt' => $salt,
        'passwordHash' => $passwordHash,
        'role' => $role,
        'permissions' => ($role === 'Staff') ? ['manage_tasks'] : [],
        'emailVerified' => false,
        'verificationPin' => $pin,
        'verificationToken' => $token,
        'verificationExpiresAt' => $expiresAt,
        'registeredAt' => date('c')
    ];

    $users[] = $newUser;
    saveUsers($USERS_FILE, $users);

    $emailRec = dispatchEmailPHP($email, $name, $pin, $token, $EMAILS_FILE, $emailConfig);

    http_response_code(201);
    echo json_encode([
        'success' => true,
        'message' => 'Account created. Verification code sent to email.',
        'email' => $email,
        'expiresAt' => $expiresAt,
        'emailId' => $emailRec['id']
    ]);
    exit;
}

// 2. VERIFY CODE
if ($action === 'verify-code' && $method === 'POST') {
    $email = strtolower(trim($input['email'] ?? ''));
    $code = trim($input['code'] ?? '');

    $users = loadUsers($USERS_FILE);
    foreach ($users as &$u) {
        if (strtolower($u['email']) === $email) {
            if (!empty($u['emailVerified'])) {
                echo json_encode(['success' => true, 'message' => 'Account already verified.', 'user' => $u]);
                exit;
            }
            if (isset($u['verificationExpiresAt']) && (time() * 1000) > $u['verificationExpiresAt']) {
                http_response_code(400);
                echo json_encode(['success' => false, 'error' => 'Code has expired. Please request a new one.']);
                exit;
            }
            if (($u['verificationPin'] ?? '') !== $code) {
                http_response_code(400);
                echo json_encode(['success' => false, 'error' => 'Incorrect verification code.']);
                exit;
            }

            $u['emailVerified'] = true;
            unset($u['verificationPin'], $u['verificationToken'], $u['verificationExpiresAt']);
            $u['verifiedAt'] = date('c');
            saveUsers($USERS_FILE, $users);

            echo json_encode([
                'success' => true,
                'message' => 'Email verified successfully!',
                'token' => bin2hex(random_bytes(32)),
                'user' => [
                    'id' => $u['id'],
                    'name' => $u['name'],
                    'email' => $u['email'],
                    'role' => $u['role'],
                    'permissions' => $u['permissions'] ?? [],
                    'emailVerified' => true
                ]
            ]);
            exit;
        }
    }
    http_response_code(404);
    echo json_encode(['success' => false, 'error' => 'Account not found.']);
    exit;
}

// 3. RESEND CODE
if ($action === 'resend-code' && $method === 'POST') {
    $email = strtolower(trim($input['email'] ?? ''));
    $users = loadUsers($USERS_FILE);
    foreach ($users as &$u) {
        if (strtolower($u['email']) === $email) {
            $newPin = (string)rand(100000, 999999);
            $newToken = bin2hex(random_bytes(24));
            $newExpiresAt = (time() + 15 * 60) * 1000;

            $u['verificationPin'] = $newPin;
            $u['verificationToken'] = $newToken;
            $u['verificationExpiresAt'] = $newExpiresAt;
            saveUsers($USERS_FILE, $users);

            $rec = dispatchEmailPHP($email, $u['name'], $newPin, $newToken, $EMAILS_FILE, $emailConfig);

            echo json_encode([
                'success' => true,
                'message' => 'New verification code sent.',
                'expiresAt' => $newExpiresAt,
                'emailId' => $rec['id']
            ]);
            exit;
        }
    }
    http_response_code(404);
    echo json_encode(['success' => false, 'error' => 'User not found.']);
    exit;
}

// 4. LOGIN
if ($action === 'login' && $method === 'POST') {
    $email = strtolower(trim($input['email'] ?? ''));
    $passwordHash = $input['passwordHash'] ?? '';

    $users = loadUsers($USERS_FILE);
    foreach ($users as $u) {
        if (strtolower($u['email']) === $email) {
            if ($u['passwordHash'] !== $passwordHash) {
                http_response_code(401);
                echo json_encode(['success' => false, 'error' => 'Invalid password.']);
                exit;
            }
            if (empty($u['emailVerified'])) {
                http_response_code(403);
                echo json_encode([
                    'success' => false,
                    'requiresVerification' => true,
                    'email' => $u['email'],
                    'message' => 'Email verification required.'
                ]);
                exit;
            }

            echo json_encode([
                'success' => true,
                'message' => 'Logged in successfully.',
                'token' => bin2hex(random_bytes(32)),
                'user' => [
                    'id' => $u['id'],
                    'name' => $u['name'],
                    'email' => $u['email'],
                    'role' => $u['role'],
                    'permissions' => $u['permissions'] ?? [],
                    'emailVerified' => true
                ]
            ]);
            exit;
        }
    }
    http_response_code(404);
    echo json_encode(['success' => false, 'error' => 'No account found with this email.']);
    exit;
}

// 5. INBOX
if ($action === 'inbox' && $method === 'GET') {
    $emails = loadEmails($EMAILS_FILE);
    echo json_encode(['success' => true, 'emails' => $emails]);
    exit;
}

// 6. ADMIN USERS
if (($action === 'admin-users' || $action === 'users') && $method === 'GET') {
    $users = loadUsers($USERS_FILE);
    $sanitized = [];
    foreach ($users as $u) {
        $sanitized[] = [
            'id' => $u['id'] ?? '',
            'name' => $u['name'] ?? '',
            'email' => $u['email'] ?? '',
            'role' => $u['role'] ?? 'Customer',
            'permissions' => $u['permissions'] ?? [],
            'emailVerified' => !empty($u['emailVerified']),
            'registeredAt' => $u['registeredAt'] ?? '',
            'verifiedAt' => $u['verifiedAt'] ?? ''
        ];
    }
    echo json_encode(['success' => true, 'users' => $sanitized]);
    exit;
}

// 7. UPDATE PERMISSIONS
if (($action === 'admin-update-permissions' || $action === 'update-permissions') && $method === 'POST') {
    $email = strtolower(trim($input['email'] ?? ''));
    $users = loadUsers($USERS_FILE);
    foreach ($users as &$u) {
        if (strtolower($u['email']) === $email) {
            if (isset($input['role'])) $u['role'] = $input['role'];
            if (isset($input['permissions']) && is_array($input['permissions'])) $u['permissions'] = $input['permissions'];
            if (isset($input['emailVerified'])) $u['emailVerified'] = (bool)$input['emailVerified'];
            saveUsers($USERS_FILE, $users);
            echo json_encode(['success' => true, 'message' => 'Permissions updated.', 'user' => $u]);
            exit;
        }
    }
    http_response_code(404);
    echo json_encode(['success' => false, 'error' => 'User not found.']);
    exit;
}

// 8. GET/POST MAIL CONFIG (FOR ADMIN)
if ($action === 'admin-mail-config') {
    if ($method === 'GET') {
        echo json_encode([
            'success' => true,
            'config' => [
                'provider'     => 'ionos',
                'from_email'   => $emailConfig['from_email'] ?? '',
                'from_name'    => $emailConfig['from_name'] ?? 'One Stop Canterbury',
                'imap_host'    => $emailConfig['imap']['host'] ?? 'imap.ionos.co.uk',
                'imap_port'    => (int)($emailConfig['imap']['port'] ?? 993),
                'smtp_host'    => $emailConfig['smtp']['host'] ?? 'smtp.ionos.co.uk',
                'smtp_port'    => (int)($emailConfig['smtp']['port'] ?? 587),
                'smtp_user'    => $emailConfig['smtp']['username'] ?? '',
                'has_password' => !empty($emailConfig['smtp']['password']) && $emailConfig['smtp']['password'] !== 'YourIonosEmailPasswordHere',
                'timeout'      => (int)($emailConfig['smtp']['timeout'] ?? 15)
            ]
        ]);
        exit;
    } elseif ($method === 'POST') {
        $smtpPort = (int)($input['smtp_port'] ?? ($emailConfig['smtp']['port'] ?? 587));
        $updated = [
            'provider'   => 'ionos',
            'from_email' => trim($input['from_email'] ?? $emailConfig['from_email']),
            'from_name'  => trim($input['from_name'] ?? $emailConfig['from_name']),
            'imap_host'  => trim($input['imap_host'] ?? ($emailConfig['imap']['host'] ?? 'imap.ionos.co.uk')),
            'imap_port'  => (int)($input['imap_port'] ?? ($emailConfig['imap']['port'] ?? 993)),
            'smtp_host'  => trim($input['smtp_host'] ?? ($emailConfig['smtp']['host'] ?? 'smtp.ionos.co.uk')),
            'smtp_port'  => $smtpPort,
            'smtp_user'  => trim($input['smtp_user'] ?? ($emailConfig['smtp']['username'] ?? '')),
            'smtp_pass'  => (!empty($input['smtp_pass']) && $input['smtp_pass'] !== '••••••••') ? $input['smtp_pass'] : ($emailConfig['smtp']['password'] ?? ''),
            'timeout'    => 15
        ];
        @file_put_contents($JSON_CONFIG, json_encode($updated, JSON_PRETTY_PRINT));

        // Also sync PHP config.php
        $phpConfigCode = "<?php\n/**\n * IONOS Email & Verification Configuration (Auto-synced from Admin Settings)\n * Official IONOS Settings:\n * - Incoming: imap.ionos.co.uk : 993 (SSL)\n * - Outgoing: smtp.ionos.co.uk : 587 (TLS, Auth required)\n */\nreturn [\n    'from_email' => '" . addslashes($updated['from_email']) . "',\n    'from_name'  => '" . addslashes($updated['from_name']) . "',\n    'mailer'     => 'smtp',\n    'imap' => [\n        'host'     => '" . addslashes($updated['imap_host']) . "',\n        'port'     => " . (int)$updated['imap_port'] . ",\n        'secure'   => 'ssl'\n    ],\n    'smtp' => [\n        'host'     => '" . addslashes($updated['smtp_host']) . "',\n        'port'     => " . $smtpPort . ",\n        'secure'   => '" . ($smtpPort === 465 ? 'ssl' : 'tls') . "',\n        'auth'     => true,\n        'username' => '" . addslashes($updated['smtp_user']) . "',\n        'password' => '" . addslashes($updated['smtp_pass']) . "',\n        'timeout'  => 15\n    ]\n];\n";
        @file_put_contents($CONFIG_FILE, $phpConfigCode);

        echo json_encode([
            'success' => true,
            'message' => 'Mail configuration saved successfully.',
            'config'  => [
                'provider'     => 'ionos',
                'from_email'   => $updated['from_email'],
                'from_name'    => $updated['from_name'],
                'imap_host'    => $updated['imap_host'],
                'imap_port'    => $updated['imap_port'],
                'smtp_host'    => $updated['smtp_host'],
                'smtp_port'    => $updated['smtp_port'],
                'smtp_user'    => $updated['smtp_user'],
                'has_password' => !empty($updated['smtp_pass'])
            ]
        ]);
        exit;
    }
}

// 9. TEST EMAIL DISPATCH
if ($action === 'admin-test-email' && $method === 'POST') {
    $targetEmail = strtolower(trim($input['toEmail'] ?? ''));
    if (!$targetEmail) {
        http_response_code(400);
        echo json_encode(['success' => false, 'error' => 'Recipient email is required.']);
        exit;
    }

    // Support clientConfig fallback if passed from client
    if ((empty($emailConfig['smtp']['username']) || empty($emailConfig['smtp']['password'])) && !empty($input['clientConfig'])) {
        $c = $input['clientConfig'];
        if (!empty($c['smtp_user']) && !empty($c['smtp_pass'])) {
            $emailConfig['smtp']['host'] = $c['smtp_host'] ?? $emailConfig['smtp']['host'];
            $emailConfig['smtp']['port'] = (int)($c['smtp_port'] ?? $emailConfig['smtp']['port']);
            $emailConfig['smtp']['username'] = $c['smtp_user'];
            $emailConfig['smtp']['password'] = $c['smtp_pass'];
            $emailConfig['from_email'] = $c['from_email'] ?? $c['smtp_user'];
            $emailConfig['from_name'] = $c['from_name'] ?? $emailConfig['from_name'];
            $emailConfig['smtp']['secure'] = ($emailConfig['smtp']['port'] === 465) ? 'ssl' : 'tls';
            @file_put_contents($JSON_CONFIG, json_encode([
                'provider' => 'ionos',
                'smtp_host' => $emailConfig['smtp']['host'],
                'smtp_port' => $emailConfig['smtp']['port'],
                'smtp_user' => $emailConfig['smtp']['username'],
                'smtp_pass' => $emailConfig['smtp']['password'],
                'from_email' => $emailConfig['from_email'],
                'from_name' => $emailConfig['from_name']
            ], JSON_PRETTY_PRINT));
        }
    }

    $testPin = (string)rand(100000, 999999);
    $testHtml = "
      <div style='font-family: Arial, sans-serif; max-width: 520px; margin: 0 auto; padding: 24px; border: 1px solid #BFDBFE; border-radius: 12px; background: #FFFFFF;'>
        <div style='text-align: center; margin-bottom: 20px;'>
          <div style='background: #002B49; color: #FFFFFF; display: inline-block; padding: 6px 14px; border-radius: 20px; font-weight: bold; font-size: 14px;'>one stop</div>
          <h2 style='color: #002B49; margin-top: 10px; font-size: 22px;'>IONOS Mail API Connection Test</h2>
        </div>
        <p style='color: #334155; font-size: 14px; line-height: 1.6;'>Hello,</p>
        <p style='color: #334155; font-size: 14px; line-height: 1.6;'>This test confirms that your <strong>IONOS SMTP connection</strong> is active and delivering emails to real inboxes:</p>
        <div style='background: #F0F7FF; border: 2px dashed #0A58CA; border-radius: 10px; padding: 18px; text-align: center; margin: 20px 0;'>
          <div style='font-size: 11px; font-weight: bold; color: #64748B; text-transform: uppercase; letter-spacing: 1px;'>TEST CODE</div>
          <div style='font-size: 32px; font-weight: 900; color: #0A58CA; letter-spacing: 6px; margin: 8px 0;'>{$testPin}</div>
          <div style='font-size: 12px; color: #64748B;'>Dispatched via " . ($emailConfig['smtp']['host'] ?? 'IONOS') . " at " . date('H:i') . "</div>
        </div>
        <p style='font-size: 12px; color: #10B981; font-weight: bold; text-align: center;'>✅ Live delivery confirmed.</p>
      </div>
    ";

    $result = sendSmtpIonos($emailConfig['smtp'], $emailConfig['from_email'], $emailConfig['from_name'], $targetEmail, "[TEST] One Stop Canterbury Mail Verification: {$testPin}", $testHtml);
    if ($result['success']) {
        echo json_encode(['success' => true, 'message' => "Test email successfully delivered to {$targetEmail}! Please check your inbox and spam folder."]);
    } else {
        http_response_code(500);
        echo json_encode(['success' => false, 'error' => "SMTP Delivery Failed: " . ($result['error'] ?? 'Unknown error')]);
    }
    exit;
}

// Default fallback
echo json_encode(['status' => 'One Stop Verification API Ready', 'time' => date('c')]);
