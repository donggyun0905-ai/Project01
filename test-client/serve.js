// 외부 패키지 없이 Node 내장 모듈만으로 만든 최소 정적 파일 서버.
// CorsFilter.java의 ALLOWED_ORIGIN이 "http://localhost:5500"으로 고정돼 있어서,
// 이 테스트 페이지도 반드시 5500번 포트로 열어야 CORS가 통과한다.
const http = require('http');
const fs = require('fs');
const path = require('path');

const PORT = 5500;
const ROOT = __dirname;

const MIME = {
  '.html': 'text/html; charset=utf-8',
  '.js': 'text/javascript; charset=utf-8',
  '.css': 'text/css; charset=utf-8',
};

http.createServer((req, res) => {
  let filePath = req.url === '/' ? '/index.html' : req.url.split('?')[0];
  filePath = path.join(ROOT, decodeURIComponent(filePath));

  fs.readFile(filePath, (err, data) => {
    if (err) {
      res.writeHead(404, { 'Content-Type': 'text/plain; charset=utf-8' });
      res.end('404 Not Found: ' + req.url);
      return;
    }
    const ext = path.extname(filePath);
    res.writeHead(200, { 'Content-Type': MIME[ext] || 'application/octet-stream' });
    res.end(data);
  });
}).listen(PORT, () => {
  console.log('테스트 페이지: http://localhost:' + PORT);
});
