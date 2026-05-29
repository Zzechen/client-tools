const http = require('http');
const fs = require('fs');
const path = require('path');
const os = require('os');

const port = parseInt(process.argv[2] || '3000', 10);
const publicDir = path.join(__dirname, 'public');

const mimeTypes = {
  '.html': 'text/html; charset=utf-8',
  '.css': 'text/css',
  '.js': 'application/javascript',
  '.json': 'application/json',
};

const server = http.createServer((req, res) => {
  const urlPath = req.url.split('?')[0];
  const query = req.url.includes('?') ? req.url.slice(req.url.indexOf('?') + 1) : '';
  const filePath = path.join(publicDir, urlPath === '/' ? 'index.html' : urlPath);
  const ext = path.extname(filePath);
  const contentType = mimeTypes[ext] || 'text/plain';

  fs.readFile(filePath, (err, data) => {
    if (err) {
      res.writeHead(404, { 'Content-Type': 'text/plain' });
      res.end('Not found');
      return;
    }
    // Inject query params as text for test pages
    const body = data.toString().replace('{{QUERY}}', query || '(none)');
    res.writeHead(200, { 'Content-Type': contentType });
    res.end(body);
  });
});

server.listen(port, '0.0.0.0', () => {
  const interfaces = os.networkInterfaces();
  const lanIp = Object.values(interfaces)
    .flat()
    .find(i => i && i.family === 'IPv4' && !i.internal)?.address ?? 'localhost';
  console.log('\nLocal server running:');
  console.log(`  Local:   http://localhost:${port}`);
  console.log(`  LAN:     http://${lanIp}:${port}  <- use this in targetUrl`);
  console.log('\nCtrl+C to stop\n');
});
