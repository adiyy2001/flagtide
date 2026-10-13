import type {
  ConnectionStatus,
  FlagtideClient,
  JsonValue as FlagtideJsonValue,
  Resolution,
  ResolutionReason as FlagtideReason,
  StreamError,
} from '@flagtide/core';
import {
  ErrorCode,
  GeneralError,
  OpenFeatureEventEmitter,
  ProviderEvents,
  ProviderFatalError,
  StandardResolutionReasons,
} from '@openfeature/web-sdk';
import type {
  EvaluationContext,
  FlagMetadata,
  JsonValue,
  Provider,
  ResolutionDetails,
  ResolutionReason,
} from '@openfeature/web-sdk';
import { Subscription, filter, firstValueFrom, map, race, take, timer } from 'rxjs';
import { toFlagtideContext } from './context.js';

const SDK_KEY_REJECTED = 4401;
const DEFAULT_READY_TIMEOUT_MS = 10_000;

const REASONS: Readonly<
  Record<Exclude<FlagtideReason, 'FLAG_NOT_FOUND' | 'TYPE_MISMATCH'>, ResolutionReason>
> = {
  KILL_SWITCH: StandardResolutionReasons.DISABLED,
  OFF: StandardResolutionReasons.DISABLED,
  RULE_MATCH: StandardResolutionReasons.TARGETING_MATCH,
  FALLTHROUGH: StandardResolutionReasons.DEFAULT,
  OVERRIDE: 'OVERRIDE',
};

type ProviderState = 'idle' | 'initializing' | 'ready' | 'stale' | 'error' | 'fatal';

export interface FlagtideWebProviderOptions {
  readonly readyTimeoutMs?: number;
}

function metadataOf(resolution: Resolution): FlagMetadata {
  const metadata: Record<string, string | number> = {};
  if (resolution.ruleId !== null) {
    metadata['ruleId'] = resolution.ruleId;
  }
  if (resolution.ruleIndex !== null) {
    metadata['ruleIndex'] = resolution.ruleIndex;
  }
  if (resolution.bucket !== null) {
    metadata['bucket'] = resolution.bucket;
  }
  return metadata;
}

function reasonOf(resolution: Resolution): ResolutionReason {
  const reason = resolution.reason;
  if (reason === 'FLAG_NOT_FOUND' || reason === 'TYPE_MISMATCH') {
    return StandardResolutionReasons.ERROR;
  }
  if ((reason === 'RULE_MATCH' || reason === 'FALLTHROUGH') && resolution.bucket !== null) {
    return StandardResolutionReasons.SPLIT;
  }
  return REASONS[reason];
}

export class FlagtideWebProvider implements Provider {
  readonly metadata = { name: 'flagtide' } as const;
  readonly runsOn = 'client' as const;
  readonly events = new OpenFeatureEventEmitter();
  private readonly readyTimeoutMs: number;
  private readonly anonymousKey: string;
  private subscription = new Subscription();
  private state: ProviderState = 'idle';
  private applyingContext = false;

  constructor(
    private readonly client: FlagtideClient,
    options: FlagtideWebProviderOptions = {},
  ) {
    this.readyTimeoutMs = options.readyTimeoutMs ?? DEFAULT_READY_TIMEOUT_MS;
    this.anonymousKey = client.context.key;
  }

  async initialize(context?: EvaluationContext): Promise<void> {
    this.state = 'initializing';
    if (context !== undefined && Object.keys(context).length > 0) {
      this.applyContext(context);
    }
    this.watch();
    this.client.start();
    try {
      await this.firstData();
      this.state = 'ready';
    } catch (error) {
      this.state = error instanceof ProviderFatalError ? 'fatal' : 'error';
      throw error;
    }
  }

  onContextChange(_oldContext: EvaluationContext, newContext: EvaluationContext): void {
    this.applyContext(newContext);
  }

  onClose(): Promise<void> {
    this.subscription.unsubscribe();
    this.subscription = new Subscription();
    this.client.stop();
    this.state = 'idle';
    return Promise.resolve();
  }

