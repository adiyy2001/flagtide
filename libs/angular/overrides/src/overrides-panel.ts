import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';
import { Flagtide } from '@flagtide/angular';
import { flagTypeOf } from '@flagtide/core';
import type { FlagType, JsonValue, ResolutionReason } from '@flagtide/core';

interface OverrideRow {
  readonly key: string;
  readonly type: FlagType;
  readonly value: JsonValue;
  readonly text: string;
  readonly reason: ResolutionReason;
  readonly overridden: boolean;
}

const SAMPLE_FALLBACK: Record<FlagType, JsonValue> = { boolean: false, string: '', number: 0, json: null };

function describeValue(value: JsonValue): string {
  return typeof value === 'string' ? value : JSON.stringify(value);
}

/**
 * A development panel that lists every flag the client knows and lets you override its value in this browser.
 * Overrides win over server values, are reported with the reason `OVERRIDE` and are kept in browser storage.
 * Place it once, near the root of the application, for example behind a development check.
 *
 * ```html
 * <flagtide-overrides-panel />
 * ```
 */
@Component({
  selector: 'flagtide-overrides-panel',
  changeDetection: ChangeDetectionStrategy.OnPush,
  host: { class: 'fw-host' },
  template: `
    <section class="fw-panel" aria-label="Flag overrides">
      <button
        type="button"
        class="fw-toggle"
        [attr.aria-expanded]="open()"
        aria-controls="fw-overrides-body"
        (click)="open.set(!open())"
      >
        <span>Flags</span>
        <span class="fw-count">{{ rows().length }}</span>
        @if (overriddenCount() > 0) {
          <span class="fw-count fw-count-active">{{ overriddenCount() }} overridden</span>
        }
        <span class="fw-status" [attr.data-status]="status()">Connection: {{ status() }}</span>
      </button>
      @if (open()) {
        <div id="fw-overrides-body" class="fw-body">
          @if (rows().length === 0) {
            <p class="fw-empty">No flags received yet.</p>
          } @else {
            <ul class="fw-list">
              @for (row of rows(); track row.key) {
                <li class="fw-row" [class.fw-overridden]="row.overridden">
                  <div class="fw-meta">
                    <code class="fw-key">{{ row.key }}</code>
                    <span class="fw-type">{{ row.type }}</span>
                    <span class="fw-reason">{{ row.reason }}</span>
                  </div>
                  <div class="fw-control">
                    @switch (row.type) {
                      @case ('boolean') {
                        <label class="fw-switch">
                          <input
                            type="checkbox"
                            role="switch"
                            [checked]="row.value === true"
                            [attr.aria-label]="'Override ' + row.key"
                            (change)="setBoolean(row.key, $event)"
                          />
                          <span>{{ row.value === true ? 'on' : 'off' }}</span>
                        </label>
                      }
                      @case ('string') {
                        <input
                          class="fw-input"
                          type="text"
                          [value]="row.text"
                          [attr.aria-label]="'Override ' + row.key"
                          (change)="setString(row.key, $event)"
                        />
                      }
                      @case ('number') {
                        <input
                          class="fw-input"
                          type="number"
                          step="any"
                          [value]="row.text"
                          [attr.aria-label]="'Override ' + row.key"
                          (change)="setNumber(row.key, $event)"
                        />
                      }
                      @default {
                        <textarea
                          class="fw-input fw-json"
                          rows="3"
                          spellcheck="false"
                          [value]="row.text"
                          [attr.aria-label]="'Override ' + row.key + ' as JSON'"
                          [attr.aria-invalid]="invalidJson() === row.key"
                          (change)="setJson(row.key, $event)"
                        ></textarea>
                        @if (invalidJson() === row.key) {
                          <span class="fw-error" role="alert"
                            >Not applied. Enter valid JSON: an object, an array or null.</span
                          >
                        }
                      }
                    }
                    @if (row.overridden) {
                      <button type="button" class="fw-reset" (click)="clear(row.key)">
                        Reset<span class="fw-sr"> {{ row.key }}</span>
                      </button>
                    }
                  </div>
                </li>
              }
            </ul>
            <button
              type="button"
              class="fw-reset-all"
              [disabled]="overriddenCount() === 0"
              (click)="clearAll()"
            >
              Reset all overrides
            </button>
          }
        </div>
      }
    </section>
  `,
  styles: `
    :host {
      position: fixed;
      right: 16px;
      bottom: 16px;
      z-index: 2147483000;
      font:
        14px/1.4 system-ui,
        sans-serif;
      color: #f3f5f8;
    }
    .fw-panel {
      width: min(420px, calc(100vw - 32px));
      background: #1b1f27;
      border: 1px solid #4a5263;
      border-radius: 10px;
      box-shadow: 0 8px 28px rgb(0 0 0 / 35%);
    }
    .fw-toggle {
      display: flex;
      flex-wrap: wrap;
      gap: 8px;
      align-items: center;
      width: 100%;
      padding: 10px 12px;
      font: inherit;
      color: inherit;
      text-align: left;
      background: transparent;
      border: 0;
      cursor: pointer;
    }
    .fw-count {
      padding: 0 8px;
      background: #3a4254;
      border-radius: 10px;
    }
    .fw-count-active {
      color: #1b1f27;
      background: #ffd24a;
    }
    .fw-status {
      margin-left: auto;
    }
    .fw-status::before {
      display: inline-block;
      width: 8px;
      height: 8px;
      margin-right: 6px;
      content: '';
      background: #9aa3b5;
      border-radius: 50%;
    }
    .fw-status[data-status='live']::before {
      background: #46d37c;
    }
    .fw-status[data-status='stale']::before {
      background: #ffd24a;
      border-radius: 2px;
    }
    .fw-status[data-status='offline']::before {
      background: #ff7a7a;
      border-radius: 0;
    }
    .fw-body {
      max-height: min(60vh, 520px);
      padding: 0 12px 12px;
      overflow: auto;
      border-top: 1px solid #4a5263;
    }
    .fw-list {
      padding: 0;
      margin: 8px 0;
      list-style: none;
    }
    .fw-row {
      padding: 8px 0;
      border-bottom: 1px solid #333a49;
    }
    .fw-overridden {
      border-left: 3px solid #ffd24a;
      padding-left: 8px;
    }
    .fw-meta {
      display: flex;
      flex-wrap: wrap;
      gap: 8px;
      align-items: baseline;
    }
    .fw-key {
      font-weight: 600;
    }
    .fw-type,
    .fw-reason {
      color: #b6bdcc;
      font-size: 12px;
    }
    .fw-control {
      display: flex;
      flex-wrap: wrap;
      gap: 8px;
      align-items: center;
      margin-top: 6px;
    }
    .fw-switch {
      display: inline-flex;
      gap: 8px;
      align-items: center;
    }
    .fw-input {
      flex: 1 1 160px;
      min-width: 0;
      padding: 6px 8px;
      font: inherit;
      color: #f3f5f8;
      background: #11141a;
      border: 1px solid #6b7488;
      border-radius: 6px;
    }
    .fw-json {
      font-family: ui-monospace, monospace;
    }
    .fw-error {
      flex-basis: 100%;
      color: #ffb3b3;
    }
    button {
      font: inherit;
    }
    .fw-reset,
    .fw-reset-all {
      padding: 4px 10px;
      color: #f3f5f8;
      background: #2c3342;
      border: 1px solid #6b7488;
      border-radius: 6px;
      cursor: pointer;
    }
    .fw-reset-all:disabled {
      color: #8b94a8;
      cursor: default;
    }
    .fw-empty {
      margin: 12px 0 0;
      color: #b6bdcc;
    }
    .fw-sr {
      position: absolute;
      width: 1px;
      height: 1px;
      overflow: hidden;
      clip-path: inset(50%);
      white-space: nowrap;
    }
    button:focus-visible,
    input:focus-visible,
    textarea:focus-visible {
      outline: 3px solid #ffd24a;
      outline-offset: 2px;
    }
  `,
})
export class FlagtideOverridesPanel {
  protected readonly open = signal(false);
  protected readonly invalidJson = signal<string | null>(null);

