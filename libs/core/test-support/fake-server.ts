import type { AddressInfo } from 'node:net';
import { WebSocketServer } from 'ws';
import type { WebSocket } from 'ws';
import type { DeltasFrame, ServerFrame, StreamChange, StreamEntry } from '../src/lib/protocol.js';
import type { FlagConfig, Segment } from '../src/lib/types.js';

export class FakeStreamServer {
  readonly received: Record<string, unknown>[] = [];
  readonly connections: WebSocket[] = [];
  private readonly server: WebSocketServer;
  private flags: FlagConfig[];
  private segments: Segment[];
  private history: StreamEntry[] = [];
  private version: number;
  private retainedFrom = 0;
  private acceptedKey = 'fws_test';
  private heartbeatMs: number | null = null;
  private heartbeatTimer: NodeJS.Timeout | null = null;

  private constructor(server: WebSocketServer, flags: FlagConfig[], segments: Segment[], version: number) {
    this.server = server;
    this.flags = flags;
    this.segments = segments;
    this.version = version;
    this.server.on('connection', (socket) => this.accept(socket));
  }

  static async start(
    flags: FlagConfig[] = [],
    segments: Segment[] = [],
    version = 1,
  ): Promise<FakeStreamServer> {
    const server = new WebSocketServer({ host: '127.0.0.1', port: 0 });
    await new Promise<void>((resolve) => server.on('listening', resolve));
    return new FakeStreamServer(server, flags, segments, version);
  }

  get url(): string {
    const address = this.server.address() as AddressInfo;
    return `ws://127.0.0.1:${address.port}/sdk/v1/stream`;
  }

  get currentVersion(): number {
    return this.version;
  }

  get hellos(): Record<string, unknown>[] {
    return this.received.filter((frame) => frame['t'] === 'hello');
  }

  get acks(): number[] {
    return this.received.filter((frame) => frame['t'] === 'ack').map((frame) => frame['v'] as number);
  }

  rejectKeysOtherThan(key: string): void {
    this.acceptedKey = key;
  }

  sendHeartbeatsEvery(ms: number | null): void {
    this.heartbeatMs = ms;
    if (this.heartbeatTimer !== null) {
      clearInterval(this.heartbeatTimer);
      this.heartbeatTimer = null;
    }
    if (ms !== null) {
      this.heartbeatTimer = setInterval(
        () => this.broadcast({ t: 'hb', ts: Date.now(), v: this.version }),
        ms,
      );
    }
  }

  forgetHistoryBefore(version: number): void {
    this.retainedFrom = version;
  }

  publish(...changes: StreamChange[]): number {
    this.version += 1;
    changes.forEach((change) => this.applyToState(change));
    const entry: StreamEntry = { v: this.version, committedAtMs: Date.now(), changes };
    this.history.push(entry);
    const frame: DeltasFrame = { t: 'deltas', from: this.version - 1, to: this.version, entries: [entry] };
    this.broadcast(frame);
    return this.version;
  }

  publishSilently(...changes: StreamChange[]): number {
    this.version += 1;
    changes.forEach((change) => this.applyToState(change));
    this.history.push({ v: this.version, committedAtMs: Date.now(), changes });
    return this.version;
  }

  broadcast(frame: ServerFrame): void {
    const text = JSON.stringify(frame);
    this.connections.forEach((socket) => socket.send(text));
  }

  dropConnections(code = 1011): void {
    this.connections.splice(0).forEach((socket) => socket.close(code));
  }

  async stop(): Promise<void> {
    this.sendHeartbeatsEvery(null);
    this.server.clients.forEach((client) => client.terminate());
    await new Promise<void>((resolve) => this.server.close(() => resolve()));
  }

  private accept(socket: WebSocket): void {
    this.connections.push(socket);
    socket.on('close', () => {
      const index = this.connections.indexOf(socket);
      if (index >= 0) {
        this.connections.splice(index, 1);
      }
    });
    socket.on('message', (data) => {
      const frame = JSON.parse(data.toString()) as Record<string, unknown>;
      this.received.push(frame);
      if (frame['t'] === 'hello') {
        this.answerHello(socket, frame);
      }
    });
    if (this.heartbeatMs !== null) {
      this.sendHeartbeatsEvery(this.heartbeatMs);
    }
  }

  private answerHello(socket: WebSocket, hello: Record<string, unknown>): void {
    if (hello['sdkKey'] !== this.acceptedKey) {
      socket.send(JSON.stringify({ t: 'error', code: 4401, message: 'unknown key' }));
      socket.close(4401);
      return;
    }
    const known = hello['version'];
    if (typeof known === 'number' && known <= this.version && known >= this.retainedFrom) {
      const entries = this.history.filter((entry) => entry.v > known);
      socket.send(JSON.stringify({ t: 'deltas', from: known, to: this.version, entries }));
      return;
    }
    socket.send(
      JSON.stringify({
        t: 'snapshot',
        v: this.version,
        committedAtMs: Date.now(),
        flags: this.flags,
        segments: this.segments,
      }),
    );
  }

  private applyToState(change: StreamChange): void {
    const list = change.kind === 'flag' ? this.flags : this.segments;
    const index = list.findIndex((item) => item.key === change.key);
    if (index >= 0) {
      list.splice(index, 1);
    }
    if (change.op === 'upsert') {
      (list as { key: string }[]).push(change.config);
    }
  }
}
