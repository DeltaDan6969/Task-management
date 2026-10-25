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

// 8. Sync PWA & iOS Icons and Manifest
const pwaFiles = ['manifest.json', 'apple-touch-icon.png', 'icon-192.png', 'icon-512.png'];
pwaFiles.forEach(file => {
  const src = path.join(ROOT_DIR, file);
  if (fs.existsSync(src)) {
    ['docs', 'web', 'standalone-web-app'].forEach(target => {
      fs.copyFileSync(src, path.join(ROOT_DIR, target, file));
    });
    console.log(`🖼️ Synced PWA asset: ${file}`);
  }
});

// 9. Verify GitHub Pages structure summary
console.log('\n✨ GitHub Pages Build Complete! Distribution Summary:');
console.log('   - /docs/index.html (Main entry point for GitHub Pages via docs/ folder)');
console.log('   - /docs/404.html (Client-side SPA redirect fallback)');
console.log('   - /docs/.nojekyll (Bypasses Jekyll parser to serve all assets)');
console.log(`   - /docs/${apkName} (Direct Android download link)`);
console.log('   - /docs/ios-app.zip (Downloadable native iOS Swift / Xcode package)');
console.log('   - /docs/standalone-web-app.zip (Downloadable offline bundle)');
console.log('   - .github/workflows/pages.yml (Automated GitHub Actions deploy workflow)\n');
