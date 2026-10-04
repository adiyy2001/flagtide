import { ChangeDetectionStrategy, Component, computed, input, output } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { MatCheckboxModule } from '@angular/material/checkbox';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatSelectModule } from '@angular/material/select';
import type { Operator } from '@flagtide/core';
import type { ConditionDraft } from '../../domain/drafts';
import type { Issue } from '../../domain/models';
import { OPERATORS, operatorInfo } from '../../domain/operators';
import type { ScalarType } from '../../domain/operators';
import { issuesAt } from '../../domain/validation';
import { Icon } from '../../shared/icon';

@Component({
  selector: 'admin-condition-editor',
  imports: [Icon, MatButtonModule, MatCheckboxModule, MatFormFieldModule, MatInputModule, MatSelectModule],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './condition-editor.html',
  styleUrl: './condition-editor.scss',
})
export class ConditionEditor {
  readonly condition = input.required<ConditionDraft>();
  readonly segmentKeys = input<readonly string[]>([]);
  readonly allowSegment = input(true);
  readonly issues = input<readonly Issue[]>([]);
  readonly path = input.required<string>();
  readonly idPrefix = input.required<string>();
  readonly label = input.required<string>();
  readonly conditionChange = output<ConditionDraft>();
  readonly removed = output<void>();

  protected readonly operators = OPERATORS;
  protected readonly scalarTypes: readonly ScalarType[] = ['string', 'number', 'boolean'];
  protected readonly info = computed(() => operatorInfo(this.condition().operator));
  protected readonly showsType = computed(() => this.info().kind === 'scalar');
  protected readonly hint = computed(() => {
    const info = this.info();
    switch (info.kind) {
      case 'semver':
        return 'One version such as 2.1.0 or 2.0.0-rc.1';
      case 'number':
        return 'One number';
      case 'text':
        return 'One or more values separated by commas';
      case 'scalar':
        return info.many ? 'Values separated by commas' : 'One value';
    }
  });

  protected messages(field: 'attribute' | 'values' | 'segment'): string[] {
    return issuesAt(this.issues(), `${this.path()}.${field}`);
  }

  protected patch(change: Partial<ConditionDraft>): void {
    this.conditionChange.emit({ ...this.condition(), ...change });
  }

  protected setText(field: 'attribute' | 'valuesText', event: Event): void {
    this.patch({ [field]: (event.target as HTMLInputElement).value });
  }

  protected setOperator(operator: Operator): void {
    this.patch({ operator });
  }

  protected setKind(kind: 'attribute' | 'segment'): void {
    this.patch({ kind, segment: kind === 'segment' ? (this.segmentKeys()[0] ?? '') : '' });
  }
}
