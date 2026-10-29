/**
 * GitHub Pages & Web Distribution Build Script
 * Builds, synchronizes, and validates all GitHub Pages and web distribution artifacts:
 * - /docs (GitHub Pages from /docs branch deployment)
 * - /web (GitHub Actions workflow deployment)
 * - / (Root deployment fallback)
 * - Android APK v1.5 direct download link
 * - Standalone offline web application package (.zip)
 */

const fs = require('fs');
const path = require('path');
const { execSync } = require('child_process');

console.log('🚀 Starting GitHub Pages & Web Distribution Build...');

const ROOT_DIR = __dirname;
const WEB_HTML = path.join(ROOT_DIR, 'web', 'index.html');
const APK_SRC = path.join(ROOT_DIR, 'app', 'build', 'outputs', 'apk', 'debug', 'app-debug.apk');

// 1. Ensure target directories exist
const dirs = [
  path.join(ROOT_DIR, 'docs'),
  path.join(ROOT_DIR, 'web'),
  path.join(ROOT_DIR, 'standalone-web-app'),
  path.join(ROOT_DIR, 'standalone-web-app', 'public'),
];

dirs.forEach(dir => {
  if (!fs.existsSync(dir)) {
    fs.mkdirSync(dir, { recursive: true });
    console.log(`📁 Created directory: ${path.relative(ROOT_DIR, dir)}`);
  }
});

// 2. Read source HTML
if (!fs.existsSync(WEB_HTML)) {
  console.error(`❌ Source file missing: ${WEB_HTML}`);
  process.exit(1);
}
const htmlContent = fs.readFileSync(WEB_HTML, 'utf8');

// 3. Mirror HTML and 404 for SPA routing
const htmlDestinations = [
  path.join(ROOT_DIR, 'index.html'),
  path.join(ROOT_DIR, '404.html'),
  path.join(ROOT_DIR, 'web', '404.html'),
  path.join(ROOT_DIR, 'docs', 'index.html'),
  path.join(ROOT_DIR, 'docs', '404.html'),
  path.join(ROOT_DIR, 'standalone-web-app', 'index.html'),
  path.join(ROOT_DIR, 'standalone-web-app', 'public', 'index.html'),
];

htmlDestinations.forEach(dest => {
  fs.writeFileSync(dest, htmlContent, 'utf8');
  console.log(`✅ Synced HTML: ${path.relative(ROOT_DIR, dest)} (${htmlContent.length} bytes)`);
});

// 3b. Mirror tasks.html (Shift Task Manager web app)
const TASKS_HTML = path.join(ROOT_DIR, 'web', 'tasks.html');
if (fs.existsSync(TASKS_HTML)) {
  const tasksContent = fs.readFileSync(TASKS_HTML, 'utf8');
  const tasksDestinations = [
    path.join(ROOT_DIR, 'tasks.html'),
    path.join(ROOT_DIR, 'docs', 'tasks.html'),
    path.join(ROOT_DIR, 'standalone-web-app', 'tasks.html'),
    path.join(ROOT_DIR, 'standalone-web-app', 'public', 'tasks.html'),
  ];
  tasksDestinations.forEach(dest => {
    fs.writeFileSync(dest, tasksContent, 'utf8');
    console.log(`✅ Synced Task App: ${path.relative(ROOT_DIR, dest)} (${tasksContent.length} bytes)`);
  });
}

// 3c. Mirror website.html (Store Website & Portal)
const WEBSITE_HTML = path.join(ROOT_DIR, 'web', 'website.html');
if (fs.existsSync(WEBSITE_HTML)) {
  const websiteContent = fs.readFileSync(WEBSITE_HTML, 'utf8');
  const websiteDestinations = [
    path.join(ROOT_DIR, 'website.html'),
    path.join(ROOT_DIR, 'docs', 'website.html'),
    path.join(ROOT_DIR, 'standalone-web-app', 'website.html'),
    path.join(ROOT_DIR, 'standalone-web-app', 'public', 'website.html'),
  ];
  websiteDestinations.forEach(dest => {
    fs.writeFileSync(dest, websiteContent, 'utf8');
    console.log(`✅ Synced Store Website: ${path.relative(ROOT_DIR, dest)} (${websiteContent.length} bytes)`);
  });
}

// 3c. Mirror assets directory
const ASSETS_SRC = path.join(ROOT_DIR, 'web', 'assets');
if (fs.existsSync(ASSETS_SRC)) {
  const assetTargets = [
    path.join(ROOT_DIR, 'assets'),
    path.join(ROOT_DIR, 'docs', 'assets'),
    path.join(ROOT_DIR, 'standalone-web-app', 'assets'),
    path.join(ROOT_DIR, 'standalone-web-app', 'public', 'assets'),
  ];
  assetTargets.forEach(target => {
    if (!fs.existsSync(target)) fs.mkdirSync(target, { recursive: true });
    fs.cpSync(ASSETS_SRC, target, { recursive: true });
    console.log(`🖼️ Synced assets directory: ${path.relative(ROOT_DIR, target)}`);
  });
}