  private readonly flagtide = inject(Flagtide);

  protected readonly status = this.flagtide.status;

  protected readonly rows = computed<readonly OverrideRow[]>(() => {
    this.flagtide.revision();
    const client = this.flagtide.client;
    return client
      .flagKeys()
      .map((key) => {
        const type = client.flagType(key) ?? 'json';
        const resolution = client.resolve(key, SAMPLE_FALLBACK[type]);
        return {
          key,
          type,
          value: resolution.value,
          text: describeValue(resolution.value),
          reason: resolution.reason,
          overridden: client.overrides.get(key) !== undefined,
        };
      })
      .sort((left, right) => left.key.localeCompare(right.key));
  });

  protected readonly overriddenCount = computed(() => this.rows().filter((row) => row.overridden).length);

  protected setBoolean(key: string, event: Event): void {
    this.apply(key, (event.target as HTMLInputElement).checked);
  }

  protected setString(key: string, event: Event): void {
    this.apply(key, (event.target as HTMLInputElement).value);
  }

  protected setNumber(key: string, event: Event): void {
    const raw = (event.target as HTMLInputElement).value.trim();
    const parsed = Number(raw);
    if (raw !== '' && Number.isFinite(parsed)) {
      this.apply(key, parsed);
    }
  }

  protected setJson(key: string, event: Event): void {
    let parsed: JsonValue;
    try {
      parsed = JSON.parse((event.target as HTMLTextAreaElement).value) as JsonValue;
    } catch {
      this.invalidJson.set(key);
      return;
    }
    if (!this.apply(key, parsed)) {
      this.invalidJson.set(key);
    }
  }

  protected clear(key: string): void {
    this.flagtide.client.overrides.clear(key);
  }

  protected clearAll(): void {
    this.flagtide.client.overrides.clearAll();
  }

  private apply(key: string, value: JsonValue): boolean {
    this.invalidJson.set(null);
    if (flagTypeOf(value) !== this.flagtide.client.flagType(key)) {
      return false;
    }
    this.flagtide.client.overrides.set(key, value);
    return true;
  }
}
