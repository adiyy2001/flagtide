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
import { jsonEquals } from '@flagtide/core';
import type { JsonValue } from '@flagtide/core';
import { Flagtide } from './flagtide';

/** What a template of `*flagtideFlag` can read: the current value of the flag. */
export interface FlagtideFlagContext {
  $implicit: JsonValue;
  flagtideFlag: JsonValue;
}

type Rendered = 'then' | 'else' | null;

/**
 * Renders its template while a flag has the expected value, and the optional `else` template otherwise.
 *
 * ```html
 * <app-promo *flagtideFlag="'promo-banner'; else plain" />
 * <ng-template #plain>...</ng-template>
 * <p *flagtideFlag="'checkout-label'; equals: 'Buy now'">...</p>
 * ```
 *
 * `equals` defaults to `true`. A flag that is unknown or of another type never matches.
 */
@Directive({ selector: '[flagtideFlag]' })
export class FlagtideFlagDirective {
  readonly flagtideFlag = input.required<string>();
  readonly flagtideFlagEquals = input<JsonValue>(true);
  readonly flagtideFlagElse = input<TemplateRef<FlagtideFlagContext> | null>(null);

  private readonly flagtide = inject(Flagtide);
  private readonly container = inject(ViewContainerRef);
  private readonly template = inject<TemplateRef<FlagtideFlagContext>>(TemplateRef);
  private rendered: Rendered = null;

  private readonly outcome = computed(
    () => {
      this.flagtide.revision();
      const expected = this.flagtideFlagEquals();
      const resolution = this.flagtide.client.resolve(this.flagtideFlag(), expected);
      const known = resolution.reason !== 'FLAG_NOT_FOUND' && resolution.reason !== 'TYPE_MISMATCH';
      return { matches: known && jsonEquals(resolution.value, expected), value: resolution.value };
    },
    { equal: (left, right) => left.matches === right.matches && jsonEquals(left.value, right.value) },
  );

  constructor() {
    effect(() => {
      const { matches, value } = this.outcome();
      const elseTemplate = this.flagtideFlagElse();
      untracked(() => this.render(matches ? 'then' : 'else', matches ? this.template : elseTemplate, value));
    });
  }

  private render(
    kind: Exclude<Rendered, null>,
    template: TemplateRef<FlagtideFlagContext> | null,
    value: JsonValue,
  ): void {
    if (this.rendered === kind && kind === 'then') {
      return;
    }
    this.container.clear();
    this.rendered = null;
    if (template !== null) {
      this.container.createEmbeddedView(template, { $implicit: value, flagtideFlag: value });
      this.rendered = kind;
    }
  }

  static ngTemplateContextGuard(
    directive: FlagtideFlagDirective,
    context: unknown,
  ): context is FlagtideFlagContext {
    return directive !== null && context !== null;
  }
}
