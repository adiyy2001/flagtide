import type { ComponentFixture } from '@angular/core/testing';
import { TestBed } from '@angular/core/testing';
import { Flagtide } from '@flagtide/angular';
import { describe, expect, it } from 'vitest';
import { booleanFlag, snapshotFrame, variantFlag } from '../../../core/test-support/fixtures';
import { FakeSockets, testFlagtide } from '../../test-support/flagtide-testing';
import { FlagtideOverridesPanel } from './overrides-panel';

async function setup() {
  const sockets = new FakeSockets();
  TestBed.configureTestingModule({ providers: [testFlagtide(sockets)] });
  const fixture = TestBed.createComponent(FlagtideOverridesPanel);
  sockets.latest.open();
  sockets.latest.receive(
    snapshotFrame(1, [
      booleanFlag('beta', { enabled: false }),
      variantFlag('label', 'string', [{ key: 'a', value: 'Buy' }], 'a'),
      variantFlag('threshold', 'number', [{ key: 'a', value: 50 }], 'a'),
      variantFlag('promo', 'json', [{ key: 'a', value: { title: 'Sale' } }], 'a'),
    ]),
  );
  await fixture.whenStable();
  return { sockets, fixture, flagtide: TestBed.inject(Flagtide) };
}

function query<T extends Element>(fixture: ComponentFixture<unknown>, selector: string): T {
  const element = fixture.nativeElement.querySelector(selector) as T | null;
  if (element === null) {
    throw new Error(`nothing matches ${selector}`);
  }
  return element;
}

async function open(fixture: ComponentFixture<unknown>): Promise<void> {
  query<HTMLButtonElement>(fixture, '.fw-toggle').click();
  await fixture.whenStable();
}

describe('FlagtideOverridesPanel', () => {
  it('is a real button that discloses the list and says so with aria-expanded and aria-controls', async () => {
    const { fixture } = await setup();
    const toggle = query<HTMLButtonElement>(fixture, '.fw-toggle');
    expect(toggle.tagName).toBe('BUTTON');
    expect(toggle.getAttribute('aria-expanded')).toBe('false');
    expect(fixture.nativeElement.querySelector('#fw-overrides-body')).toBeNull();
    await open(fixture);
    expect(toggle.getAttribute('aria-expanded')).toBe('true');
    expect(fixture.nativeElement.querySelector(`#${toggle.getAttribute('aria-controls')}`)).not.toBeNull();
  });

  it('shows the connection status as text and lists every flag with its type', async () => {
    const { fixture } = await setup();
    await open(fixture);
    expect(query(fixture, '.fw-status').textContent).toContain('live');
    const keys = [...fixture.nativeElement.querySelectorAll('.fw-key')].map(
      (element) => (element as HTMLElement).textContent,
    );
    expect(keys).toEqual(['beta', 'label', 'promo', 'threshold']);
    expect(fixture.nativeElement.textContent).toContain('boolean');
  });

  it('overrides a boolean flag through a labelled switch and offers a reset', async () => {
    const { fixture, flagtide } = await setup();
    await open(fixture);
    const control = query<HTMLInputElement>(fixture, 'input[role="switch"]');
    expect(control.getAttribute('aria-label')).toBe('Override beta');
    control.checked = true;
    control.dispatchEvent(new Event('change'));
    await fixture.whenStable();
    expect(flagtide.client.value('beta', false)).toBe(true);
    expect(flagtide.client.resolve('beta', false).reason).toBe('OVERRIDE');
    const reset = query<HTMLButtonElement>(fixture, '.fw-reset');
    expect(reset.textContent).toContain('Reset');
    expect(reset.textContent).toContain('beta');
    reset.click();
    await fixture.whenStable();
    expect(flagtide.client.value('beta', true)).toBe(false);
    expect(fixture.nativeElement.querySelector('.fw-reset')).toBeNull();
  });

  it('overrides string and number flags through text and number inputs', async () => {
    const { fixture, flagtide } = await setup();
    await open(fixture);
    const text = query<HTMLInputElement>(fixture, 'input[type="text"]');
    text.value = 'Order now';
    text.dispatchEvent(new Event('change'));
    const number = query<HTMLInputElement>(fixture, 'input[type="number"]');
    number.value = '75.5';
    number.dispatchEvent(new Event('change'));
    await fixture.whenStable();
    expect(flagtide.client.value('label', '')).toBe('Order now');
    expect(flagtide.client.value('threshold', 0)).toBe(75.5);
  });

  it('ignores a number input that is not a number', async () => {
    const { fixture, flagtide } = await setup();
    await open(fixture);
    const number = query<HTMLInputElement>(fixture, 'input[type="number"]');
    number.value = '';
    number.dispatchEvent(new Event('change'));
    await fixture.whenStable();
    expect(flagtide.client.overrides.entries().size).toBe(0);
  });

  it('overrides a json flag and flags invalid json with an alert and aria-invalid', async () => {
    const { fixture, flagtide } = await setup();
    await open(fixture);
    const area = query<HTMLTextAreaElement>(fixture, 'textarea');
    area.value = '{"title": "Winter"}';
    area.dispatchEvent(new Event('change'));
    await fixture.whenStable();
    expect(flagtide.client.value('promo', {})).toEqual({ title: 'Winter' });

    area.value = '{broken';
    area.dispatchEvent(new Event('change'));
    await fixture.whenStable();
    expect(area.getAttribute('aria-invalid')).toBe('true');
    expect(query(fixture, '[role="alert"]').textContent).toContain('Not applied');
    expect(flagtide.client.value('promo', {})).toEqual({ title: 'Winter' });

    area.value = '5';
    area.dispatchEvent(new Event('change'));
    await fixture.whenStable();
    expect(query(fixture, '[role="alert"]')).not.toBeNull();
  });

  it('counts overrides and resets them all', async () => {
    const { fixture, flagtide } = await setup();
    await open(fixture);
    flagtide.client.overrides.set('beta', true);
    flagtide.client.overrides.set('label', 'x');
    await fixture.whenStable();
    expect(query(fixture, '.fw-count-active').textContent).toContain('2 overridden');
    query<HTMLButtonElement>(fixture, '.fw-reset-all').click();
    await fixture.whenStable();
    expect(flagtide.client.overrides.entries().size).toBe(0);
    expect(query<HTMLButtonElement>(fixture, '.fw-reset-all').disabled).toBe(true);
  });

  it('says when no flags have been received', async () => {
    const sockets = new FakeSockets();
    TestBed.configureTestingModule({ providers: [testFlagtide(sockets)] });
    const fixture = TestBed.createComponent(FlagtideOverridesPanel);
    await fixture.whenStable();
    await open(fixture);
    expect(fixture.nativeElement.textContent).toContain('No flags received yet');
  });
});
