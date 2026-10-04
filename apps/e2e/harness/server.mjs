import { createServer } from 'node:http';
import { fileURLToPath } from 'node:url';

export const HARNESS_PORT = 14400;

export function harnessPage({ adminUrl, shopUrl }) {
  return `<!doctype html>
<html lang="en">
  <head>
    <meta charset="utf-8" />
    <title>flagtide: admin and shop side by side</title>
    <meta name="viewport" content="width=device-width, initial-scale=1" />
    <style>
      :root { color-scheme: light dark; }
      * { box-sizing: border-box; }
      body { margin: 0; height: 100vh; display: grid; grid-template-columns: 1fr 1fr; gap: 8px; padding: 8px; background: #1b1b1f; font: 14px system-ui, sans-serif; }
      section { display: flex; flex-direction: column; min-width: 0; border-radius: 8px; overflow: hidden; background: #fff; }
      h1 { margin: 0; padding: 6px 12px; font-size: 13px; font-weight: 600; background: #2b2b31; color: #f1f1f4; }
      iframe { flex: 1; width: 100%; border: 0; background: #fff; }
    </style>
  </head>
  <body>
    <section><h1>Admin</h1><iframe id="admin" title="flagtide admin" src="${adminUrl}/flags"></iframe></section>
    <section><h1>Demo shop</h1><iframe id="shop" title="demo shop" src="${shopUrl}/"></iframe></section>
  </body>
</html>
`;
}

export function startHarness({
  port = HARNESS_PORT,
  adminUrl = 'http://127.0.0.1:14200',
  shopUrl = 'http://127.0.0.1:14300',
} = {}) {
  const server = createServer((request, response) => {
    const path = new URL(request.url ?? '/', 'http://localhost').pathname;
    if (path !== '/') {
      response.statusCode = 404;
      response.end('Not found');
      return;
    }
    response.setHeader('Content-Type', 'text/html; charset=utf-8');
    response.setHeader('Cache-Control', 'no-store');
    response.end(harnessPage({ adminUrl, shopUrl }));
  });
  return new Promise((resolve, reject) => {
    server.once('error', reject);
    server.listen(port, '127.0.0.1', () => resolve(server));
  });
}

if (process.argv[1] === fileURLToPath(import.meta.url)) {
  const server = await startHarness();
  console.log(`harness on http://127.0.0.1:${server.address().port}/`);
}
