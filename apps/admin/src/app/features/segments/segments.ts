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
import { AdminApi } from '../../api/admin-api';
import { ApiError, describeFailure } from '../../api/problem';
import { newCondition, removeAt, replaceAt } from '../../domain/drafts';
import type { ConditionDraft } from '../../domain/drafts';
import type { SegmentModel } from '../../domain/models';
import {
  emptySegmentDraft,
  newGroup,
  segmentBody,
  segmentDraftOf,
  serializeSegment,
  validateSegment,
} from '../../domain/segment-draft';
import type { GroupDraft, SegmentDraft } from '../../domain/segment-draft';
import { describeIssue, issuesAt } from '../../domain/validation';
import { Confirm } from '../../shared/confirm';
import { Icon } from '../../shared/icon';
import { Notifier } from '../../shared/notifier';
import { Workspace } from '../../state/workspace';
import { ConditionEditor } from '../flags/condition-editor';

@Component({
  selector: 'admin-segments',
  imports: [ConditionEditor, Icon, MatButtonModule, MatFormFieldModule, MatInputModule],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './segments.html',
  styleUrl: './segments.scss',
})
export class Segments {
  private readonly api = inject(AdminApi);
  private readonly notifier = inject(Notifier);
  private readonly confirm = inject(Confirm);
  protected readonly workspace = inject(Workspace);

  protected readonly segments = signal<readonly SegmentModel[]>([]);
  protected readonly loading = signal(true);
  protected readonly failure = signal<string | null>(null);
  protected readonly current = signal<SegmentModel | 'new' | null>(null);
  protected readonly draft = signal<SegmentDraft | null>(null);
  protected readonly baseline = signal('');
  protected readonly saving = signal(false);

  protected readonly environment = computed(() => this.workspace.selected() ?? '');
  protected readonly isNew = computed(() => this.current() === 'new');
  protected readonly activeKey = computed(() => {
    const current = this.current();
    return current === null || current === 'new' ? null : current.key;
  });
  protected readonly issues = computed(() => {
    const draft = this.draft();
    return draft === null
      ? []
      : validateSegment(
          draft,
          this.isNew(),
          this.segments().map((segment) => segment.key),
        );
  });
  protected readonly lines = computed(() => this.issues().map(describeIssue));
  protected readonly dirty = computed(() => {
    const draft = this.draft();
    return draft !== null && serializeSegment(draft) !== this.baseline();
  });
  protected readonly canWrite = computed(() => this.workspace.selectedHasKey());
  protected readonly canSave = computed(
    () => this.canWrite() && this.dirty() && this.issues().length === 0 && !this.saving(),
  );

  constructor() {
    effect(() => {
      const environment = this.workspace.selected();
      if (environment !== null) {
        untracked(() => void this.load(environment));
      }
    });
  }

  protected messages(path: string): string[] {
    return issuesAt(this.issues(), path);
  }

  protected async load(environment: string): Promise<void> {
    this.loading.set(true);
    this.draft.set(null);
    this.current.set(null);
    try {
      this.segments.set(await this.api.listSegments(environment));
      this.failure.set(null);
    } catch (failure) {
      this.failure.set(describeFailure(failure));
    } finally {
      this.loading.set(false);
    }
  }

  protected async open(segment: SegmentModel | 'new'): Promise<void> {
    if (this.dirty() && !(await this.confirmDiscard())) {
      return;
    }
    const draft = segment === 'new' ? emptySegmentDraft() : segmentDraftOf(segment);
    this.current.set(segment);
    this.draft.set(draft);
    this.baseline.set(serializeSegment(draft));
  }

  private confirmDiscard(): Promise<boolean> {
    return this.confirm.ask({
      title: 'Discard your changes?',
      message: 'The segment you are editing has unsaved changes.',
      confirmLabel: 'Discard changes',
      cancelLabel: 'Keep editing',
    });
  }

  protected patch(change: Partial<SegmentDraft>): void {
    this.draft.update((draft) => (draft === null ? draft : { ...draft, ...change }));
  }

  protected setText(field: 'key' | 'name' | 'included' | 'excluded', event: Event): void {
    this.patch({ [field]: (event.target as HTMLInputElement | HTMLTextAreaElement).value });
  }

  protected addGroup(): void {
    const draft = this.draft();
    if (draft !== null) {
      this.patch({ groups: [...draft.groups, newGroup()] });
    }
  }

  protected removeGroup(index: number): void {
    const draft = this.draft();
    if (draft !== null) {
      this.patch({ groups: removeAt(draft.groups, index) });
    }
  }

  protected updateGroup(index: number, group: GroupDraft): void {
    const draft = this.draft();
    if (draft !== null) {
      this.patch({ groups: replaceAt(draft.groups, index, group) });
    }
  }

  protected setCondition(groupIndex: number, index: number, condition: ConditionDraft): void {
    const group = this.draft()?.groups[groupIndex];
    if (group !== undefined) {
      this.updateGroup(groupIndex, { ...group, conditions: replaceAt(group.conditions, index, condition) });
    }
  }

  protected addCondition(groupIndex: number): void {
    const group = this.draft()?.groups[groupIndex];
    if (group !== undefined) {
      this.updateGroup(groupIndex, {
        ...group,
        conditions: [...group.conditions, newCondition('attribute')],
      });
    }
  }

  protected removeCondition(groupIndex: number, index: number): void {
    const group = this.draft()?.groups[groupIndex];
    if (group !== undefined) {
      this.updateGroup(groupIndex, { ...group, conditions: removeAt(group.conditions, index) });
    }
  }

  protected discard(): void {
    const current = this.current();
    if (current === null) {
      return;
    }
    const draft = current === 'new' ? emptySegmentDraft() : segmentDraftOf(current);
    this.draft.set(draft);
    this.baseline.set(serializeSegment(draft));
  }

  protected async save(): Promise<void> {
    const draft = this.draft();
    const current = this.current();
    if (draft === null || current === null || !this.canSave()) {
      return;
    }
    this.saving.set(true);
    try {
      const revision = current === 'new' ? null : current.revision;
      const saved = await this.api.saveSegment(this.environment(), draft.key, segmentBody(draft), revision);
      this.notifier.success(`Segment ${saved.key} saved`);
      this.segments.set(await this.api.listSegments(this.environment()));
      const refreshed = this.segments().find((segment) => segment.key === saved.key) ?? saved;
      const next = segmentDraftOf(refreshed);
      this.current.set(refreshed);
      this.draft.set(next);
      this.baseline.set(serializeSegment(next));
    } catch (failure) {
      if (failure instanceof ApiError && failure.isConflict) {
        this.notifier.failure(new Error('Someone else changed this segment. Reload the list and try again.'));
        await this.load(this.environment());
      } else {
        this.notifier.failure(failure);
      }
    } finally {
      this.saving.set(false);
    }
  }

  protected async remove(): Promise<void> {
    const current = this.current();
    if (current === null || current === 'new') {
      return;
    }
    const confirmed = await this.confirm.ask({
      title: `Delete segment ${current.key}?`,
      message: 'A segment that a flag still uses cannot be deleted.',
      confirmLabel: 'Delete segment',
      danger: true,
    });
    if (!confirmed) {
      return;
    }
    try {
      await this.api.deleteSegment(this.environment(), current.key, current.revision);
      this.notifier.success(`Segment ${current.key} deleted`);
      await this.load(this.environment());
    } catch (failure) {
      this.notifier.failure(failure);
    }
  }
}
