import {
  ChangeDetectionStrategy,
  Component,
  HostListener,
  computed,
  effect,
  inject,
  input,
  signal,
  untracked,
} from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { MatDialog } from '@angular/material/dialog';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatTabsModule } from '@angular/material/tabs';
import { Router, RouterLink } from '@angular/router';
import { firstValueFrom } from 'rxjs';
import { AdminApi } from '../../api/admin-api';
import { KeyStore } from '../../api/key-store';
import { ApiError, describeFailure } from '../../api/problem';
import { diffJson } from '../../domain/diff';
import type { JsonValue } from '@flagtide/core';
import {
  draftDefinitionSnapshot,
  draftEnvironmentSnapshot,
  savedDefinitionSnapshot,
  savedEnvironmentSnapshot,
} from '../../domain/conflict';
import type { DefinitionDraft } from '../../domain/drafts';
import type { FlagModel, SegmentModel } from '../../domain/models';
import { describeIssue } from '../../domain/validation';
import { environmentSettingsBody, variantBodies } from '../../domain/wire';
import { Confirm } from '../../shared/confirm';
import { Notifier } from '../../shared/notifier';
import type { HasUnsavedChanges } from '../../shared/unsaved-changes.guard';
import { FlagEditorState, environmentSection } from '../../state/flag-editor-state';
import type { Section } from '../../state/flag-editor-state';
import { Workspace } from '../../state/workspace';
import { ConflictDialog } from './conflict-dialog';
import type { ConflictChoice, ConflictData } from './conflict-dialog';
import { EnvironmentEditor } from './environment-editor';
import { VariantsEditor } from './variants-editor';

@Component({
  selector: 'admin-flag-editor',
  imports: [
    EnvironmentEditor,
    MatButtonModule,
    MatFormFieldModule,
    MatInputModule,
    MatTabsModule,
    RouterLink,
    VariantsEditor,
  ],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './flag-editor.html',
  styleUrl: './flag-editor.scss',
})
export class FlagEditor implements HasUnsavedChanges {
  readonly key = input.required<string>();

  private readonly api = inject(AdminApi);
  private readonly keys = inject(KeyStore);
  private readonly dialog = inject(MatDialog);
  private readonly confirm = inject(Confirm);
  private readonly notifier = inject(Notifier);
  private readonly router = inject(Router);
  protected readonly workspace = inject(Workspace);

  protected readonly state = signal<FlagEditorState | null>(null);
  protected readonly segments = signal<Readonly<Record<string, readonly SegmentModel[]>>>({});
  protected readonly loading = signal(true);
  protected readonly failure = signal<string | null>(null);
  protected readonly saving = signal<Section | null>(null);

  protected readonly environments = this.workspace.environments;
  protected readonly selectedIndex = computed(() => {
    const selected = this.workspace.selected();
    return Math.max(
      0,
      this.environments().findIndex((environment) => environment.key === selected),
    );
  });
  protected readonly definitionKeyEnvironment = computed(() => {
    const selected = this.workspace.selected();
    if (selected !== null && this.keys.keyFor(selected) !== null) {
      return selected;
    }
    return this.environments().find((environment) => this.keys.keyFor(environment.key) !== null)?.key ?? null;
  });
  protected readonly definitionLines = computed(
    () => this.state()?.definitionIssues().map(describeIssue) ?? [],
  );

  constructor() {
    effect(() => {
      const key = this.key();
      this.workspace.project();
      untracked(() => void this.load(key));
    });
  }

  private leaving = false;

  hasUnsavedChanges(): boolean {
    return !this.leaving && (this.state()?.dirty() ?? false);
  }

  @HostListener('window:beforeunload', ['$event'])
  protected warnBeforeUnload(event: BeforeUnloadEvent): void {
    if (this.hasUnsavedChanges()) {
      event.preventDefault();
    }
  }

  protected canWrite(environment: string): boolean {
    return this.keys.keyFor(environment) !== null;
  }

  protected segmentsOf(environment: string): readonly SegmentModel[] {
    return this.segments()[environment] ?? [];
  }

  protected selectTab(index: number): void {
    const environment = this.environments()[index];
    if (environment !== undefined) {
      this.workspace.select(environment.key);
    }
  }

  protected setDescription(state: FlagEditorState, event: Event): void {
    state.setDefinition({ ...state.definition(), description: (event.target as HTMLTextAreaElement).value });
  }

  protected setVariants(state: FlagEditorState, variants: DefinitionDraft['variants']): void {
    state.setDefinition({ ...state.definition(), variants });
  }

  protected async load(key: string): Promise<void> {
    this.loading.set(true);
    try {
      const flag = await this.api.getFlag(key);
      const state = new FlagEditorState(flag);
      this.state.set(state);
      this.failure.set(null);
      await this.loadSegments(state);
    } catch (failure) {
      this.state.set(null);
      this.failure.set(describeFailure(failure));
    } finally {
      this.loading.set(false);
    }
  }

  private async loadSegments(state: FlagEditorState): Promise<void> {
    const loaded: Record<string, readonly SegmentModel[]> = {};
    await Promise.all(
      this.environments().map(async (environment) => {
        try {
          const segments = await this.api.listSegments(environment.key);
          loaded[environment.key] = segments;
          state.setSegmentKeys(
            environment.key,
            segments.map((segment) => segment.key),
          );
        } catch {
          loaded[environment.key] = [];
        }
      }),
    );
    this.segments.set(loaded);
  }

