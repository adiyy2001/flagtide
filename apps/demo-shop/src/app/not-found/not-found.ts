import { ChangeDetectionStrategy, Component, inject, RESPONSE_INIT } from '@angular/core';
import { RouterLink } from '@angular/router';

@Component({
  selector: 'shop-not-found',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [RouterLink],
  template: `
    <h1>Page not found</h1>
    <p>There is nothing at this address.</p>
    <a routerLink="/">Back to the shop</a>
  `,
})
export class NotFound {
  constructor() {
    const response = inject(RESPONSE_INIT, { optional: true });
    if (response !== null) {
      response.status = 404;
    }
  }
}
