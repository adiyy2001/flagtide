import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatSelectModule } from '@angular/material/select';
import { Router, RouterLink } from '@angular/router';
import type { FlagType } from '@flagtide/core';
import { AdminApi } from '../../api/admin-api';
import { ApiError, describeFailure } from '../../api/problem';
import { nextUid } from '../../domain/drafts';
import type { VariantDraft } from '../../domain/drafts';
import { defaultVariants } from '../../domain/variants';
import { describeIssue, issuesAt, validateNewFlag } from '../../domain/validation';
import { variantBodies } from '../../domain/wire';
import { Notifier } from '../../shared/notifier';
import { Workspace } from '../../state/workspace';
import { VariantsEditor } from './variants-editor';

function draftsFor(type: FlagType): VariantDraft[] {
  return defaultVariants(type).map((variant) => ({ uid: nextUid('variant'), ...variant }));
}

@Component({
  selector: 'admin-new-flag',
  imports: [MatButtonModule, MatFormFieldModule, MatInputModule, MatSelectModule, RouterLink, VariantsEditor],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './new-flag.html',
  styleUrl: './new-flag.scss',
})
export class NewFlag {
  private readonly api = inject(AdminApi);
  private readonly router = inject(Router);
  private readonly notifier = inject(Notifier);
  protected readonly workspace = inject(Workspace);

  protected readonly types: readonly FlagType[] = ['boolean', 'string', 'number', 'json'];
  protected readonly key = signal('');
  protected readonly type = signal<FlagType>('boolean');
  protected readonly description = signal('');
  protected readonly variants = signal<readonly VariantDraft[]>(draftsFor('boolean'));
  protected readonly offVariant = signal('off');
  protected readonly fallthroughVariant = signal('on');
  protected readonly saving = signal(false);
  protected readonly touched = signal(false);
  protected readonly serverIssues = signal<readonly string[]>([]);

  protected readonly issues = computed(() =>
    validateNewFlag({
      key: this.key(),
      type: this.type(),
      description: this.description(),
      variants: this.variants(),
      offVariant: this.offVariant(),
      fallthroughVariant: this.fallthroughVariant(),
    }),
  );
  protected readonly variantKeys = computed(() => this.variants().map((variant) => variant.key));
  protected readonly keyMessages = computed(() => (this.touched() ? issuesAt(this.issues(), 'key') : []));
  protected readonly lines = computed(() => this.issues().map(describeIssue));
  protected readonly via = computed(() =>
    this.workspace.selectedHasKey() ? this.workspace.selected() : null,
  );

  protected setKey(event: Event): void {
    this.touched.set(true);
    this.key.set((event.target as HTMLInputElement).value);
  }

  protected setDescription(event: Event): void {
    this.description.set((event.target as HTMLTextAreaElement).value);
  }

  protected setType(type: FlagType): void {
    const variants = draftsFor(type);
    this.type.set(type);
    this.variants.set(variants);
    this.offVariant.set(variants[1]?.key ?? variants[0]?.key ?? '');
    this.fallthroughVariant.set(variants[0]?.key ?? '');
  }

  protected async create(): Promise<void> {
    const via = this.via();
    if (via === null || this.issues().length > 0) {
      this.touched.set(true);
      return;
    }
    this.saving.set(true);
    this.serverIssues.set([]);
    try {
      const flag = await this.api.createFlag(via, {
        key: this.key(),
        type: this.type(),
        description: this.description(),
        variants: variantBodies(this.type(), this.variants()),
        offVariant: this.offVariant(),
        fallthroughVariant: this.fallthroughVariant(),
      });
      this.notifier.success(`Created ${flag.key}`);
      await this.router.navigate(['/flags', flag.key]);
    } catch (failure) {
      this.serverIssues.set([describeFailure(failure)]);
      if (!(failure instanceof ApiError)) {
        this.notifier.failure(failure);
      }
    } finally {
      this.saving.set(false);
    }
  }
}
