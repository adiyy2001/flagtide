import { afterEach, describe, expect, it, vi } from 'vitest';
import { createBrowserEnvironment, createBrowserSocketFactory } from './connection-manager.js';

afterEach(() => {
  vi.unstubAllGlobals();
});

class StubSocket {
  static latest: StubSocket | undefined;
  onopen: (() => void) | null = null;
  onmessage: ((event: { data: unknown }) => void) | null = null;
  onclose: ((event: { code: number }) => void) | null = null;
  onerror: (() => void) | null = null;
  readonly sent: string[] = [];
  closedWith: number | undefined;

  constructor(readonly url: string) {
    StubSocket.latest = this;
  }

  send(data: string): void {
    this.sent.push(data);
  }

  close(code?: number): void {
    this.closedWith = code;
  }
}

describe('createBrowserSocketFactory', () => {
  it('translates socket events and ignores binary messages', () => {
    const factory = createBrowserSocketFactory(StubSocket);
    const events: string[] = [];
    const control = factory('ws://x', {
      open: () => events.push('open'),
      message: (text) => events.push(`message:${text}`),
      close: (code) => events.push(`close:${code}`),
    });
    const created = StubSocket.latest;
    created?.onopen?.();
    created?.onmessage?.({ data: 'hello' });
    created?.onmessage?.({ data: new Uint8Array(1) });
    created?.onerror?.();
    created?.onclose?.({ code: 4401 });
    control.send('x');
    control.close(1000);
    expect(events).toEqual(['open', 'message:hello', 'close:4401']);
    expect(created?.sent).toEqual(['x']);
    expect(created?.closedWith).toBe(1000);
  });

  it('throws when the runtime has no WebSocket', () => {
    vi.stubGlobal('WebSocket', undefined);
    expect(() =>
      createBrowserSocketFactory()('ws://x', { open: vi.fn(), message: vi.fn(), close: vi.fn() }),
    ).toThrow('WebSocket');
  });

  it('uses the global WebSocket by default', () => {
    vi.stubGlobal('WebSocket', StubSocket);
    expect(() =>
      createBrowserSocketFactory()('ws://x', { open: vi.fn(), message: vi.fn(), close: vi.fn() }),
    ).not.toThrow();
  });
});

describe('createBrowserEnvironment', () => {
  it('reports always online without window and document', () => {
    const environment = createBrowserEnvironment();
    expect(environment.isOnline()).toBe(true);
  });

  it('follows online, offline and visibility events', () => {
    const windowTarget = new EventTarget();
    const documentTarget = Object.assign(new EventTarget(), { visibilityState: 'visible' });
    vi.stubGlobal('window', windowTarget);
    vi.stubGlobal('document', documentTarget);
    vi.stubGlobal('navigator', { onLine: false });
    const environment = createBrowserEnvironment();
    expect(environment.isOnline()).toBe(false);
    const onlineSeen: boolean[] = [];
    const visibleSeen: boolean[] = [];
    const online = environment.online$.subscribe((value) => onlineSeen.push(value));
    const visible = environment.visible$.subscribe((value) => visibleSeen.push(value));
    windowTarget.dispatchEvent(new Event('online'));
    windowTarget.dispatchEvent(new Event('offline'));
    documentTarget.visibilityState = 'hidden';
    documentTarget.dispatchEvent(new Event('visibilitychange'));
    documentTarget.visibilityState = 'visible';
    documentTarget.dispatchEvent(new Event('visibilitychange'));
    online.unsubscribe();
    visible.unsubscribe();
    windowTarget.dispatchEvent(new Event('online'));
    expect(onlineSeen).toEqual([true, false]);
    expect(visibleSeen).toEqual([false, true]);
  });
});
