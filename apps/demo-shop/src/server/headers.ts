export interface SecurityHeaderOptions {
  readonly frameAncestors: string;
}

export const DEFAULT_FRAME_ANCESTORS = "'self' http://127.0.0.1:14400 http://localhost:14400";

export function securityHeaders(options: SecurityHeaderOptions): Readonly<Record<string, string>> {
  return {
    'X-Content-Type-Options': 'nosniff',
    'Referrer-Policy': 'no-referrer',
    'Content-Security-Policy': `frame-ancestors ${options.frameAncestors}`,
  };
}
