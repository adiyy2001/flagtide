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
import { MatSlideToggleModule } from '@angular/material/slide-toggle';
import type { MatSlideToggleChange } from '@angular/material/slide-toggle';
import { MatTableModule } from '@angular/material/table';
import { RouterLink } from '@angular/router';
import type { FlagType } from '@flagtide/core';
import { AdminApi } from '../../api/admin-api';
import { ApiError, describeFailure } from '../../api/problem';
import { EMPTY_FILTER, filterFlags, isFiltered } from '../../domain/filter';
import type { FlagFilter, FlagModel } from '../../domain/models';
import { formatTimestamp } from '../../domain/time';
import { Icon } from '../../shared/icon';
import { Notifier } from '../../shared/notifier';
import { Workspace } from '../../state/workspace';

@Component({
  selector: 'admin-flag-list',
  imports: [
    Icon,
    MatButtonModule,
    MatFormFieldModule,
    MatInputModule,
    MatSelectModule,
    MatSlideToggleModule,
    MatTableModule,
    RouterLink,
  ],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './flag-list.html',
  styleUrl: './flag-list.scss',
})
export class FlagList {
  private readonly api = inject(AdminApi);
  private readonly notifier = inject(Notifier);
  protected readonly workspace = inject(Workspace);

  protected readonly flags = signal<readonly FlagModel[]>([]);
  protected readonly loading = signal(true);
  protected readonly failure = signal<string | null>(null);
  protected readonly filter = signal<FlagFilter>(EMPTY_FILTER);
  protected readonly busy = signal<ReadonlySet<string>>(new Set());
  protected readonly columns = ['flag', 'type', 'state', 'rules', 'updated'] as const;
  protected readonly types: readonly FlagType[] = ['boolean', 'string', 'number', 'json'];

  protected readonly environment = computed(() => this.workspace.selected() ?? '');
  protected readonly visible = computed(() => filterFlags(this.flags(), this.filter(), this.environment()));
  protected readonly filtered = computed(() => isFiltered(this.filter()));
  protected readonly canWrite = computed(() => this.workspace.selectedHasKey());

  constructor() {
    effect(() => {
      this.workspace.project();
      untracked(() => void this.load());
    });
  }

  protected async load(): Promise<void> {
    this.loading.set(true);
    try {
      this.flags.set(await this.api.listFlags());
      this.failure.set(null);
    } catch (failure) {
      this.failure.set(describeFailure(failure));
    } finally {
      this.loading.set(false);
    }
  }

  protected setText(event: Event): void {
    this.filter.update((current) => ({ ...current, text: (event.target as HTMLInputElement).value }));
  }

  protected setType(type: FlagFilter['type']): void {
    this.filter.update((current) => ({ ...current, type }));
  }

  protected setState(state: FlagFilter['state']): void {
    this.filter.update((current) => ({ ...current, state }));
  }

  protected clearFilter(): void {
    this.filter.set(EMPTY_FILTER);
  }

  protected configOf(flag: FlagModel) {
    return flag.environments[this.environment()] ?? null;
  }

  protected ruleCount(flag: FlagModel): number {
    return this.configOf(flag)?.rules.length ?? 0;
  }

  protected updated(flag: FlagModel): string {
    return formatTimestamp(flag.updatedAt);
  }

  protected isBusy(flag: FlagModel): boolean {
    return this.busy().has(flag.key);
  }

  protected async toggle(flag: FlagModel, change: MatSlideToggleChange): Promise<void> {
    const enabled = change.checked;
    const environment = this.environment();
    this.busy.update((current) => new Set(current).add(flag.key));
    try {
      const saved = await this.api.setEnabled(environment, flag.key, enabled, flag.revision);
      this.flags.update((current) => current.map((item) => (item.key === saved.key ? saved : item)));
      this.notifier.success(
        `${flag.key} is ${enabled ? 'on' : 'off'} in ${this.workspace.environmentName(environment)}`,
      );
    } catch (failure) {
      change.source.checked = !enabled;
      if (failure instanceof ApiError && failure.isConflict) {
        this.notifier.failure(new Error(`${flag.key} was changed by someone else. The list was refreshed.`));
        await this.load();
      } else {
        this.notifier.failure(failure);
      }
    } finally {
      this.busy.update((current) => {
        const next = new Set(current);
        next.delete(flag.key);
        return next;
      });
    }
  }

  protected readonly trackByKey = (_index: number, flag: FlagModel): string => flag.key;
}
