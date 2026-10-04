import { ChangeDetectionStrategy, Component, computed, input, output } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatSelectModule } from '@angular/material/select';
import type { FlagType } from '@flagtide/core';
import { newVariantDraft, removeAt, replaceAt } from '../../domain/drafts';
import type { VariantDraft } from '../../domain/drafts';
import type { Issue } from '../../domain/models';
import { issuesAt } from '../../domain/validation';
import { Icon } from '../../shared/icon';

@Component({
  selector: 'admin-variants-editor',
  imports: [Icon, MatButtonModule, MatFormFieldModule, MatInputModule, MatSelectModule],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './variants-editor.html',
  styleUrl: './variants-editor.scss',
})
export class VariantsEditor {
  readonly type = input.required<FlagType>();
  readonly variants = input.required<readonly VariantDraft[]>();
  readonly issues = input<readonly Issue[]>([]);
  readonly idPrefix = input('variant');
  readonly variantsChange = output<readonly VariantDraft[]>();

  protected readonly generalIssues = computed(() => issuesAt(this.issues(), 'variants'));

  protected messages(index: number, field: 'key' | 'value'): string[] {
    return issuesAt(this.issues(), `variants.${index}.${field}`);
  }

  protected update(index: number, patch: Partial<VariantDraft>): void {
    const current = this.variants()[index];
    if (current !== undefined) {
      this.variantsChange.emit(replaceAt(this.variants(), index, { ...current, ...patch }));
    }
  }

  protected setKey(index: number, event: Event): void {
    this.update(index, { key: (event.target as HTMLInputElement).value });
  }

  protected setValue(index: number, event: Event): void {
    this.update(index, { valueText: (event.target as HTMLInputElement | HTMLTextAreaElement).value });
  }

  protected add(): void {
    const taken = this.variants().map((variant) => variant.key);
    this.variantsChange.emit([...this.variants(), newVariantDraft(this.type(), taken)]);
  }

  protected remove(index: number): void {
    this.variantsChange.emit(removeAt(this.variants(), index));
  }
}
