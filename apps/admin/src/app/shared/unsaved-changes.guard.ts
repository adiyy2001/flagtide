import { inject } from '@angular/core';
import type { CanDeactivateFn } from '@angular/router';
import { Confirm } from './confirm';

export interface HasUnsavedChanges {
  hasUnsavedChanges(): boolean;
}

export const unsavedChangesGuard: CanDeactivateFn<HasUnsavedChanges> = (component) =>
  !component.hasUnsavedChanges() ||
  inject(Confirm).ask({
    title: 'Leave without saving?',
    message: 'This flag has unsaved changes. If you leave, they are lost.',
    confirmLabel: 'Leave and discard',
    cancelLabel: 'Keep editing',
  });
