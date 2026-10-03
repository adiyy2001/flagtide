import { ChangeDetectionStrategy, Component, computed, input, signal } from '@angular/core';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { evaluate, indexSegments } from '@flagwire/core';
import type { EvaluationResult, FlagConfig, Segment } from '@flagwire/core';

type Outcome =
  | { readonly kind: 'result'; readonly result: EvaluationResult }
  | { readonly kind: 'problem'; readonly message: string };

const REASONS: Record<EvaluationResult['reason'], string> = {
  KILL_SWITCH: 'The kill switch is engaged, so the off variant is served',
  OFF: 'The flag is off in this environment, so the off variant is served',
  RULE_MATCH: 'A targeting rule matched',
  FALLTHROUGH: 'No rule matched, so the fallthrough is served',
};

function parseAttributes(text: string): Record<string, unknown> | string {
  if (text.trim() === '') {
    return {};
  }
  try {
    const parsed: unknown = JSON.parse(text);
    if (typeof parsed !== 'object' || parsed === null || Array.isArray(parsed)) {
      return 'Attributes must be a JSON object such as {"plan": "pro"}';
    }
    return parsed as Record<string, unknown>;
  } catch {
    return 'Attributes are not valid JSON';
  }
}

@Component({
  selector: 'admin-try-context',
  imports: [MatFormFieldModule, MatInputModule],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './try-context.html',
  styleUrl: './try-context.scss',
})
export class TryContext {
  readonly config = input.required<FlagConfig | null>();
  readonly segments = input<readonly Segment[]>([]);
  readonly idPrefix = input.required<string>();

  protected readonly contextKey = signal('user-42');
  protected readonly attributesText = signal('{\n  "plan": "pro"\n}');

  protected readonly outcome = computed<Outcome>(() => {
    const config = this.config();
    if (config === null) {
      return { kind: 'problem', message: 'Fix the problems in this configuration to try a context.' };
    }
    const attributes = parseAttributes(this.attributesText());
    if (typeof attributes === 'string') {
      return { kind: 'problem', message: attributes };
    }
    try {
      const result = evaluate(config, { key: this.contextKey(), attributes }, indexSegments(this.segments()));
      return { kind: 'result', result };
    } catch (failure) {
      return { kind: 'problem', message: failure instanceof Error ? failure.message : 'Evaluation failed' };
    }
  });

  protected readonly result = computed(() => {
    const outcome = this.outcome();
    return outcome.kind === 'result' ? outcome.result : null;
  });

  protected readonly problem = computed(() => {
    const outcome = this.outcome();
    return outcome.kind === 'problem' ? outcome.message : null;
  });

  protected explanation(result: EvaluationResult): string {
    return REASONS[result.reason];
  }

  protected valueText(result: EvaluationResult): string {
    return JSON.stringify(result.value);
  }

  protected setKey(event: Event): void {
    this.contextKey.set((event.target as HTMLInputElement).value);
  }

  protected setAttributes(event: Event): void {
    this.attributesText.set((event.target as HTMLTextAreaElement).value);
  }
}
