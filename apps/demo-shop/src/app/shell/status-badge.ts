import { ChangeDetectionStrategy, Component, inject } from '@angular/core';
import { FlagtideStatus } from '@flagtide/angular';

const LABELS = {
  connecting: 'Connecting',
  live: 'Live',
  stale: 'Cached flags',
  offline: 'Offline',
} as const;

@Component({
  selector: 'shop-status-badge',
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <span class="badge" role="status" [attr.data-status]="status()">
      <span class="dot" aria-hidden="true"></span>
      <span class="text">Flags: {{ label() }}</span>
    </span>
  `,
  styles: `
    .badge {
      display: inline-flex;
      align-items: center;
      gap: 0.5rem;
      padding: 0.25rem 0.7rem;
      border: 1px solid var(--line);
      border-radius: 999px;
      font-size: 0.8125rem;
      background: var(--surface);
    }
    .dot {
      width: 0.6rem;
      height: 0.6rem;
      border-radius: 50%;
      background: var(--status-color, var(--muted));
    }
    [data-status='live'] {
      --status-color: var(--ok);
    }
    [data-status='stale'] {
      --status-color: var(--warn);
    }
    [data-status='offline'] {
      --status-color: var(--bad);
    }
    [data-status='connecting'] .dot {
      border: 2px solid var(--muted);
      background: transparent;
    }
    [data-status='stale'] .dot {
      border-radius: 2px;
    }
    [data-status='offline'] .dot {
      border-radius: 2px;
      transform: rotate(45deg);
    }
  `,
})
export class StatusBadge {
  private readonly source = inject(FlagtideStatus);
  readonly status = this.source.status;
  readonly label = () => LABELS[this.source.status()];
}