  resolveBooleanEvaluation(flagKey: string, defaultValue: boolean): ResolutionDetails<boolean> {
    return this.resolve(flagKey, defaultValue);
  }

  resolveStringEvaluation(flagKey: string, defaultValue: string): ResolutionDetails<string> {
    return this.resolve(flagKey, defaultValue);
  }

  resolveNumberEvaluation(flagKey: string, defaultValue: number): ResolutionDetails<number> {
    return this.resolve(flagKey, defaultValue);
  }

  resolveObjectEvaluation<T extends JsonValue>(flagKey: string, defaultValue: T): ResolutionDetails<T> {
    return this.resolve(flagKey, defaultValue);
  }

  private resolve<T extends JsonValue>(flagKey: string, defaultValue: T): ResolutionDetails<T> {
    const resolution = this.client.resolve<FlagtideJsonValue>(flagKey, defaultValue);
    const reason = reasonOf(resolution);
    if (resolution.reason === 'FLAG_NOT_FOUND') {
      return {
        value: defaultValue,
        reason,
        errorCode: ErrorCode.FLAG_NOT_FOUND,
        errorMessage: `flag "${flagKey}" is not in the environment`,
      };
    }
    if (resolution.reason === 'TYPE_MISMATCH') {
      return {
        value: defaultValue,
        reason,
        errorCode: ErrorCode.TYPE_MISMATCH,
        errorMessage: `flag "${flagKey}" is a ${this.client.flagType(flagKey) ?? 'different'} flag`,
      };
    }
    return {
      value: resolution.value as T,
      reason,
      flagMetadata: metadataOf(resolution),
      ...(resolution.variantKey === null ? {} : { variant: resolution.variantKey }),
    };
  }

  private applyContext(context: EvaluationContext): void {
    this.applyingContext = true;
    try {
      this.client.setContext(toFlagtideContext(context, this.anonymousKey));
    } finally {
      this.applyingContext = false;
    }
  }

  private async firstData(): Promise<void> {
    if (this.client.version !== null) {
      return;
    }
    const loaded$ = this.client.changes$.pipe(
      filter(() => this.client.version !== null),
      map((): Error | null => null),
    );
    const rejected$ = this.client.errors$.pipe(
      filter((error) => error.code === SDK_KEY_REJECTED),
      map((error): Error | null => new ProviderFatalError(error.message)),
    );
    const timedOut$ = timer(this.readyTimeoutMs).pipe(
      map(
        (): Error | null =>
          new GeneralError(`no flag data arrived within ${this.readyTimeoutMs} ms, still connecting`),
      ),
    );
    const failure = await firstValueFrom(race(loaded$, rejected$, timedOut$).pipe(take(1)));
    if (failure !== null) {
      throw failure;
    }
  }

  private watch(): void {
    this.subscription.unsubscribe();
    this.subscription = new Subscription();
    this.subscription.add(this.client.changes$.subscribe(() => this.changed()));
    this.subscription.add(this.client.status$.subscribe((status) => this.statusChanged(status)));
    this.subscription.add(this.client.errors$.subscribe((error) => this.failed(error)));
  }

  private changed(): void {
    if (this.applyingContext || this.client.version === null) {
      return;
    }
    if (this.state === 'error') {
      this.state = 'ready';
      this.events.emit(ProviderEvents.Ready);
      return;
    }
    if (this.state === 'ready' || this.state === 'stale') {
      this.events.emit(ProviderEvents.ConfigurationChanged);
    }
  }

  private statusChanged(status: ConnectionStatus): void {
    if (this.state === 'ready' && (status === 'stale' || status === 'offline')) {
      this.state = 'stale';
      this.events.emit(ProviderEvents.Stale);
      return;
    }
    if (this.state === 'stale' && status === 'live') {
      this.state = 'ready';
      this.events.emit(ProviderEvents.Ready);
    }
  }

  private failed(error: StreamError): void {
    if (error.code !== SDK_KEY_REJECTED || this.state === 'initializing' || this.state === 'idle') {
      return;
    }
    this.state = 'fatal';
    this.events.emit(ProviderEvents.Error, { message: error.message, errorCode: ErrorCode.PROVIDER_FATAL });
  }
}