// 3d. Mirror API directory & server files (Node & PHP backends)
const API_SRC = path.join(ROOT_DIR, 'web', 'api');
if (fs.existsSync(API_SRC)) {
  const apiTargets = [
    path.join(ROOT_DIR, 'docs', 'api'),
    path.join(ROOT_DIR, 'standalone-web-app', 'api'),
  ];
  apiTargets.forEach(target => {
    if (!fs.existsSync(target)) fs.mkdirSync(target, { recursive: true });
    fs.cpSync(API_SRC, target, { recursive: true });
    console.log(`⚡ Synced API backend: ${path.relative(ROOT_DIR, target)}`);
  });
}

// Sync server.js and data folder into standalone-web-app
fs.copyFileSync(path.join(ROOT_DIR, 'server.js'), path.join(ROOT_DIR, 'standalone-web-app', 'server.js'));
if (fs.existsSync(path.join(ROOT_DIR, 'data'))) {
  fs.cpSync(path.join(ROOT_DIR, 'data'), path.join(ROOT_DIR, 'standalone-web-app', 'data'), { recursive: true });
}

// 4. Ensure .nojekyll in all deployment directories
const nojekyllDestinations = [
  path.join(ROOT_DIR, '.nojekyll'),
  path.join(ROOT_DIR, 'web', '.nojekyll'),
  path.join(ROOT_DIR, 'docs', '.nojekyll'),
  path.join(ROOT_DIR, 'standalone-web-app', '.nojekyll'),
];

nojekyllDestinations.forEach(dest => {
  fs.writeFileSync(dest, '', 'utf8');
  console.log(`✅ Verified .nojekyll: ${path.relative(ROOT_DIR, dest)}`);
});

// 4b. Ensure .htaccess for IONOS Apache Webhosting in all web directories
const htaccessDestinations = [
  path.join(ROOT_DIR, 'web', '.htaccess'),
  path.join(ROOT_DIR, 'docs', '.htaccess'),
  path.join(ROOT_DIR, 'standalone-web-app', '.htaccess'),
];
if (fs.existsSync(path.join(ROOT_DIR, '.htaccess'))) {
  const htaccessContent = fs.readFileSync(path.join(ROOT_DIR, '.htaccess'), 'utf8');
  htaccessDestinations.forEach(dest => {
    fs.writeFileSync(dest, htaccessContent, 'utf8');
    console.log(`✅ Synced .htaccess: ${path.relative(ROOT_DIR, dest)}`);
  });
}

// 5. Update Android APK binaries
const apkName = 'ShiftTask-Tracker-v1.5.apk';
if (fs.existsSync(APK_SRC)) {
  const apkDestinations = [
    path.join(ROOT_DIR, apkName),
    path.join(ROOT_DIR, 'docs', apkName),
    path.join(ROOT_DIR, 'web', apkName),
  ];

  apkDestinations.forEach(dest => {
    fs.copyFileSync(APK_SRC, dest);
    const stats = fs.statSync(dest);
    console.log(`📱 Synced APK: ${path.relative(ROOT_DIR, dest)} (${(stats.size / (1024 * 1024)).toFixed(2)} MB)`);
  });
} else {
  console.warn(`⚠️ Warning: ${APK_SRC} not found. Existing APKs will remain intact.`);
}

// 5b. Sync PWA and Icon Assets
const pwaFiles = [
  'sw.js',
  'manifest.json',
  'apple-touch-icon.png',
  'apple-touch-icon-180x180.png',
  'apple-touch-icon-167x167.png',
  'apple-touch-icon-152x152.png',
  'apple-touch-icon-120x120.png',
  'apple-touch-icon-precomposed.png',
  'icon-192.png',
  'icon-512.png',
  'favicon.png',
  'favicon.ico'
];
pwaFiles.forEach(file => {
  const src = path.join(ROOT_DIR, file);
  if (fs.existsSync(src)) {
    ['docs', 'web', 'standalone-web-app'].forEach(target => {
      fs.copyFileSync(src, path.join(ROOT_DIR, target, file));
    });
    console.log(`🖼️ Synced PWA asset: ${file}`);
  }
});

// 6. Package standalone-web-app.zip
try {
  console.log('📦 Packaging standalone-web-app.zip...');
  execSync('zip -q -r standalone-web-app.zip standalone-web-app', { cwd: ROOT_DIR });
  
  // Sync zip to docs and web
  fs.copyFileSync(path.join(ROOT_DIR, 'standalone-web-app.zip'), path.join(ROOT_DIR, 'docs', 'standalone-web-app.zip'));
  fs.copyFileSync(path.join(ROOT_DIR, 'standalone-web-app.zip'), path.join(ROOT_DIR, 'web', 'standalone-web-app.zip'));
  
  const zipStats = fs.statSync(path.join(ROOT_DIR, 'standalone-web-app.zip'));
  console.log(`📦 Synced standalone-web-app.zip (${(zipStats.size / 1024).toFixed(1)} KB) to root, docs/, and web/`);
} catch (err) {
  console.error(`⚠️ Error packaging zip:`, err.message);
}

