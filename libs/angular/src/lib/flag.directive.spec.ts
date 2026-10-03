import { ChangeDetectionStrategy, Component, signal } from '@angular/core';
import type { ComponentFixture } from '@angular/core/testing';
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
import { FlagwireFlagDirective } from './flag.directive';

@Component({
  selector: 'flagwire-test-host',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [FlagwireFlagDirective],
  template: `
    <p *flagwireFlag="key(); else plain" id="then">new</p>
    <ng-template #plain><p id="else">old</p></ng-template>
    <p *flagwireFlag="'label'; equals: 'Buy now'; let value" id="label">{{ value }}</p>
    <p *flagwireFlag="'missing'" id="missing">never</p>
  `,
})
class HostComponent {
  readonly key = signal('banner');
}

function present(fixture: ComponentFixture<HostComponent>, id: string): boolean {
  return fixture.nativeElement.querySelector(`#${id}`) !== null;
}

function setup() {
  const sockets = new FakeSockets();
  TestBed.configureTestingModule({ providers: [testFlagwire(sockets)] });
  const fixture = TestBed.createComponent(HostComponent);
  sockets.latest.open();
  return { sockets, fixture };
}

describe('*flagwireFlag', () => {
  it('shows the else template until the flag is on, then swaps and swaps back', async () => {
    const { sockets, fixture } = setup();
    sockets.latest.receive(snapshotFrame(1, [booleanFlag('banner', { enabled: false })]));
    await fixture.whenStable();
    expect(present(fixture, 'then')).toBe(false);
    expect(present(fixture, 'else')).toBe(true);

    sockets.latest.receive(deltasFrame(1, [upsertFlag(booleanFlag('banner'))]));
    await fixture.whenStable();
    expect(present(fixture, 'then')).toBe(true);
    expect(present(fixture, 'else')).toBe(false);

    sockets.latest.receive(deltasFrame(2, [upsertFlag(booleanFlag('banner', { killSwitch: true }))]));
    await fixture.whenStable();
    expect(present(fixture, 'then')).toBe(false);
    expect(present(fixture, 'else')).toBe(true);
  });

  it('renders for the expected non boolean value and exposes it to the template', async () => {
    const { sockets, fixture } = setup();
    sockets.latest.receive(
      snapshotFrame(1, [variantFlag('label', 'string', [{ key: 'a', value: 'Buy' }], 'a')]),
    );
    await fixture.whenStable();
    expect(present(fixture, 'label')).toBe(false);
    sockets.latest.receive(
      deltasFrame(1, [upsertFlag(variantFlag('label', 'string', [{ key: 'a', value: 'Buy now' }], 'a'))]),
    );
    await fixture.whenStable();
    expect(fixture.nativeElement.querySelector('#label')?.textContent).toBe('Buy now');
  });

  it('never renders for a flag that does not exist', async () => {
    const { sockets, fixture } = setup();
    sockets.latest.receive(snapshotFrame(1, []));
    await fixture.whenStable();
    expect(present(fixture, 'missing')).toBe(false);
  });

  it('follows a changed flag key', async () => {
    const { sockets, fixture } = setup();
    sockets.latest.receive(
      snapshotFrame(1, [booleanFlag('banner', { enabled: false }), booleanFlag('other')]),
    );
    await fixture.whenStable();
    expect(present(fixture, 'else')).toBe(true);
    fixture.componentInstance.key.set('other');
    await fixture.whenStable();
    expect(present(fixture, 'then')).toBe(true);
  });

  it('renders again for an override and removes it when the override is cleared', async () => {
    const { sockets, fixture } = setup();
    sockets.latest.receive(snapshotFrame(1, [booleanFlag('banner', { enabled: false })]));
    await fixture.whenStable();
    expect(present(fixture, 'then')).toBe(false);
    const { Flagwire } = await import('./flagwire');
    const flagwire = TestBed.inject(Flagwire);
    flagwire.client.overrides.set('banner', true);
    await fixture.whenStable();
    expect(present(fixture, 'then')).toBe(true);
    flagwire.client.overrides.clear('banner');
    await fixture.whenStable();
    expect(present(fixture, 'then')).toBe(false);
  });
});
