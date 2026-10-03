import { describe, expect, it } from 'vitest';
import { DEFAULT_FRAME_ANCESTORS, securityHeaders } from './headers';

describe('securityHeaders', () => {
  it('forbids sniffing and referrers and limits framing to the given ancestors', () => {
    expect(securityHeaders({ frameAncestors: "'none'" })).toEqual({
      'X-Content-Type-Options': 'nosniff',
      'Referrer-Policy': 'no-referrer',
      'Content-Security-Policy': "frame-ancestors 'none'",
    });
  });

  it('allows the local e2e harness by default', () => {
    expect(DEFAULT_FRAME_ANCESTORS).toContain('http://127.0.0.1:14400');
  });
});
