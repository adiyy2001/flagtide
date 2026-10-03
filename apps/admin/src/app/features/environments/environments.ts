import {
  ChangeDetectionStrategy,
  Component,
  computed,
  effect,
  inject,
  signal,
  untracked,
} from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { MatCheckboxModule } from '@angular/material/checkbox';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { AdminApi } from '../../api/admin-api';
import { KeyStore } from '../../api/key-store';
import { describeFailure } from '../../api/problem';
import { RUNTIME_CONFIG } from '../../api/runtime-config';
import type { ApiKeyModel, ProvisionedEnvironmentModel } from '../../domain/models';
import { isSlug } from '../../domain/variants';
import { Clipboard } from '../../shared/clipboard';
import { Icon } from '../../shared/icon';
import { Notifier } from '../../shared/notifier';
import { Workspace } from '../../state/workspace';

@Component({
  selector: 'admin-environments',
  imports: [Icon, MatButtonModule, MatCheckboxModule, MatFormFieldModule, MatInputModule],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './environments.html',
  styleUrl: './environments.scss',
})
export class Environments {
  private readonly api = inject(AdminApi);
  private readonly config = inject(RUNTIME_CONFIG);
  private readonly notifier = inject(Notifier);
  private readonly clipboard = inject(Clipboard);
  protected readonly keys = inject(KeyStore);
  protected readonly workspace = inject(Workspace);

  protected readonly sdkKeys = signal<Readonly<Record<string, readonly ApiKeyModel[]>>>({});
  protected readonly newKey = signal('');
  protected readonly newName = signal('');
  protected readonly creating = signal(false);
  protected readonly createError = signal<string | null>(null);
  protected readonly issued = signal<ProvisionedEnvironmentModel | null>(null);
  protected readonly rememberIssued = signal(true);
  protected readonly keyDrafts = signal<Readonly<Record<string, string>>>({});

  protected readonly keyIssue = computed(() => {
    const key = this.newKey();
    if (key === '') {
      return null;
    }
    if (!isSlug(key)) {
      return 'Use lowercase letters, digits, hyphens and underscores, up to 64';
    }
    return this.workspace.environments().some((environment) => environment.key === key)
      ? 'An environment with this key already exists'
      : null;
  });
  protected readonly canCreate = computed(
    () =>
      this.newKey() !== '' && this.newName().trim() !== '' && this.keyIssue() === null && !this.creating(),
  );
  protected readonly creatorEnvironment = computed(() =>
    this.workspace.selectedHasKey() ? this.workspace.selected() : null,
  );

  constructor() {
    effect(() => {
      const environments = this.workspace.environments();
      untracked(() => void this.loadKeys(environments.map((environment) => environment.key)));
    });
  }

  private async loadKeys(environments: readonly string[]): Promise<void> {
    const loaded: Record<string, readonly ApiKeyModel[]> = {};
    await Promise.all(
      environments.map(async (environment) => {
        try {
          loaded[environment] = (await this.api.listKeys(environment)).filter((key) => key.kind === 'sdk');
        } catch {
          loaded[environment] = [];
        }
      }),
    );
    this.sdkKeys.set(loaded);
  }

  protected adminKeySource(environment: string): 'saved' | 'config' | 'missing' {
    if (this.keys.isRemembered(environment)) {
      return 'saved';
    }
    return this.config.adminKeys[environment] === undefined ? 'missing' : 'config';
  }

  protected setText(target: 'newKey' | 'newName', event: Event): void {
    this[target].set((event.target as HTMLInputElement).value);
  }

  protected setKeyDraft(environment: string, event: Event): void {
    const value = (event.target as HTMLInputElement).value;
    this.keyDrafts.update((current) => ({ ...current, [environment]: value }));
  }

  protected keyDraft(environment: string): string {
    return this.keyDrafts()[environment] ?? '';
  }

  protected saveKey(environment: string): void {
    const value = this.keyDraft(environment).trim();
    if (value === '') {
      return;
    }
    this.keys.remember(environment, value);
    this.keyDrafts.update((current) => ({ ...current, [environment]: '' }));
    this.notifier.success(`Admin key saved in this browser for ${environment}`);
  }

  protected forgetKey(environment: string): void {
    this.keys.forget(environment);
    this.notifier.success(`Forgot the admin key saved for ${environment}`);
  }

  protected copy(text: string, what: string): void {
    void this.clipboard.copy(text, what);
  }

  protected async create(): Promise<void> {
    const via = this.creatorEnvironment();
    if (via === null || !this.canCreate()) {
      return;
    }
    this.creating.set(true);
    this.createError.set(null);
    try {
      const environment = await this.api.createEnvironment(via, this.newKey(), this.newName().trim());
      this.issued.set(environment);
      const admin = environment.keys.find((key) => key.kind === 'admin');
      if (admin !== undefined && this.rememberIssued()) {
        this.keys.remember(environment.key, admin.secret);
      }
      this.newKey.set('');
      this.newName.set('');
      await this.workspace.load();
    } catch (failure) {
      this.createError.set(describeFailure(failure));
    } finally {
      this.creating.set(false);
    }
  }

  protected dismissIssued(): void {
    this.issued.set(null);
  }
}
