import { describe, expect, it } from 'vitest';
import { cacheControlOf, contentTypeOf, resolveStaticFile } from './static-files';

describe('resolveStaticFile', () => {
  const root = '/srv/browser';

  it('resolves a file inside the root', () => {
    expect(resolveStaticFile(root, '/main-ABCD1234.js')).toBe('/srv/browser/main-ABCD1234.js');
  });

  it.each(['/../secret', '/..%2Fsecret', '/a/../../secret', '/%2e%2e/secret'])('refuses %s', (path) => {
    expect(resolveStaticFile(root, path)).toBeNull();
  });

  it('refuses directories, the bare root, null bytes and broken escapes', () => {
    expect(resolveStaticFile(root, '/')).toBeNull();
    expect(resolveStaticFile(root, '/assets/')).toBeNull();
    expect(resolveStaticFile(root, '/a%00b')).toBeNull();
    expect(resolveStaticFile(root, '/%E0%A4%A')).toBeNull();
  });
});

describe('contentTypeOf', () => {
  it('knows the types the build produces and falls back to octet stream', () => {
    expect(contentTypeOf('x.js')).toBe('text/javascript; charset=utf-8');
    expect(contentTypeOf('x.CSS')).toBe('text/css; charset=utf-8');
    expect(contentTypeOf('favicon.svg')).toBe('image/svg+xml');
    expect(contentTypeOf('x.bin')).toBe('application/octet-stream');
  });
});

describe('cacheControlOf', () => {
  it('caches hashed bundles for a year and everything else not at all', () => {
    expect(cacheControlOf('/b/main-ABCD1234.js')).toBe('public, max-age=31536000, immutable');
    expect(cacheControlOf('/b/styles-URXWXOI5.css')).toBe('public, max-age=31536000, immutable');
    expect(cacheControlOf('/b/config.json')).toBe('no-cache');
    expect(cacheControlOf('/b/favicon.svg')).toBe('no-cache');
  });
});
