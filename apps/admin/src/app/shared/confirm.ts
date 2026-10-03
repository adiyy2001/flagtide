import { Injectable, inject } from '@angular/core';
import { MatDialog } from '@angular/material/dialog';
import { firstValueFrom } from 'rxjs';
import { ConfirmDialog } from './confirm-dialog';
import type { ConfirmData } from './confirm-dialog';

@Injectable({ providedIn: 'root' })
export class Confirm {
  private readonly dialog = inject(MatDialog);

  async ask(data: ConfirmData): Promise<boolean> {
    const reference = this.dialog.open<ConfirmDialog, ConfirmData, boolean>(ConfirmDialog, {
      data,
      width: '28rem',
      autoFocus: false,
    });
    return (await firstValueFrom(reference.afterClosed())) === true;
  }
}
