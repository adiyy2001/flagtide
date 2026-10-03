import { Injectable, inject } from '@angular/core';
import { Notifier } from './notifier';

@Injectable({ providedIn: 'root' })
export class Clipboard {
  private readonly notifier = inject(Notifier);

  async copy(text: string, what: string): Promise<void> {
    try {
      await navigator.clipboard.writeText(text);
      this.notifier.success(`${what} copied`);
    } catch {
      this.notifier.failure(
        new Error(`The browser blocked copying. Select the ${what} and copy it by hand.`),
      );
    }
  }
}
