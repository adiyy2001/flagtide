import type { ComponentFixture } from '@angular/core/testing';
import { TestBed } from '@angular/core/testing';
import { provideRouter, Router } from '@angular/router';
import type { FlagConfig } from '@flagwire/core';
import { snapshotFrame } from '../../../../libs/core/test-support/fixtures';
import { FakeSockets, testFlagwire } from '../../../../libs/angular/test-support/flagwire-testing';
import { App } from '../app/app';
import { routes } from '../app/app.routes';
import { toEvaluationContext, visitorFromUrl } from '../app/visitor';

export interface ShopHarness {
  readonly fixture: ComponentFixture<App>;
  readonly sockets: FakeSockets;
  readonly root: HTMLElement;
  go(url: string): Promise<void>;
  deliver(flags: readonly FlagConfig[], version?: number): Promise<void>;
  settle(): Promise<void>;
  query<T extends HTMLElement = HTMLElement>(selector: string): T;
  queryAll<T extends HTMLElement = HTMLElement>(selector: string): T[];
  has(selector: string): boolean;
  textOf(selector: string): string;
}

export async function mountShop(visitorQuery = ''): Promise<ShopHarness> {
  history.replaceState(null, '', `/${visitorQuery}`);
  const sockets = new FakeSockets();
  TestBed.configureTestingModule({
    providers: [
      provideRouter(routes),
      testFlagwire(sockets, { context: toEvaluationContext(visitorFromUrl(`/${visitorQuery}`)) }),
    ],
  });
  const fixture = TestBed.createComponent(App);
  const router = TestBed.inject(Router);
  const root = fixture.nativeElement as HTMLElement;

  const settle = async (): Promise<void> => {
    for (let round = 0; round < 6; round += 1) {
      await fixture.whenStable();
      await new Promise<void>((done) => setTimeout(done, 0));
    }
  };

  const query = <T extends HTMLElement>(selector: string): T => {
    const element = root.querySelector<T>(selector);
    if (element === null) {
      throw new Error(`nothing matches ${selector}`);
    }
    return element;
  };

  const harness: ShopHarness = {
    fixture,
    sockets,
    root,
    settle,
    query,
    queryAll: <T extends HTMLElement>(selector: string) => Array.from(root.querySelectorAll<T>(selector)),
    has: (selector) => root.querySelector(selector) !== null,
    textOf: (selector) => (query(selector).textContent ?? '').replace(/\s+/gu, ' ').trim(),
    async go(url) {
      await router.navigateByUrl(url);
      await settle();
    },
    async deliver(flags, version = 1) {
      sockets.latest.open();
      sockets.latest.receive(snapshotFrame(version, flags));
      await settle();
    },
  };
  await harness.go('/');
  return harness;
}
