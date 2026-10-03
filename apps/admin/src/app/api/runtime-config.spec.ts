import { describe, expect, it } from 'vitest';
import { loadRuntimeConfig, parseRuntimeConfig } from './runtime-config';

describe('parseRuntimeConfig', () => {
  it('reads the api url, project and admin keys and trims trailing slashes', () => {
    const config = parseRuntimeConfig({
      apiUrl: 'http://localhost:8080//',
      project: 'demo',
      adminKeys: { dev: 'k1', prod: '', bad: 7 },
    });
    expect(config).toEqual({ apiUrl: 'http://localhost:8080', project: 'demo', adminKeys: { dev: 'k1' } });
  });

  it('accepts a missing adminKeys section', () => {
    expect(parseRuntimeConfig({ apiUrl: 'http://x', project: 'p' }).adminKeys).toEqual({});
  });

  it.each([
    [null, 'object'],
    [[], 'object'],
    [{ project: 'p' }, 'apiUrl'],
    [{ apiUrl: 'http://x' }, 'project'],
    [{ apiUrl: '', project: 'p' }, 'apiUrl'],
  ])('rejects %j', (raw, mention) => {
    expect(() => parseRuntimeConfig(raw)).toThrow(mention);
  });
});

describe('loadRuntimeConfig', () => {
  it('loads and parses the file', async () => {
    const fetcher: typeof fetch = async () =>
      new Response(JSON.stringify({ apiUrl: 'http://x', project: 'p', adminKeys: {} }));
    expect((await loadRuntimeConfig('config.json', fetcher)).project).toBe('p');
  });

  it('fails with the status when the file is missing', async () => {
    const fetcher: typeof fetch = async () => new Response('nope', { status: 404 });
    await expect(loadRuntimeConfig('config.json', fetcher)).rejects.toThrow('404');
  });
});
