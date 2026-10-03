import { DOCUMENT } from '@angular/common';
import {
  ChangeDetectionStrategy,
  Component,
  DestroyRef,
  computed,
  effect,
  inject,
  signal,
  untracked,
} from '@angular/core';
import { AdminApi } from '../../api/admin-api';
import { describeFailure } from '../../api/problem';
import { appendPoint, linePath, niceMax } from '../../domain/chart';
import type { ChartPoint } from '../../domain/chart';
import type { PropagationModel } from '../../domain/models';
import { Workspace } from '../../state/workspace';

const POLL_MILLIS = 1000;
const WIDTH = 600;
const HEIGHT = 200;
const SERIES = [
  { key: 'p50', label: 'p50', className: 'p50' },
  { key: 'p95', label: 'p95', className: 'p95' },
  { key: 'p99', label: 'p99', className: 'p99' },
] as const;

interface Sample extends ChartPoint {
  readonly at: string;
}

@Component({
  selector: 'admin-propagation-monitor',
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './propagation-monitor.html',
  styleUrl: './propagation-monitor.scss',
})
export class PropagationMonitor {
  private readonly api = inject(AdminApi);
  private readonly document = inject(DOCUMENT);
  protected readonly workspace = inject(Workspace);

  protected readonly latest = signal<PropagationModel | null>(null);
  protected readonly history = signal<readonly Sample[]>([]);
  protected readonly failure = signal<string | null>(null);
  protected readonly width = WIDTH;
  protected readonly height = HEIGHT;
  protected readonly series = SERIES;

  protected readonly maximum = computed(() =>
    niceMax(Math.max(1, ...this.history().map((sample) => sample.p99))),
  );
  protected readonly gridLines = computed(() => {
    const maximum = this.maximum();
    return [0, 0.5, 1].map((fraction) => ({
      y: HEIGHT - fraction * HEIGHT,
      label: Math.round(maximum * fraction),
    }));
  });
  protected readonly paths = computed(() => {
    const history = this.history();
    const maximum = this.maximum();
    return SERIES.map((series) => ({
      ...series,
      d: linePath(
        history.map((sample) => sample[series.key]),
        WIDTH,
        HEIGHT,
        maximum,
        Math.max(2, history.length),
      ),
    }));
  });
  protected readonly idle = computed(() => (this.latest()?.samples ?? 0) === 0);
  protected readonly figures = computed(() => {
    const reading = this.latest();
    const timed = reading !== null && reading.samples > 0;
    return [
      { label: 'Connected clients', value: reading === null ? 'none' : String(reading.connectedClients) },
      { label: 'p50', value: timed ? `${this.formatMillis(reading.p50Millis)} ms` : 'none' },
      { label: 'p95', value: timed ? `${this.formatMillis(reading.p95Millis)} ms` : 'none' },
      { label: 'p99', value: timed ? `${this.formatMillis(reading.p99Millis)} ms` : 'none' },
      { label: 'Samples', value: reading === null ? 'none' : String(reading.samples) },
    ];
  });
  protected readonly recent = computed(() => [...this.history()].slice(-10).reverse());

  constructor() {
    effect((onCleanup) => {
      const environment = this.workspace.selected();
      untracked(() => {
        this.latest.set(null);
        this.history.set([]);
        this.failure.set(null);
      });
      if (environment === null) {
        return;
      }
      void this.poll(environment);
      const timer = setInterval(() => void this.poll(environment), POLL_MILLIS);
      onCleanup(() => clearInterval(timer));
    });
    inject(DestroyRef).onDestroy(() => this.latest.set(null));
  }

  protected formatMillis(value: number): string {
    return value >= 100 ? value.toFixed(0) : value.toFixed(1);
  }

  private async poll(environment: string): Promise<void> {
    if (this.document.hidden) {
      return;
    }
    try {
      const reading = await this.api.readPropagation(environment);
      if (this.workspace.selected() !== environment) {
        return;
      }
      this.latest.set(reading);
      this.failure.set(null);
      if (reading.samples > 0) {
        const sample: Sample = {
          at: new Date().toISOString().slice(11, 19),
          p50: reading.p50Millis,
          p95: reading.p95Millis,
          p99: reading.p99Millis,
        };
        this.history.update((current) => appendPoint(current, sample));
      }
    } catch (failure) {
      this.failure.set(describeFailure(failure));
    }
  }
}
