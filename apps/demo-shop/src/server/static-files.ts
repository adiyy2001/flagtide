import { extname, join, normalize, sep } from 'node:path';

const CONTENT_TYPES: Readonly<Record<string, string>> = {
  '.js': 'text/javascript; charset=utf-8',
  '.mjs': 'text/javascript; charset=utf-8',
  '.css': 'text/css; charset=utf-8',
  '.json': 'application/json; charset=utf-8',
  '.svg': 'image/svg+xml',
  '.png': 'image/png',
  '.ico': 'image/x-icon',
  '.woff2': 'font/woff2',
  '.txt': 'text/plain; charset=utf-8',
  '.map': 'application/json; charset=utf-8',
};

const HASHED_ASSET = /-[A-Z0-9]{8}\.(?:js|css|woff2)$/u;

export function contentTypeOf(path: string): string {
  return CONTENT_TYPES[extname(path).toLowerCase()] ?? 'application/octet-stream';
}

export function cacheControlOf(path: string): string {
  return HASHED_ASSET.test(path) ? 'public, max-age=31536000, immutable' : 'no-cache';
}

export function resolveStaticFile(root: string, urlPath: string): string | null {
  let decoded: string;
  try {
    decoded = decodeURIComponent(urlPath);
  } catch {
    return null;
  }
  if (decoded.includes('\0') || decoded === '/' || decoded.endsWith('/')) {
    return null;
  }
  const candidate = normalize(join(root, decoded));
  return candidate.startsWith(root + sep) ? candidate : null;
}
