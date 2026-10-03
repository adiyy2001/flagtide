import { ChangeDetectionStrategy, Component, computed, input, output } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatRadioModule } from '@angular/material/radio';
import { MatSelectModule } from '@angular/material/select';
import { fixedServeDraft, removeAt, replaceAt, rolloutServeDraft, nextUid } from '../../domain/drafts';
import type { RolloutEntryDraft, ServeDraft } from '../../domain/drafts';
import type { Issue } from '../../domain/models';
import { describeRemaining, parsePercent, remainingUnits, splitEvenly } from '../../domain/rollout';
import { issuesAt } from '../../domain/validation';
import { Icon } from '../../shared/icon';

interface Slice {
  readonly variant: string;
  readonly width: number;
}

@Component({
  selector: 'admin-serve-editor',
  imports: [Icon, MatButtonModule, MatFormFieldModule, MatInputModule, MatRadioModule, MatSelectModule],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './serve-editor.html',
  styleUrl: './serve-editor.scss',
})
export class ServeEditor {
  readonly serve = input.required<ServeDraft>();
  readonly variantKeys = input.required<readonly string[]>();
  readonly issues = input<readonly Issue[]>([]);
  readonly path = input.required<string>();
  readonly idPrefix = input.required<string>();
  readonly label = input('Serve');
  readonly serveChange = output<ServeDraft>();

  protected readonly percentTexts = computed(() => this.serve().rollout.map((entry) => entry.percent));
  protected readonly remaining = computed(() => remainingUnits(this.percentTexts()));
  protected readonly remainingText = computed(() => describeRemaining(this.remaining()));
  protected readonly balanced = computed(() => this.remaining() === 0);
  protected readonly slices = computed<readonly Slice[]>(() =>
    this.serve().rollout.map((entry) => ({
      variant: entry.variant,
      width: Math.min(100, (parsePercent(entry.percent) ?? 0) / 1000),
    })),
  );
  protected readonly rolloutIssues = computed(() => issuesAt(this.issues(), `${this.path()}.rollout`));
  protected readonly variantIssues = computed(() => issuesAt(this.issues(), `${this.path()}.variant`));
  protected readonly canAddEntry = computed(() => this.serve().rollout.length < this.variantKeys().length);

  protected messages(index: number, field: 'variant' | 'percent'): string[] {
    return issuesAt(this.issues(), `${this.path()}.rollout.${index}.${field}`);
  }

  protected setMode(mode: 'variant' | 'rollout'): void {
    const current = this.serve();
    if (mode === current.mode) {
      return;
    }
    if (mode === 'variant') {
      this.serveChange.emit(fixedServeDraft(current.rollout[0]?.variant ?? this.variantKeys()[0] ?? ''));
      return;
    }
    const first = current.variant;
    const ordered = [first, ...this.variantKeys().filter((key) => key !== first)];
    this.serveChange.emit(rolloutServeDraft(ordered));
  }

  protected setVariant(variant: string): void {
    this.serveChange.emit({ ...this.serve(), variant });
  }

  protected updateEntry(index: number, patch: Partial<RolloutEntryDraft>): void {
    const entry = this.serve().rollout[index];
    if (entry !== undefined) {
      this.serveChange.emit({
        ...this.serve(),
        rollout: replaceAt(this.serve().rollout, index, { ...entry, ...patch }),
      });
    }
  }

  protected setPercent(index: number, event: Event): void {
    this.updateEntry(index, { percent: (event.target as HTMLInputElement).value });
  }

  protected addEntry(): void {
    const used = new Set(this.serve().rollout.map((entry) => entry.variant));
    const next = this.variantKeys().find((key) => !used.has(key)) ?? '';
    this.serveChange.emit({
      ...this.serve(),
      rollout: [...this.serve().rollout, { uid: nextUid('entry'), variant: next, percent: '0' }],
    });
  }

  protected removeEntry(index: number): void {
    this.serveChange.emit({ ...this.serve(), rollout: removeAt(this.serve().rollout, index) });
  }

  protected splitEvenly(): void {
    const shares = splitEvenly(this.serve().rollout.length);
    this.serveChange.emit({
      ...this.serve(),
      rollout: this.serve().rollout.map((entry, index) => ({ ...entry, percent: shares[index] ?? '0' })),
    });
  }

  protected fill(index: number): void {
    const others = this.serve()
      .rollout.filter((_, position) => position !== index)
      .map((entry) => entry.percent);
    const left = remainingUnits(others);
    this.updateEntry(index, { percent: left > 0 ? (left / 1000).toString() : '0' });
  }
}
