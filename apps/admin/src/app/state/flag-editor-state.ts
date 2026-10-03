import { computed, signal } from '@angular/core';
import type { WritableSignal } from '@angular/core';
import type { FlagModel } from '../domain/models';
import {
  definitionDraftOf,
  environmentDraftOf,
  serializeDefinition,
  serializeEnvironment,
} from '../domain/drafts';
import type { DefinitionDraft, EnvironmentDraft } from '../domain/drafts';
import { validateDefinition, validateEnvironment } from '../domain/validation';
import type { EnvironmentContext } from '../domain/validation';

export type Section = 'definition' | `environment:${string}`;

export function environmentSection(environment: string): Section {
  return `environment:${environment}`;
}

export function usedVariantKeys(flag: FlagModel): Map<string, string> {
  const used = new Map<string, string>();
  for (const [environment, config] of Object.entries(flag.environments)) {
    const keys = [
      config.offVariant,
      ...serveVariants(config.fallthrough),
      ...config.rules.flatMap((rule) => serveVariants(rule.serve)),
    ];
    keys.forEach((key) => {
      if (!used.has(key)) {
        used.set(key, environment);
      }
    });
  }
  return used;
}

function serveVariants(serve: FlagModel['environments'][string]['fallthrough']): string[] {
  return 'rollout' in serve ? serve.rollout.map((entry) => entry.variant) : [serve.variant];
}

export class FlagEditorState {
  readonly flag: WritableSignal<FlagModel>;
  readonly definition = signal<DefinitionDraft>({ description: '', variants: [] });
  readonly environments = signal<Readonly<Record<string, EnvironmentDraft>>>({});
  readonly segmentKeys = signal<Readonly<Record<string, readonly string[]>>>({});

  private readonly definitionBaseline = signal('');
  private readonly environmentBaselines = signal<Readonly<Record<string, string>>>({});

  readonly definitionDirty = computed(
    () => serializeDefinition(this.definition()) !== this.definitionBaseline(),
  );

  readonly dirtyEnvironments = computed(() => {
    const baselines = this.environmentBaselines();
    return Object.entries(this.environments())
      .filter(([environment, draft]) => serializeEnvironment(draft) !== baselines[environment])
      .map(([environment]) => environment);
  });

  readonly dirty = computed(() => this.definitionDirty() || this.dirtyEnvironments().length > 0);

  readonly variantKeys = computed(() => this.flag().variants.map((variant) => variant.key));

  readonly definitionIssues = computed(() =>
    validateDefinition(this.flag().type, this.definition(), usedVariantKeys(this.flag())),
  );

  readonly environmentIssues = computed(() => {
    const keys = this.variantKeys();
    const segments = this.segmentKeys();
    return Object.fromEntries(
      Object.entries(this.environments()).map(([environment, draft]) => [
        environment,
        validateEnvironment(draft, { variantKeys: keys, segmentKeys: segments[environment] ?? [] }),
      ]),
    );
  });

  constructor(initial: FlagModel) {
    this.flag = signal(initial);
    this.replaceAll(initial);
  }

  context(environment: string): EnvironmentContext {
    return {
      variantKeys: this.variantKeys(),
      segmentKeys: this.segmentKeys()[environment] ?? [],
    };
  }

  isDirty(section: Section): boolean {
    if (section === 'definition') {
      return this.definitionDirty();
    }
    return this.dirtyEnvironments().includes(section.slice('environment:'.length));
  }

  setDefinition(draft: DefinitionDraft): void {
    this.definition.set(draft);
  }

  setEnvironment(environment: string, draft: EnvironmentDraft): void {
    this.environments.update((current) => ({ ...current, [environment]: draft }));
  }

  setSegmentKeys(environment: string, keys: readonly string[]): void {
    this.segmentKeys.update((current) => ({ ...current, [environment]: keys }));
  }

  discard(section: Section): void {
    const flag = this.flag();
    if (section === 'definition') {
      this.definition.set(definitionDraftOf(flag));
      return;
    }
    const environment = section.slice('environment:'.length);
    const config = flag.environments[environment];
    if (config !== undefined) {
      this.setEnvironment(environment, environmentDraftOf(config));
    }
  }

  accept(next: FlagModel, saved: Section | null = null): void {
    const definitionWasDirty = this.definitionDirty() && saved !== 'definition';
    const dirtyEnvironments = new Set(
      this.dirtyEnvironments().filter((environment) => environmentSection(environment) !== saved),
    );
    const currentDefinition = this.definition();
    const currentEnvironments = this.environments();
    this.flag.set(next);
    this.replaceAll(next);
    if (definitionWasDirty) {
      this.definition.set(currentDefinition);
    }
    dirtyEnvironments.forEach((environment) => {
      const draft = currentEnvironments[environment];
      if (draft !== undefined) {
        this.setEnvironment(environment, draft);
      }
    });
  }

  private replaceAll(flag: FlagModel): void {
    const definition = definitionDraftOf(flag);
    this.definition.set(definition);
    this.definitionBaseline.set(serializeDefinition(definition));
    const drafts: Record<string, EnvironmentDraft> = {};
    const baselines: Record<string, string> = {};
    for (const [environment, config] of Object.entries(flag.environments)) {
      const draft = environmentDraftOf(config);
      drafts[environment] = draft;
      baselines[environment] = serializeEnvironment(draft);
    }
    this.environments.set(drafts);
    this.environmentBaselines.set(baselines);
  }
}
