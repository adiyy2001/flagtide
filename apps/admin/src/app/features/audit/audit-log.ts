import {
  ChangeDetectionStrategy,
  Component,
  computed,
  effect,
  inject,
  signal,
  untracked,
} from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatSelectModule } from '@angular/material/select';
import { AdminApi } from '../../api/admin-api';
import { describeFailure } from '../../api/problem';
import { diffJson } from '../../domain/diff';
import type { DiffLine } from '../../domain/diff';
import type { AuditEntryModel } from '../../domain/models';
import { formatTimestamp } from '../../domain/time';
import { Icon } from '../../shared/icon';
import { Workspace } from '../../state/workspace';

const PAGE_SIZE = 25;

const MARKS: Readonly<Record<DiffLine['kind'], string>> = {
  added: '+',
  removed: '-',
  changed: '~',
};

const KIND_LABELS: Readonly<Record<DiffLine['kind'], string>> = {
  added: 'Added',
  removed: 'Removed',
  changed: 'Changed',
};

interface AuditRow {
  readonly entry: AuditEntryModel;
  readonly when: string;
  readonly lines: readonly DiffLine[];
}

@Component({
  selector: 'admin-audit-log',
  imports: [Icon, MatButtonModule, MatFormFieldModule, MatInputModule, MatSelectModule],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './audit-log.html',
  styleUrl: './audit-log.scss',
})
export class AuditLog {
  private readonly api = inject(AdminApi);
  protected readonly workspace = inject(Workspace);

  protected readonly environmentFilter = signal<string>('');
  protected readonly entityFilter = signal('');
  protected readonly entries = signal<readonly AuditEntryModel[]>([]);
  protected readonly loading = signal(false);
  protected readonly failure = signal<string | null>(null);
  protected readonly exhausted = signal(false);
  protected readonly expanded = signal<ReadonlySet<string>>(new Set());

  protected readonly rows = computed<readonly AuditRow[]>(() =>
    this.entries().map((entry) => ({
      entry,
      when: formatTimestamp(entry.at),
      lines: diffJson(entry.before, entry.after),
    })),
  );

  constructor() {
    effect(() => {
      this.environmentFilter();
      this.entityFilter();
      this.workspace.environments();
      untracked(() => void this.reload());
    });
  }

  protected mark(kind: DiffLine['kind']): string {
    return MARKS[kind];
  }

  protected kindLabel(kind: DiffLine['kind']): string {
    return KIND_LABELS[kind];
  }

  protected isOpen(id: string): boolean {
    return this.expanded().has(id);
  }

  protected toggle(id: string): void {
    this.expanded.update((current) => {
      const next = new Set(current);
      if (!next.delete(id)) {
        next.add(id);
      }
      return next;
    });
  }

  protected setEnvironment(value: string): void {
    this.environmentFilter.set(value);
  }

  protected setEntity(event: Event): void {
    this.entityFilter.set((event.target as HTMLInputElement).value.trim());
  }

  protected async reload(): Promise<void> {
    this.entries.set([]);
    this.exhausted.set(false);
    await this.fetch(0);
  }

  protected async loadMore(): Promise<void> {
    await this.fetch(this.entries().length);
  }

  private async fetch(offset: number): Promise<void> {
    this.loading.set(true);
    this.failure.set(null);
    try {
      const environment = this.environmentFilter();
      const entity = this.entityFilter();
      const page = await this.api.readAudit({
        environment: environment === '' ? undefined : environment,
        entity: entity === '' ? undefined : entity,
        limit: PAGE_SIZE,
        offset,
      });
      this.entries.update((current) => (offset === 0 ? page : [...current, ...page]));
      this.exhausted.set(page.length < PAGE_SIZE);
    } catch (failure) {
      this.failure.set(describeFailure(failure));
    } finally {
      this.loading.set(false);
    }
  }
}