  protected async saveDefinition(state: FlagEditorState): Promise<void> {
    const via = this.definitionKeyEnvironment();
    if (via === null) {
      this.notifier.failure(new Error('No admin key is set. Add one on the Environments page.'));
      return;
    }
    await this.attempt(state, 'definition', async () => {
      const flag = state.flag();
      const draft = state.definition();
      return this.api.updateDefinition(
        via,
        flag.key,
        { description: draft.description, variants: variantBodies(flag.type, draft.variants) },
        flag.revision,
      );
    });
  }

  protected async saveEnvironment(state: FlagEditorState, environment: string): Promise<void> {
    const section = environmentSection(environment);
    await this.attempt(state, section, async () => {
      const draft = state.environments()[environment];
      if (draft === undefined) {
        throw new Error(`There is nothing to save for ${environment}`);
      }
      return this.api.configureEnvironment(
        environment,
        state.flag().key,
        environmentSettingsBody(draft),
        state.flag().revision,
      );
    });
  }

  protected sectionOf(environment: string): Section {
    return environmentSection(environment);
  }

  protected discard(state: FlagEditorState, section: Section): void {
    state.discard(section);
  }

  protected async toggleKillSwitch(
    state: FlagEditorState,
    environment: string,
    engage: boolean,
  ): Promise<void> {
    const name = this.workspace.environmentName(environment);
    const confirmed = await this.confirm.ask({
      title: engage ? `Engage the kill switch in ${name}?` : `Release the kill switch in ${name}?`,
      message: engage
        ? `Every client in ${name} gets the off variant of ${state.flag().key} right away, whatever the rules say.`
        : `Clients in ${name} go back to the normal rules of ${state.flag().key}.`,
      confirmLabel: engage ? 'Engage kill switch' : 'Release kill switch',
      danger: engage,
    });
    if (!confirmed) {
      return;
    }
    try {
      const saved = await this.api.setKillSwitch(environment, state.flag().key, engage);
      state.accept(saved);
      this.notifier.success(`Kill switch ${engage ? 'engaged' : 'released'} in ${name}`);
    } catch (failure) {
      this.notifier.failure(failure);
    }
  }

  protected async archive(state: FlagEditorState): Promise<void> {
    const via = this.definitionKeyEnvironment();
    if (via === null) {
      this.notifier.failure(new Error('No admin key is set. Add one on the Environments page.'));
      return;
    }
    const flag = state.flag();
    const confirmed = await this.confirm.ask({
      title: `Archive ${flag.key}?`,
      message:
        'An archived flag stops being served to clients and disappears from the default list. Its history stays in the audit log.',
      confirmLabel: 'Archive flag',
      danger: true,
    });
    if (!confirmed) {
      return;
    }
    try {
      await this.api.archiveFlag(via, flag.key, flag.revision);
      this.notifier.success(`${flag.key} is archived`);
      this.leaving = true;
      await this.router.navigate(['/flags']);
    } catch (failure) {
      this.notifier.failure(failure);
    }
  }

  private async attempt(
    state: FlagEditorState,
    section: Section,
    save: () => Promise<FlagModel>,
  ): Promise<void> {
    this.saving.set(section);
    try {
      const saved = await save();
      state.accept(saved, section);
      this.notifier.success(section === 'definition' ? 'Definition saved' : 'Configuration saved');
    } catch (failure) {
      if (failure instanceof ApiError && failure.isConflict) {
        await this.resolveConflict(state, section, save);
      } else {
        this.notifier.failure(failure);
      }
    } finally {
      this.saving.set(null);
    }
  }

  private async resolveConflict(
    state: FlagEditorState,
    section: Section,
    save: () => Promise<FlagModel>,
  ): Promise<void> {
    let latest: FlagModel;
    try {
      latest = await this.api.getFlag(state.flag().key);
    } catch (failure) {
      this.notifier.failure(failure);
      return;
    }
    const choice = await this.askAboutConflict(state, section, latest);
    if (choice === 'reload') {
      state.accept(latest, section);
      this.notifier.success('Loaded the version from the server');
      return;
    }
    if (choice === 'overwrite') {
      state.accept(latest);
      try {
        state.accept(await save(), section);
        this.notifier.success('Saved over the other change');
      } catch (failure) {
        this.notifier.failure(failure);
      }
    }
  }

  private async askAboutConflict(
    state: FlagEditorState,
    section: Section,
    latest: FlagModel,
  ): Promise<ConflictChoice | undefined> {
    const data: ConflictData = {
      subject:
        section === 'definition'
          ? 'The definition'
          : `The ${this.workspace.environmentName(section.slice('environment:'.length))} configuration`,
      lines: diffJson(this.theirs(section, latest), this.mine(state, section)),
    };
    const reference = this.dialog.open<ConflictDialog, ConflictData, ConflictChoice>(ConflictDialog, {
      data,
      width: '44rem',
      maxWidth: '95vw',
      autoFocus: false,
    });
    return firstValueFrom(reference.afterClosed());
  }

  private mine(state: FlagEditorState, section: Section): JsonValue {
    if (section === 'definition') {
      return draftDefinitionSnapshot(state.flag(), state.definition());
    }
    return draftEnvironmentSnapshot(state.environments()[section.slice('environment:'.length)]);
  }

  private theirs(section: Section, latest: FlagModel): JsonValue {
    if (section === 'definition') {
      return savedDefinitionSnapshot(latest);
    }
    return savedEnvironmentSnapshot(latest.environments[section.slice('environment:'.length)]);
  }
}
