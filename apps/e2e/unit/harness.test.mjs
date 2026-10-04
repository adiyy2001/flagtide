import assert from 'node:assert/strict';
import { after, before, describe, it } from 'node:test';
import { harnessPage, startHarness } from '../harness/server.mjs';

describe('harness page', () => {
  it('embeds the admin flag list and the shop in two iframes', () => {
    const html = harnessPage({ adminUrl: 'http://admin.test', shopUrl: 'http://shop.test' });
    assert.match(html, /<iframe id="admin"[^>]*src="http:\/\/admin\.test\/flags"/u);
    assert.match(html, /<iframe id="shop"[^>]*src="http:\/\/shop\.test\/"/u);
  });

  it('titles both frames for assistive technology', () => {
    const html = harnessPage({ adminUrl: 'http://a', shopUrl: 'http://s' });
    assert.match(html, /title="flagtide admin"/u);
    assert.match(html, /title="demo shop"/u);
  });
});

describe('harness server', () => {
  let server;
  let base;

  before(async () => {
    server = await startHarness({ port: 0 });
    base = `http://127.0.0.1:${server.address().port}`;
  });

  after(() => new Promise((done) => server.close(done)));

  it('serves the page without caching', async () => {
    const response = await fetch(`${base}/`);
    assert.equal(response.status, 200);
    assert.equal(response.headers.get('cache-control'), 'no-store');
    assert.match(response.headers.get('content-type'), /text\/html/u);
  });

  it('answers other paths with 404', async () => {
    const response = await fetch(`${base}/other`);
    assert.equal(response.status, 404);
  });

  it('listens on the loopback interface only', () => {
    assert.equal(server.address().address, '127.0.0.1');
  });
});
