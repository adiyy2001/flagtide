import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';
import { injectFlag } from '@flagtide/angular';
import type { JsonValue } from '@flagtide/angular';
import { Cart, discountedTotal, shippingProgress } from '../cart';
import { parsePromo } from '../promo';
import { formatMoney, PRODUCTS } from '../products';

const DEFAULT_CHECKOUT_LABEL = 'Checkout';
const DEFAULT_THRESHOLD_EUROS = 75;

@Component({
  selector: 'shop-storefront',
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './storefront.html',
  styleUrl: './storefront.scss',
})
export class Storefront {
  readonly cart = inject(Cart);
  readonly products = PRODUCTS;
  readonly money = formatMoney;
  readonly checkoutLabel = injectFlag('checkout-label', DEFAULT_CHECKOUT_LABEL);
  readonly freeShippingThreshold = injectFlag('free-shipping-threshold', DEFAULT_THRESHOLD_EUROS);
  private readonly promoValue = injectFlag<JsonValue>('promo', null);
  private readonly promoBannerOn = injectFlag('promo-banner', false);
  readonly ordered = signal(false);

  readonly discountPercent = computed(() =>
    this.promoBannerOn() ? parsePromo(this.promoValue()).discountPercent : 0,
  );
  readonly discountCents = computed(
    () => this.cart.subtotalCents() - discountedTotal(this.cart.subtotalCents(), this.discountPercent()),
  );
  readonly totalCents = computed(() => this.cart.subtotalCents() - this.discountCents());
  readonly shipping = computed(() => shippingProgress(this.totalCents(), this.freeShippingThreshold()));

  add(productId: string): void {
    this.ordered.set(false);
    this.cart.add(productId);
  }

  placeOrder(): void {
    this.cart.clear();
    this.ordered.set(true);
  }
}
