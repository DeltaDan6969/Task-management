const http = require('http');
const fs = require('fs');
const path = require('path');

const PORT = 3000;
const WEB_DIR = path.join(__dirname, 'web');

const MIME_TYPES = {
  '.html': 'text/html; charset=utf-8',
  '.css': 'text/css; charset=utf-8',
  '.js': 'text/javascript; charset=utf-8',
  '.json': 'application/json; charset=utf-8',
  '.png': 'image/png',
  '.jpg': 'image/jpeg',
  '.svg': 'image/svg+xml',
  '.ico': 'image/x-icon',
  '.apk': 'application/vnd.android.package-archive',
  '.zip': 'application/zip'
};

function handleRequest(req, res) {
  let reqPath = req.url.split('?')[0];
  if (reqPath === '/' || reqPath === '') {
    reqPath = '/index.html';
  }

  // Prevent directory traversal
  const safePath = path.normalize(reqPath).replace(/^(\.\.[\/\\])+/, '');
  let filePath = path.join(WEB_DIR, safePath);

  // If requesting root-level APK, check web dir and root dir
  if (!fs.existsSync(filePath)) {
    const rootCandidate = path.join(__dirname, safePath);
    if (fs.existsSync(rootCandidate) && !fs.statSync(rootCandidate).isDirectory()) {
      filePath = rootCandidate;
    }
  }

  fs.stat(filePath, (err, stats) => {
    if (err || stats.isDirectory()) {
      // Fallback to index.html for SPA routes
      const indexPath = path.join(WEB_DIR, 'index.html');
      fs.readFile(indexPath, (err2, indexData) => {
        if (err2) {
          res.writeHead(404, { 'Content-Type': 'text/plain' });
          res.end('404 Not Found');
        } else {
          res.writeHead(200, {
            'Content-Type': 'text/html; charset=utf-8',
            'Content-Length': Buffer.byteLength(indexData)
          });
          res.end(indexData);
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

    if (ext === '.apk') {
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
  console.log(`Web server listening on port ${PORT}`);
});

process.on('uncaughtException', (err) => {
  console.error('Server uncaughtException:', err);
});

process.on('unhandledRejection', (reason) => {
  console.error('Server unhandledRejection:', reason);
});


