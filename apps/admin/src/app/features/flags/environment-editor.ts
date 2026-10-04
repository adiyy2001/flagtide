import { CdkDrag, CdkDropList } from '@angular/cdk/drag-drop';
import type { CdkDragDrop } from '@angular/cdk/drag-drop';
import { ChangeDetectionStrategy, Component, computed, input, output } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatSelectModule } from '@angular/material/select';
import { MatSlideToggleModule } from '@angular/material/slide-toggle';
import type { Segment } from '@flagtide/core';
import { moveItem, newRuleDraft, removeAt, replaceAt } from '../../domain/drafts';
import type { EnvironmentDraft, RuleDraft, ServeDraft } from '../../domain/drafts';
import type { SegmentModel } from '../../domain/models';
import { describeIssue, issuesAt } from '../../domain/validation';
import { flagConfigOf } from '../../domain/wire';
import { Icon } from '../../shared/icon';
import type { FlagEditorState } from '../../state/flag-editor-state';
import { environmentSection } from '../../state/flag-editor-state';
import { RuleEditor } from './rule-editor';
import { ServeEditor } from './serve-editor';
import { TryContext } from './try-context';

@Component({
  selector: 'admin-environment-editor',
  imports: [
    CdkDrag,
    CdkDropList,
    Icon,
    MatButtonModule,
    MatFormFieldModule,
    MatSelectModule,
    MatSlideToggleModule,
    RuleEditor,
    ServeEditor,
    TryContext,
  ],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './environment-editor.html',
  styleUrl: './environment-editor.scss',
})
export class EnvironmentEditor {
  readonly state = input.required<FlagEditorState>();
  readonly environment = input.required<string>();
  readonly environmentName = input.required<string>();
  readonly canWrite = input.required<boolean>();
  readonly saving = input(false);
  readonly segments = input<readonly SegmentModel[]>([]);
  readonly saved = output<void>();
  readonly discarded = output<void>();
  readonly killSwitchRequested = output<boolean>();

  protected readonly flag = computed(() => this.state().flag());
  protected readonly config = computed(() => this.flag().environments[this.environment()] ?? null);
  protected readonly draft = computed(() => this.state().environments()[this.environment()] ?? null);
  protected readonly issues = computed(() => this.state().environmentIssues()[this.environment()] ?? []);
  protected readonly issueLines = computed(() => this.issues().map(describeIssue));
  protected readonly dirty = computed(() => this.state().isDirty(environmentSection(this.environment())));
  protected readonly variantKeys = computed(() => this.state().variantKeys());
  protected readonly segmentKeys = computed(() => this.segments().map((segment) => segment.key));
  protected readonly offIssues = computed(() => issuesAt(this.issues(), 'offVariant'));
  protected readonly canSave = computed(
    () => this.canWrite() && this.dirty() && this.issues().length === 0 && !this.saving(),
  );
  protected readonly tryConfig = computed(() => {
    const config = this.config();
    const draft = this.draft();
    return config === null || draft === null || this.issues().length > 0
      ? null
      : flagConfigOf(this.flag(), config, draft);
  });
  protected readonly tryContextSegments = computed<readonly Segment[]>(() => this.segments());

  protected change(update: (draft: EnvironmentDraft) => EnvironmentDraft): void {
    const draft = this.draft();
    if (draft !== null) {
      this.state().setEnvironment(this.environment(), update(draft));
    }
  }

  protected setEnabled(enabled: boolean): void {
    this.change((draft) => ({ ...draft, enabled }));
  }

  protected setOffVariant(offVariant: string): void {
    this.change((draft) => ({ ...draft, offVariant }));
  }

  protected setFallthrough(fallthrough: ServeDraft): void {
    this.change((draft) => ({ ...draft, fallthrough }));
  }

  protected setRule(index: number, rule: RuleDraft): void {
    this.change((draft) => ({ ...draft, rules: replaceAt(draft.rules, index, rule) }));
  }

  protected removeRule(index: number): void {
    this.change((draft) => ({ ...draft, rules: removeAt(draft.rules, index) }));
  }

  protected moveRule(from: number, to: number): void {
    this.change((draft) => ({ ...draft, rules: moveItem(draft.rules, from, to) }));
  }

  protected drop(event: CdkDragDrop<readonly RuleDraft[]>): void {
    this.moveRule(event.previousIndex, event.currentIndex);
  }

  protected addRule(): void {
    this.change((draft) => ({
      ...draft,
      rules: [
        ...draft.rules,
        newRuleDraft(
          draft.rules.map((rule) => rule.id),
          this.variantKeys()[0] ?? '',
        ),
      ],
    }));
  }
}
