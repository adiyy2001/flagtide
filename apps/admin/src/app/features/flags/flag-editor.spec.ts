import { TestBed } from '@angular/core/testing';
import { Router } from '@angular/router';
import { createMemoryStore } from '@flagwire/core';
import { beforeEach, describe, expect, it } from 'vitest';
import { FakeServer, wireFlag } from '../../../test-support/fake-server';
import { mount } from '../../../test-support/harness';
import type { Harness } from '../../../test-support/harness';

async function saveButton(harness: Harness, text: string): Promise<HTMLButtonElement> {
  await harness.settle();
  return harness.buttonByText(text);
}

async function clickText(harness: Harness, text: string): Promise<void> {
  harness.buttonByText(text).click();
  await harness.settle();
}

async function editDescription(harness: Harness, value: string): Promise<void> {
  await harness.type('textarea', value);
}

async function openEditor(harness: Harness, key = 'checkout'): Promise<void> {
  await harness.go(`/flags/${key}`);
}

describe('flag editor', () => {
  beforeEach(() => TestBed.resetTestingModule());

  it('shows the flag with its definition and the environment tabs', async () => {
    const harness = await mount();
    await openEditor(harness);
    expect(harness.textOf('h1')).toBe('checkout');
    expect(harness.queryAll('[role=tab]').map((tab) => tab.textContent?.trim())).toEqual([
      'Development',
      'Production',
    ]);
    expect(harness.query<HTMLTextAreaElement>('textarea').value).toBe('checkout description');
  });

  it('keeps Save definition disabled until something changes, then saves with the revision', async () => {
    const harness = await mount();
    await openEditor(harness);
    expect((await saveButton(harness, 'Save definition')).disabled).toBe(true);
    await editDescription(harness, 'Checkout redesign');
    expect(harness.buttonByText('Save definition').disabled).toBe(false);
    await clickText(harness, 'Save definition');
    const [call] = harness.server.callsTo('PUT', '/flags/checkout');
    expect(call?.ifMatch).toBe('"1"');
    expect(call?.body).toMatchObject({ description: 'Checkout redesign' });
    expect(harness.buttonByText('Save definition').disabled).toBe(true);
  });

  it('discards edits to the definition', async () => {
    const harness = await mount();
    await openEditor(harness);
    await editDescription(harness, 'changed');
    await clickText(harness, 'Discard changes');
    expect(harness.query<HTMLTextAreaElement>('textarea').value).toBe('checkout description');
  });

  it('on a conflict offers the diff and saves over the other change when asked', async () => {
    const harness = await mount();
    await openEditor(harness);
    await editDescription(harness, 'mine');
    harness.server.conflictOnNextWrite = true;
    await clickText(harness, 'Save definition');
    expect(harness.textOf('.mat-mdc-dialog-container')).toContain('Someone else changed this flag');
    expect(harness.textOf('.mat-mdc-dialog-container table')).toContain('description');
    await clickText(harness, 'Save mine over theirs');
    expect(harness.server.callsTo('PUT', '/flags/checkout')).toHaveLength(2);
    expect(harness.server.flags.find((flag) => flag.key === 'checkout')?.description).toBe('mine');
  });

  it('on a conflict can load the server version and drop the draft', async () => {
    const server = new FakeServer();
    const harness = await mount({ server });
    await openEditor(harness);
    await editDescription(harness, 'mine');
    server.flags = server.flags.map((flag) =>
      flag.key === 'checkout' ? { ...flag, description: 'theirs', revision: 2 } : flag,
    );
    server.conflictOnNextWrite = true;
    await clickText(harness, 'Save definition');
    await clickText(harness, 'Load theirs');
    expect(harness.query<HTMLTextAreaElement>('textarea').value).toBe('theirs');
    expect(harness.buttonByText('Save definition').disabled).toBe(true);
  });

  it('on a conflict can keep editing', async () => {
    const harness = await mount();
    await openEditor(harness);
    await editDescription(harness, 'mine');
    harness.server.conflictOnNextWrite = true;
    await clickText(harness, 'Save definition');
    await clickText(harness, 'Keep editing');
    expect(harness.query<HTMLTextAreaElement>('textarea').value).toBe('mine');
    expect(harness.server.callsTo('PUT', '/flags/checkout')).toHaveLength(1);
  });

  it('adds a rule, edits it, and saves the environment with ordered rules', async () => {
    const harness = await mount();
    await openEditor(harness);
    await clickText(harness, 'Add rule');
    expect(harness.queryAll('admin-rule-editor')).toHaveLength(1);
    const attribute = harness.query<HTMLInputElement>('admin-condition-editor input');
    await harness.type(`#${attribute.id}`, 'plan');
    expect(harness.textOf('.save-bar')).toContain('value');
    const values = harness
      .queryAll<HTMLInputElement>('admin-condition-editor input')
      .find((input) => input.id.endsWith('-values'));
    expect(values).toBeDefined();
    await harness.type(`#${values?.id}`, 'pro');
    await clickText(harness, 'Save Development');
    const [call] = harness.server.callsTo('PUT', '/environments/dev');
    const body = call?.body as { rules: { order: number; conditions: unknown[] }[] };
    expect(body.rules).toHaveLength(1);
    expect(body.rules[0]?.order).toBe(0);
    expect(body.rules[0]?.conditions).toHaveLength(1);
  });

  it('moves rules with the buttons and keeps the order contiguous', async () => {
    const server = new FakeServer();
    server.flags = [
      wireFlag('ordered', {
        environments: {
          dev: {
            enabled: true,
            offVariant: 'off',
            salt: 'aa',
            fallthrough: { variant: 'on' },
            rules: [
              { id: 'first', order: 0, conditions: [], serve: { variant: 'on' } },
              { id: 'second', order: 1, conditions: [], serve: { variant: 'off' } },
            ],
          },
        },
      }),
    ];
    const harness = await mount({ server });
    await openEditor(harness, 'ordered');
    const names = () =>
      harness.queryAll<HTMLInputElement>('admin-rule-editor .name input').map((input) => input.value);
    expect(names()).toEqual(['first', 'second']);
    harness
      .query<HTMLButtonElement>('[aria-label="Move Rule 1 down"], [aria-label^="Move Rule 1 down"]')
      .click();
    await harness.settle();
    expect(names()).toEqual(['second', 'first']);
    expect(harness.query<HTMLButtonElement>('[aria-label^="Move Rule 1 up"]').disabled).toBe(true);
    harness.query<HTMLButtonElement>('[aria-label^="Remove Rule 1"]').click();
    await harness.settle();
    expect(names()).toEqual(['first']);
  });

  it('refuses a rollout that does not add up to 100 percent and recovers with Fill', async () => {
    const harness = await mount();
    await openEditor(harness);
    const radios = harness.queryAll<HTMLInputElement>('mat-radio-button input[value=rollout]');
    radios[radios.length - 1]?.click();
    await harness.settle();
    const percent = harness.queryAll<HTMLInputElement>('input[id$="-percent-0"]');
    const first = percent[percent.length - 1];
    expect(first).toBeDefined();
    await harness.type(`#${first?.id}`, '60');
    expect(harness.textOf('.remaining')).toBe('Over by 10%');
    expect(harness.buttonByText('Save Development').disabled).toBe(true);
    expect(harness.textOf('.save-bar .issues')).toContain('100');
    await clickText(harness, 'Fill');
    expect(harness.queryAll('.remaining').map((node) => node.textContent?.trim())).toContain(
      'Weights add up to 100%',
    );
  });

  it('engages the kill switch only after confirmation', async () => {
    const harness = await mount();
    await openEditor(harness);
    await clickText(harness, 'Engage kill switch');
    expect(harness.textOf('.mat-mdc-dialog-container')).toContain('Engage the kill switch in Development?');
    await clickText(harness, 'Cancel');
    expect(harness.server.callsTo('POST', 'kill-switch')).toHaveLength(0);
    await clickText(harness, 'Engage kill switch');
    const confirm = harness
      .queryAll<HTMLButtonElement>('.mat-mdc-dialog-actions button')
      .find((button) => button.textContent?.includes('Engage kill switch'));
    confirm?.click();
    await harness.settle();
    expect(harness.server.callsTo('POST', 'kill-switch')).toHaveLength(1);
    expect(harness.textOf('main')).toContain('Release kill switch');
  });

  it('archives a flag after confirmation and returns to the list', async () => {
    const harness = await mount();
    await openEditor(harness);
    await clickText(harness, 'Archive flag');
    const confirm = harness
      .queryAll<HTMLButtonElement>('.mat-mdc-dialog-actions button')
      .find((button) => button.textContent?.includes('Archive flag'));
    confirm?.click();
    await harness.settle();
    expect(harness.server.callsTo('POST', '/archive')).toHaveLength(1);
    expect(TestBed.inject(Router).url).toBe('/flags');
  });

  it('is read only without an admin key for the environment', async () => {
    const store = createMemoryStore();
    store.set('flagwire.admin.environment', 'prod');
    const harness = await mount({ adminKeys: { dev: 'dev-key' }, store });
    await openEditor(harness);
    expect(harness.textOf('.save-bar')).toContain('Read only');
    expect(harness.buttonByText('Engage kill switch').disabled).toBe(true);
  });

  it('asks before leaving with unsaved changes', async () => {
    const harness = await mount();
    await openEditor(harness);
    await editDescription(harness, 'unsaved');
    const router = TestBed.inject(Router);
    const leaving = router.navigateByUrl('/segments');
    await harness.tick();
    expect(harness.textOf('.mat-mdc-dialog-container')).toContain('Leave without saving?');
    harness.buttonByText('Keep editing').click();
    expect(await leaving).toBe(false);
    expect(router.url).toBe('/flags/checkout');
    const again = router.navigateByUrl('/segments');
    await harness.tick();
    harness.buttonByText('Leave and discard').click();
    expect(await again).toBe(true);
  });

  it('shows a failure when the flag does not exist', async () => {
    const harness = await mount();
    await openEditor(harness, 'missing');
    expect(harness.textOf('[role=alert]')).toContain('could not be loaded');
  });
});
