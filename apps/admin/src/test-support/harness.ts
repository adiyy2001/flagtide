import { MATERIAL_ANIMATIONS } from '@angular/material/core';
import type { ComponentFixture } from '@angular/core/testing';
import { TestBed } from '@angular/core/testing';
import { Router, provideRouter, withComponentInputBinding } from '@angular/router';
import { createMemoryStore } from '@flagwire/core';
import type { KeyValueStore } from '@flagwire/core';
import { RUNTIME_CONFIG } from '../app/api/runtime-config';
import type { RuntimeConfig } from '../app/api/runtime-config';
import { FETCH, LOCAL_STORE } from '../app/api/tokens';
import { App } from '../app/app';
import { routes } from '../app/app.routes';
import { FakeServer } from './fake-server';

export interface Harness {
  readonly fixture: ComponentFixture<App>;
  readonly server: FakeServer;
  readonly store: KeyValueStore;
  readonly root: HTMLElement;
  go(url: string): Promise<void>;
  settle(): Promise<void>;
  tick(): Promise<void>;
  query<T extends HTMLElement = HTMLElement>(selector: string): T;
  queryAll<T extends HTMLElement = HTMLElement>(selector: string): T[];
  click(selector: string): Promise<void>;
  type(selector: string, value: string): Promise<void>;
  textOf(selector: string): string;
  buttonByText(text: string): HTMLButtonElement;
  choose(select: HTMLElement, optionText: string): Promise<void>;
}

export interface HarnessOptions {
  readonly adminKeys?: Record<string, string>;
  readonly server?: FakeServer;
  readonly store?: KeyValueStore;
}

export async function mount(options: HarnessOptions = {}): Promise<Harness> {
  const server = options.server ?? new FakeServer();
  const store = options.store ?? createMemoryStore();
  const config: RuntimeConfig = {
    apiUrl: 'http://api',
    project: 'demo',
    adminKeys: options.adminKeys ?? { dev: 'dev-key', prod: 'prod-key' },
  };
  TestBed.configureTestingModule({
    providers: [
      { provide: RUNTIME_CONFIG, useValue: config },
      { provide: FETCH, useValue: server.fetch },
      { provide: LOCAL_STORE, useValue: store },
      { provide: MATERIAL_ANIMATIONS, useValue: { animationsDisabled: true } },
      provideRouter(routes, withComponentInputBinding()),
    ],
  });
  const fixture = TestBed.createComponent(App);
  const router = TestBed.inject(Router);
  const root = fixture.nativeElement as HTMLElement;

  const settle = async (): Promise<void> => {
    for (let round = 0; round < 6; round += 1) {
      await fixture.whenStable();
      await new Promise<void>((resolve) => setTimeout(resolve, 0));
      fixture.detectChanges();
    }
  };

  const tick = async (): Promise<void> => {
    for (let round = 0; round < 4; round += 1) {
      await new Promise<void>((resolve) => setTimeout(resolve, 5));
      fixture.detectChanges();
    }
  };

  const query = <T extends HTMLElement>(selector: string): T => {
    const found = root.querySelector<T>(selector) ?? document.body.querySelector<T>(selector);
    if (found === null) {
      throw new Error(`Nothing matches ${selector}`);
    }
    return found;
  };

  const harness: Harness = {
    fixture,
    server,
    store,
    root,
    settle,
    tick,
    query,
    queryAll: <T extends HTMLElement>(selector: string) => [
      ...root.querySelectorAll<T>(selector),
      ...document.body.querySelectorAll<T>(`.cdk-overlay-container ${selector}`),
    ],
    go: async (url) => {
      await router.navigateByUrl(url);
      await settle();
    },
    click: async (selector) => {
      query(selector).click();
      await settle();
    },
    type: async (selector, value) => {
      const input = query<HTMLInputElement>(selector);
      input.value = value;
      input.dispatchEvent(new Event('input', { bubbles: true }));
      input.dispatchEvent(new Event('change', { bubbles: true }));
      await settle();
    },
    textOf: (selector) => query(selector).textContent?.replace(/\s+/gu, ' ').trim() ?? '',
    choose: async (select, optionText) => {
      select.querySelector<HTMLElement>('.mat-mdc-select-trigger')?.click();
      await settle();
      const option = [...document.body.querySelectorAll<HTMLElement>('mat-option')].find(
        (candidate) => candidate.textContent?.trim() === optionText,
      );
      if (option === undefined) {
        throw new Error(`No option says ${optionText}`);
      }
      option.click();
      await settle();
    },
    buttonByText: (text) => {
      const match = [...document.body.querySelectorAll<HTMLButtonElement>('button')].find((button) =>
        (button.textContent ?? '').replace(/\s+/gu, ' ').trim().includes(text),
      );
      if (match === undefined) {
        throw new Error(`No button says ${text}`);
      }
      return match;
    },
  };
  fixture.detectChanges();
  await settle();
  return harness;
}
