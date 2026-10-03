import { ChangeDetectionStrategy, Component, inject } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { MAT_DIALOG_DATA, MatDialogModule } from '@angular/material/dialog';
import type { DiffLine } from '../../domain/diff';

export interface ConflictData {
  readonly subject: string;
  readonly lines: readonly DiffLine[];
}

export type ConflictChoice = 'overwrite' | 'reload';

@Component({
  selector: 'admin-conflict-dialog',
  imports: [MatButtonModule, MatDialogModule],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <h2 mat-dialog-title>Someone else changed this flag</h2>
    <mat-dialog-content>
      <p>
        {{ data.subject }} was saved by someone else after you opened it, so your save was not applied. You
        can keep your version and save over theirs, or load theirs and drop your edits to this section.
      </p>
      @if (data.lines.length > 0) {
        <table class="diff" aria-label="Differences between the server and your draft">
          <thead>
            <tr>
              <th scope="col">Setting</th>
              <th scope="col">On the server now</th>
              <th scope="col">Your draft</th>
            </tr>
          </thead>
          <tbody>
            @for (line of data.lines; track line.path) {
              <tr>
                <th scope="row" class="mono">{{ line.path }}</th>
                <td class="mono">{{ line.before ?? 'not set' }}</td>
                <td class="mono">{{ line.after ?? 'not set' }}</td>
              </tr>
            }
          </tbody>
        </table>
      } @else {
        <p class="muted">The two versions are identical, only the revision moved on.</p>
      }
    </mat-dialog-content>
    <mat-dialog-actions align="end">
      <button mat-button type="button" mat-dialog-close>Keep editing</button>
      <button mat-stroked-button type="button" [mat-dialog-close]="'reload'">Load theirs</button>
      <button mat-flat-button type="button" [mat-dialog-close]="'overwrite'" cdkFocusInitial>
        Save mine over theirs
      </button>
    </mat-dialog-actions>
  `,
  styles: `
    .diff {
      width: 100%;
      border-collapse: collapse;
      font-size: 0.875rem;
    }

    th,
    td {
      text-align: left;
      padding: 0.25rem 0.5rem;
      border-bottom: 1px solid var(--mat-sys-outline-variant);
      vertical-align: top;
      word-break: break-word;
    }

    thead th {
      font: var(--mat-sys-label-medium);
      color: var(--mat-sys-on-surface-variant);
    }

    tbody th {
      font-weight: 500;
    }
  `,
})
export class ConflictDialog {
  protected readonly data = inject<ConflictData>(MAT_DIALOG_DATA);
}
