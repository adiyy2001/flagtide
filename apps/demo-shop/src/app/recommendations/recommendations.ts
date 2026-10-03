import { ChangeDetectionStrategy, Component } from '@angular/core';
import { RouterLink } from '@angular/router';
import { formatMoney, PRODUCTS } from '../products';

const PICKS = ['grinder', 'beans', 'scale'];

@Component({
  selector: 'shop-recommendations',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [RouterLink],
  template: `
    <h1>Recommended for you</h1>
    <p class="lead">A beta feature, shown while the flag <code>beta-recommendations</code> serves you.</p>
    <ul class="picks">
      @for (product of picks; track product.id) {
        <li>
          <strong>{{ product.name }}</strong>
          <span>{{ money(product.priceCents) }}</span>
        </li>
      }
    </ul>
    <a routerLink="/">Back to the shop</a>
  `,
  styles: `
    h1 {
      margin: 0 0 0.5rem;
      font-size: 1.75rem;
    }
    .lead {
      margin: 0 0 1rem;
      color: var(--muted);
    }
    .picks {
      display: grid;
      gap: 0.5rem;
      padding: 0;
      margin: 0 0 1.5rem;
      list-style: none;
      max-width: 28rem;
    }
    li {
      display: flex;
      justify-content: space-between;
      padding: 0.75rem 1rem;
      border: 1px solid var(--line);
      border-radius: 0.6rem;
      background: var(--surface);
    }
  `,
})
export class Recommendations {
  readonly picks = PRODUCTS.filter((product) => PICKS.includes(product.id));
  readonly money = formatMoney;
}
