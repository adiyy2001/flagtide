import { ChangeDetectionStrategy, Component } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { describe, expect, it } from 'vitest';
import {
  booleanFlag,
  deltasFrame,
  snapshotFrame,
  upsertFlag,
  variantFlag,
} from '../../../core/test-support/fixtures';
import { FakeSockets, testFlagwire } from '../../test-support/flagwire-testing';
import { injectFlag } from './inject-flag';

@Component({
  selector: 'flagwire-test-probe',
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <p id="boolean">{{ enabled() }}</p>
    <p id="label">{{ label() }}</p>
    <p id="threshold">{{ threshold() }}</p>
    <p id="promo">{{ promo().title }}</p>
  `,
})
class ProbeComponent {
  readonly enabled = injectFlag('banner', false);
  readonly label = injectFlag('label', 'Buy');
  readonly threshold = injectFlag('threshold', 50);
  readonly promo = injectFlag('promo', { title: 'none' });
}

function text(fixture: { nativeElement: HTMLElement }, id: string): string {
  return fixture.nativeElement.querySelector(`#${id}`)?.textContent ?? '';
}

function setup() {
  const sockets = new FakeSockets();
  TestBed.configureTestingModule({ providers: [testFlagwire(sockets)] });
  const fixture = TestBed.createComponent(ProbeComponent);
  return { sockets, fixture };
}

describe('injectFlag', () => {
  it('returns the fallbacks until flags arrive, then the evaluated values', async () => {
    const { sockets, fixture } = setup();
    await fixture.whenStable();
    expect(text(fixture, 'boolean')).toBe('false');
    expect(text(fixture, 'label')).toBe('Buy');
    expect(text(fixture, 'promo')).toBe('none');

    sockets.latest.open();
    sockets.latest.receive(
      snapshotFrame(1, [
        booleanFlag('banner'),
        variantFlag('label', 'string', [{ key: 'a', value: 'Buy now' }], 'a'),
        variantFlag('threshold', 'number', [{ key: 'a', value: 99 }], 'a'),
        variantFlag('promo', 'json', [{ key: 'a', value: { title: 'Autumn sale' } }], 'a'),
      ]),
    );
    await fixture.whenStable();
    expect(text(fixture, 'boolean')).toBe('true');
    expect(text(fixture, 'label')).toBe('Buy now');
    expect(text(fixture, 'threshold')).toBe('99');
    expect(text(fixture, 'promo')).toBe('Autumn sale');
  });

  it('updates the rendered value when the server pushes a delta', async () => {
    const { sockets, fixture } = setup();
    sockets.latest.open();
    sockets.latest.receive(snapshotFrame(1, [booleanFlag('banner', { enabled: false })]));
    await fixture.whenStable();
    expect(text(fixture, 'boolean')).toBe('false');
    sockets.latest.receive(deltasFrame(1, [upsertFlag(booleanFlag('banner'))]));
    await fixture.whenStable();
    expect(text(fixture, 'boolean')).toBe('true');
  });

  it('keeps the fallback when the flag has another type than the fallback', async () => {
    const { sockets, fixture } = setup();
    sockets.latest.open();
    sockets.latest.receive(snapshotFrame(1, [variantFlag('label', 'number', [{ key: 'a', value: 3 }], 'a')]));
    await fixture.whenStable();
    expect(text(fixture, 'label')).toBe('Buy');
  });

  it('keeps the same object for an unrelated change that leaves the value equal', async () => {
    const sockets = new FakeSockets();
    TestBed.configureTestingModule({ providers: [testFlagwire(sockets)] });
    const value = TestBed.runInInjectionContext(() => injectFlag('promo', { title: 'none' }));
    sockets.latest.open();
    sockets.latest.receive(
      snapshotFrame(1, [variantFlag('promo', 'json', [{ key: 'a', value: { title: 'x' } }], 'a')]),
    );
    const first = value();
    sockets.latest.receive(deltasFrame(1, [upsertFlag(booleanFlag('other'))]));
    expect(value()).toBe(first);
  });

  it('must be called in an injection context', () => {
    expect(() => injectFlag('banner', false)).toThrow();
  });
});