// 6b. Package ionos-onestop-website.zip (Root-level extraction for IONOS File Manager / Webspace)
try {
  console.log('🌐 Assembling and packaging ionos-onestop-website.zip...');
  const ionosDir = path.join(ROOT_DIR, 'ionos-package');
  if (fs.existsSync(ionosDir)) {
    fs.rmSync(ionosDir, { recursive: true, force: true });
  }
  fs.mkdirSync(ionosDir, { recursive: true });

  // Copy site files into flat root structure for IONOS
  const ionosFiles = [
    'index.html',
    'tasks.html',
    'website.html',
    '404.html',
    '.htaccess',
    '.nojekyll',
    'IONOS_DEPLOYMENT_GUIDE.md',
    'IONOS_EMAIL_SETUP_GUIDE.md',
    ...pwaFiles
  ];

  ionosFiles.forEach(f => {
    const src = path.join(ROOT_DIR, f);
    if (fs.existsSync(src)) {
      fs.copyFileSync(src, path.join(ionosDir, f));
    }
  });

  // Copy assets folder
  if (fs.existsSync(path.join(ROOT_DIR, 'assets'))) {
    fs.cpSync(path.join(ROOT_DIR, 'assets'), path.join(ionosDir, 'assets'), { recursive: true });
  }

  // Copy API folder (PHP & backend scripts for IONOS Apache)
  if (fs.existsSync(path.join(ROOT_DIR, 'web', 'api'))) {
    fs.cpSync(path.join(ROOT_DIR, 'web', 'api'), path.join(ionosDir, 'api'), { recursive: true });
  }
  fs.copyFileSync(path.join(ROOT_DIR, 'server.js'), path.join(ionosDir, 'server.js'));
  if (fs.existsSync(path.join(ROOT_DIR, 'data'))) {
    fs.cpSync(path.join(ROOT_DIR, 'data'), path.join(ionosDir, 'data'), { recursive: true });
  }

  // Build the zip with files at the root of the archive
  execSync('zip -q -r ../ionos-onestop-website.zip .', { cwd: ionosDir });

  // Mirror zip to docs/ and web/ for download links
  fs.copyFileSync(path.join(ROOT_DIR, 'ionos-onestop-website.zip'), path.join(ROOT_DIR, 'docs', 'ionos-onestop-website.zip'));
  fs.copyFileSync(path.join(ROOT_DIR, 'ionos-onestop-website.zip'), path.join(ROOT_DIR, 'web', 'ionos-onestop-website.zip'));

  const ionosStats = fs.statSync(path.join(ROOT_DIR, 'ionos-onestop-website.zip'));
  console.log(`🌐 Compiled ionos-onestop-website.zip (${(ionosStats.size / 1024).toFixed(1)} KB)`);
} catch (err) {
  console.error(`⚠️ Error packaging ionos zip:`, err.message);
}

// 7. Package and sync ios-app.zip
try {
  if (fs.existsSync(path.join(ROOT_DIR, 'ios'))) {
    console.log('🍏 Packaging ios-app.zip...');
    execSync('zip -q -r ios-app.zip ios', { cwd: ROOT_DIR });
    fs.copyFileSync(path.join(ROOT_DIR, 'ios-app.zip'), path.join(ROOT_DIR, 'docs', 'ios-app.zip'));
    fs.copyFileSync(path.join(ROOT_DIR, 'ios-app.zip'), path.join(ROOT_DIR, 'web', 'ios-app.zip'));
    const iosStats = fs.statSync(path.join(ROOT_DIR, 'ios-app.zip'));
    console.log(`🍏 Synced ios-app.zip (${(iosStats.size / 1024).toFixed(1)} KB) to root, docs/, and web/`);
  }
} catch (err) {
  console.error(`⚠️ Error packaging iOS zip:`, err.message);
}

// 9. Summary
console.log('\n✨ Build Complete! Distribution Summary:');
console.log('   - /ionos-onestop-website.zip (Direct upload for IONOS Webspace Explorer & FTP)');
console.log('   - /standalone-web-app.zip (Complete offline web package)');
console.log('   - /IONOS_DEPLOYMENT_GUIDE.md (Step-by-step instructions for IONOS)');
console.log('   - /docs/index.html (Main entry point for GitHub Pages via docs/ folder)');
console.log('   - /docs/tasks.html (ShiftTask Tracker app)');
console.log(`   - /docs/${apkName} (Direct Android download link)`);
console.log('   - /docs/ios-app.zip (Downloadable native iOS Swift / Xcode package)\n');
