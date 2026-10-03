import { isPlatformServer } from '@angular/common';
import {
  DestroyRef,
  Injectable,
  PLATFORM_ID,
  TransferState,
  inject,
  makeStateKey,
  signal,
} from '@angular/core';
import type { Signal } from '@angular/core';
import { createFlagwireClient, createMemoryStore, fetchSnapshot } from '@flagwire/core';
import type { ConnectionStatus, EvaluationContext, FlagSnapshot, FlagwireClient } from '@flagwire/core';
import { FLAGWIRE_CONFIG, deriveSnapshotUrl } from './config';

const SNAPSHOT_KEY = makeStateKey<FlagSnapshot>('flagwire:snapshot');

/**
 * The service behind `injectFlag`, the directive and the guard. It owns the {@link FlagwireClient} and exposes
 * what changes as signals. Injecting it directly is for tooling such as the overrides panel.
 */
@Injectable()
export class Flagwire {
  /** The framework-agnostic client. */
  readonly client: FlagwireClient;
  /** Counts every change that can alter a flag value: a snapshot, a delta, an override or a new context. */
  readonly revision: Signal<number>;
  /** The connection status of the stream. */
  readonly status: Signal<ConnectionStatus>;

  private readonly config = inject(FLAGWIRE_CONFIG);
  private readonly transferState = inject(TransferState);
  private readonly onServer = isPlatformServer(inject(PLATFORM_ID));

  constructor() {
    const transferred = this.transferState.get(SNAPSHOT_KEY, null);
    this.client = createFlagwireClient({
      streamUrl: this.config.streamUrl,
      sdkKey: this.config.sdkKey,
      context: typeof this.config.context === 'function' ? this.config.context() : this.config.context,
      store: this.onServer ? createMemoryStore() : this.config.storage,
      initialSnapshot: transferred ?? undefined,
      timing: this.config.timing,
      socketFactory: this.config.socketFactory,
    });
    const revision = signal(0);
    const status = signal<ConnectionStatus>(this.client.status);
    this.revision = revision.asReadonly();
    this.status = status.asReadonly();
    const subscriptions = [
      this.client.changes$.subscribe(() => revision.update((count) => count + 1)),
      this.client.status$.subscribe((value) => status.set(value)),
    ];
    inject(DestroyRef).onDestroy(() => {
      subscriptions.forEach((subscription) => subscription.unsubscribe());
      this.client.stop();
    });
  }

  /** Changes who flags are evaluated for. */
  setContext(context: EvaluationContext): void {
    this.client.setContext(context);
  }

  /**
   * Prepares the client. On the server it fetches the snapshot over HTTP and puts it into `TransferState`. In the
   * browser it opens the stream. A server that cannot be reached leaves the client empty, so flags return their
   * fallbacks instead of failing the render.
   */
  async initialize(): Promise<void> {
    if (!this.onServer) {
      this.client.start();
      return;
    }
    try {
      const snapshot = await fetchSnapshot(
        this.config.snapshotUrl ?? deriveSnapshotUrl(this.config.streamUrl),
        this.config.sdkKey,
      );
      this.client.hydrate(snapshot);
      this.transferState.set(SNAPSHOT_KEY, snapshot);
    } catch {
      return;
    }
  }
}
