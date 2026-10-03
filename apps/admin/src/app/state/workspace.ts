import { Injectable, computed, inject, signal } from '@angular/core';
import { AdminApi } from '../api/admin-api';
import { KeyStore } from '../api/key-store';
import { describeFailure } from '../api/problem';
import { LOCAL_STORE } from '../api/tokens';
import type { EnvironmentModel, ProjectModel } from '../domain/models';

const STORAGE_KEY = 'flagwire.admin.environment';

@Injectable({ providedIn: 'root' })
export class Workspace {
  private readonly api = inject(AdminApi);
  private readonly keys = inject(KeyStore);
  private readonly storage = inject(LOCAL_STORE);

  readonly project = signal<ProjectModel | null>(null);
  readonly loadError = signal<string | null>(null);
  readonly environments = computed<readonly EnvironmentModel[]>(() => this.project()?.environments ?? []);
  private readonly chosen = signal<string | null>(this.storage.get(STORAGE_KEY));

  readonly selected = computed<string | null>(() => {
    const environments = this.environments();
    const chosen = this.chosen();
    if (chosen !== null && environments.some((environment) => environment.key === chosen)) {
      return chosen;
    }
    return environments[0]?.key ?? null;
  });

  readonly selectedHasKey = computed(() => {
    const selected = this.selected();
    return selected !== null && this.keys.keyFor(selected) !== null;
  });

  async load(): Promise<void> {
    try {
      this.project.set(await this.api.loadProject());
      this.loadError.set(null);
    } catch (failure) {
      this.loadError.set(describeFailure(failure));
    }
  }

  select(environment: string): void {
    this.chosen.set(environment);
    this.storage.set(STORAGE_KEY, environment);
  }

  environmentName(key: string): string {
    return this.environments().find((environment) => environment.key === key)?.name ?? key;
  }
}
