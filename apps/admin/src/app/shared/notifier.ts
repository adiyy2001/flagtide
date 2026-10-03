import { Injectable, inject } from '@angular/core';
import { MatSnackBar } from '@angular/material/snack-bar';
import { describeFailure } from '../api/problem';

@Injectable({ providedIn: 'root' })
export class Notifier {
  private readonly snackBar = inject(MatSnackBar);

  success(message: string): void {
    this.snackBar.open(message, 'Dismiss', { duration: 4000 });
  }

  failure(failure: unknown): void {
    this.snackBar.open(describeFailure(failure), 'Dismiss', { duration: 10000 });
  }
}
