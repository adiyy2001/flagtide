import { CdkDragHandle } from '@angular/cdk/drag-drop';
import { ChangeDetectionStrategy, Component, computed, input, output } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { newCondition, removeAt, replaceAt } from '../../domain/drafts';
import type { ConditionDraft, RuleDraft, ServeDraft } from '../../domain/drafts';
import type { Issue } from '../../domain/models';
import { issuesAt, issuesUnder } from '../../domain/validation';
import { Icon } from '../../shared/icon';
import { ConditionEditor } from './condition-editor';
import { ServeEditor } from './serve-editor';

@Component({
  selector: 'admin-rule-editor',
  imports: [
    CdkDragHandle,
    ConditionEditor,
    Icon,
    MatButtonModule,
    MatFormFieldModule,
    MatInputModule,
    ServeEditor,
  ],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './rule-editor.html',
  styleUrl: './rule-editor.scss',
})
export class RuleEditor {
  readonly rule = input.required<RuleDraft>();
  readonly index = input.required<number>();
  readonly count = input.required<number>();
  readonly variantKeys = input.required<readonly string[]>();
  readonly segmentKeys = input<readonly string[]>([]);
  readonly issues = input<readonly Issue[]>([]);
  readonly idPrefix = input.required<string>();
  readonly ruleChange = output<RuleDraft>();
  readonly movedUp = output<void>();
  readonly movedDown = output<void>();
  readonly removed = output<void>();

  protected readonly path = computed(() => `rules.${this.index()}`);
  protected readonly nameIssues = computed(() => issuesAt(this.issues(), `${this.path()}.id`));
  protected readonly ruleIssueCount = computed(() => issuesUnder(this.issues(), this.path()).length);
  protected readonly title = computed(() => `Rule ${this.index() + 1}`);

  protected setName(event: Event): void {
    this.ruleChange.emit({ ...this.rule(), id: (event.target as HTMLInputElement).value });
  }

  protected setCondition(position: number, condition: ConditionDraft): void {
    this.ruleChange.emit({
      ...this.rule(),
      conditions: replaceAt(this.rule().conditions, position, condition),
    });
  }

  protected removeCondition(position: number): void {
    this.ruleChange.emit({ ...this.rule(), conditions: removeAt(this.rule().conditions, position) });
  }

  protected addCondition(kind: 'attribute' | 'segment'): void {
    const segment = kind === 'segment' ? (this.segmentKeys()[0] ?? '') : '';
    this.ruleChange.emit({
      ...this.rule(),
      conditions: [...this.rule().conditions, newCondition(kind, segment)],
    });
  }

  protected setServe(serve: ServeDraft): void {
    this.ruleChange.emit({ ...this.rule(), serve });
  }
}
