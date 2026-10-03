import { ChangeDetectionStrategy, Component, computed, inject } from '@angular/core';
import { RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';
import { FlagwireFlagDirective, injectFlag } from '@flagwire/angular';
import type { JsonValue } from '@flagwire/angular';
import { FlagwireOverridesPanel } from '@flagwire/angular/overrides';
import { Cart } from './cart';
import { parsePromo } from './promo';
import { StatusBadge } from './shell/status-badge';
import { injectVisitor } from './visitor';

@Component({
  selector: 'shop-root',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    RouterLink,
    RouterLinkActive,
    RouterOutlet,
    FlagwireFlagDirective,
    FlagwireOverridesPanel,
    StatusBadge,
  ],
  templateUrl: './app.html',
  styleUrl: './app.scss',
})
export class App {
  readonly visitor = injectVisitor();
  readonly cart = inject(Cart);
  private readonly promoValue = injectFlag<JsonValue>('promo', null);
  readonly promo = computed(() => parsePromo(this.promoValue()));

  skipToContent(event: Event, main: HTMLElement): void {
    event.preventDefault();
    main.focus();
  }
}
