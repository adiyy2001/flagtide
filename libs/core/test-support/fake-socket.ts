import { Subject } from 'rxjs';
import type { SchedulerAction, SchedulerLike, Subscription, Observable } from 'rxjs';
import type { NetworkEnvironment, SocketFactory, SocketHandlers } from '../src/lib/connection-manager.js';
import type { ServerFrame } from '../src/lib/protocol.js';

export class FakeSocket {
  readonly sent: Record<string, unknown>[] = [];
  closedByClient: number | null = null;
  readonly openedAt: number;

  constructor(
    private readonly handlers: SocketHandlers,
    openedAt: number,
  ) {
    this.openedAt = openedAt;
  }

  send(text: string): void {
    this.sent.push(JSON.parse(text) as Record<string, unknown>);
  }

  close(code?: number): void {
    this.closedByClient = code ?? 1005;
  }

  open(): void {
    this.handlers.open();
  }

  receive(frame: ServerFrame): void {
    this.handlers.message(JSON.stringify(frame));
  }

  receiveText(text: string): void {
    this.handlers.message(text);
  }

  closeFromServer(code: number): void {
    this.handlers.close(code);
  }
}

export class FakeSockets {
  readonly sockets: FakeSocket[] = [];
  readonly factory: SocketFactory;

  constructor(now: () => number = () => 0) {
    this.factory = (_url, handlers) => {
      const socket = new FakeSocket(handlers, now());
      this.sockets.push(socket);
      return socket;
    };
  }

  get latest(): FakeSocket {
    const socket = this.sockets.at(-1);
    if (socket === undefined) {
      throw new Error('no socket has been opened');
    }
    return socket;
  }

  get openTimes(): number[] {
    return this.sockets.map((socket) => socket.openedAt);
  }
}

export class FakeEnvironment implements NetworkEnvironment {
  online = true;
  private readonly onlineSubject = new Subject<boolean>();
  private readonly visibleSubject = new Subject<boolean>();

  isOnline(): boolean {
    return this.online;
  }

  get online$(): Observable<boolean> {
    return this.onlineSubject.asObservable();
  }

  get visible$(): Observable<boolean> {
    return this.visibleSubject.asObservable();
  }

  goOffline(): void {
    this.online = false;
    this.onlineSubject.next(false);
  }

  goOnline(): void {
    this.online = true;
    this.onlineSubject.next(true);
  }

  show(): void {
    this.visibleSubject.next(true);
  }
}

class ThrottledScheduler implements SchedulerLike {
  constructor(
    private readonly base: SchedulerLike,
    private readonly factor: number,
  ) {}

  now(): number {
    return this.base.now();
  }

  schedule<T>(work: (this: SchedulerAction<T>, state?: T) => void, delay = 0, state?: T): Subscription {
    return this.base.schedule(work, delay * this.factor, state);
  }
}

export function throttled(base: SchedulerLike, factor: number): SchedulerLike {
  return new ThrottledScheduler(base, factor);
}

export function advance(
  scheduler: { now(): number; maxFrames: number; schedule: SchedulerLike['schedule']; flush(): void },
  ms: number,
): void {
  const target = scheduler.now() + ms;
  scheduler.maxFrames = target;
  scheduler.schedule(() => undefined, ms);
  scheduler.flush();
  scheduler.maxFrames = Number.POSITIVE_INFINITY;
}
