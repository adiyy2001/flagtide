import {
  Directive,
  TemplateRef,
  ViewContainerRef,
  computed,
  effect,
  inject,
  input,
  untracked,
} from '@angular/core';
import { jsonEquals } from '@flagwire/core';
import type { JsonValue } from '@flagwire/core';
import { Flagwire } from './flagwire';

/** What a template of `*flagwireFlag` can read: the current value of the flag. */
export interface FlagwireFlagContext {
  $implicit: JsonValue;
  flagwireFlag: JsonValue;
}

type Rendered = 'then' | 'else' | null;

/**
 * Renders its template while a flag has the expected value, and the optional `else` template otherwise.
 *
 * ```html
 * <app-promo *flagwireFlag="'promo-banner'; else plain" />
 * <ng-template #plain>...</ng-template>
 * <p *flagwireFlag="'checkout-label'; equals: 'Buy now'">...</p>
 * ```
 *
 * `equals` defaults to `true`. A flag that is unknown or of another type never matches.
 */
@Directive({ selector: '[flagwireFlag]' })
export class FlagwireFlagDirective {
  readonly flagwireFlag = input.required<string>();
  readonly flagwireFlagEquals = input<JsonValue>(true);
  readonly flagwireFlagElse = input<TemplateRef<FlagwireFlagContext> | null>(null);

  private readonly flagwire = inject(Flagwire);
  private readonly container = inject(ViewContainerRef);
  private readonly template = inject<TemplateRef<FlagwireFlagContext>>(TemplateRef);
  private rendered: Rendered = null;

  private readonly outcome = computed(
    () => {
      this.flagwire.revision();
      const expected = this.flagwireFlagEquals();
      const resolution = this.flagwire.client.resolve(this.flagwireFlag(), expected);
      const known = resolution.reason !== 'FLAG_NOT_FOUND' && resolution.reason !== 'TYPE_MISMATCH';
      return { matches: known && jsonEquals(resolution.value, expected), value: resolution.value };
    },
    { equal: (left, right) => left.matches === right.matches && jsonEquals(left.value, right.value) },
  );

  constructor() {
    effect(() => {
      const { matches, value } = this.outcome();
      const elseTemplate = this.flagwireFlagElse();
      untracked(() => this.render(matches ? 'then' : 'else', matches ? this.template : elseTemplate, value));
    });
  }

  private render(
    kind: Exclude<Rendered, null>,
    template: TemplateRef<FlagwireFlagContext> | null,
    value: JsonValue,
  ): void {
    if (this.rendered === kind && kind === 'then') {
      return;
    }
    this.container.clear();
    this.rendered = null;
    if (template !== null) {
      this.container.createEmbeddedView(template, { $implicit: value, flagwireFlag: value });
      this.rendered = kind;
    }
  }

  static ngTemplateContextGuard(
    directive: FlagwireFlagDirective,
    context: unknown,
  ): context is FlagwireFlagContext {
    return directive !== null && context !== null;
  }
}
