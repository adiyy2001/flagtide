import { createReadStream } from 'node:fs';
import { stat } from 'node:fs/promises';
import { createServer } from 'node:http';
import type { IncomingMessage, ServerResponse } from 'node:http';
import { dirname, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';
import {
  AngularNodeAppEngine,
  createNodeRequestHandler,
  isMainModule,
  writeResponseToNodeResponse,
} from '@angular/ssr/node';
import { browserConfig, shopConfigFromEnv } from './app/shop-config';
import { DEFAULT_FRAME_ANCESTORS, securityHeaders } from './server/headers';
import { cacheControlOf, contentTypeOf, resolveStaticFile } from './server/static-files';

const browserRoot = resolve(dirname(fileURLToPath(import.meta.url)), '../browser');
const engine = new AngularNodeAppEngine();
const headers = securityHeaders({
  frameAncestors: process.env['FLAGWIRE_SHOP_FRAME_ANCESTORS'] ?? DEFAULT_FRAME_ANCESTORS,
});

function applySecurityHeaders(response: ServerResponse): void {
  Object.entries(headers).forEach(([name, value]) => response.setHeader(name, value));
}

function sendJson(response: ServerResponse, status: number, body: unknown): void {
  response.statusCode = status;
  response.setHeader('Content-Type', 'application/json; charset=utf-8');
  response.setHeader('Cache-Control', 'no-store');
  response.end(JSON.stringify(body));
}

async function serveStatic(request: IncomingMessage, response: ServerResponse): Promise<boolean> {
  if (request.method !== 'GET' && request.method !== 'HEAD') {
    return false;
  }
  const pathname = new URL(request.url ?? '/', 'http://localhost').pathname;
  const file = resolveStaticFile(browserRoot, pathname);
  if (file === null) {
    return false;
  }
  const info = await stat(file).catch(() => null);
  if (info === null || !info.isFile()) {
    return false;
  }
  response.statusCode = 200;
  response.setHeader('Content-Type', contentTypeOf(file));
  response.setHeader('Content-Length', info.size);
  response.setHeader('Cache-Control', cacheControlOf(file));
  if (request.method === 'HEAD') {
    response.end();
  } else {
    createReadStream(file).pipe(response);
  }
  return true;
}

export const reqHandler = createNodeRequestHandler(
  async (request: IncomingMessage, response: ServerResponse, next: (error?: unknown) => void) => {
    try {
      applySecurityHeaders(response);
      const pathname = new URL(request.url ?? '/', 'http://localhost').pathname;
      if (pathname === '/healthz') {
        sendJson(response, 200, { status: 'ok' });
        return;
      }
      if (pathname === '/config.json') {
        sendJson(response, 200, browserConfig(shopConfigFromEnv(process.env)));
        return;
      }
      if (await serveStatic(request, response)) {
        return;
      }
      const rendered = await engine.handle(request);
      if (rendered === null) {
        response.statusCode = 404;
        response.end('Not found');
        return;
      }
      await writeResponseToNodeResponse(rendered, response);
    } catch (error) {
      next(error);
    }
  },
);

if (isMainModule(import.meta.url)) {
  const port = Number(process.env['PORT'] ?? 4000);
  const host = process.env['HOST'] ?? '127.0.0.1';
  createServer((request, response) => {
    void reqHandler(request, response, (error?: unknown) => {
      console.error(error);
      response.statusCode = 500;
      response.end('Internal server error');
    });
  }).listen(port, host, () => {
    console.log(`demo shop listening on http://${host}:${port}`);
  });
}
