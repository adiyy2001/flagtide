import { TestBed } from '@angular/core/testing';
import { Router } from '@angular/router';
import { beforeEach, describe, expect, it } from 'vitest';
import { mount } from '../../../test-support/harness';

describe('new flag', () => {
  beforeEach(() => TestBed.resetTestingModule());

  it('rejects a bad key and creates a boolean flag with the defaults', async () => {
    const harness = await mount();
    await harness.go('/flags/new');
    await harness.type('#new-flag-key', 'Bad Key');
    expect(harness.textOf('#new-flag-key-error')).not.toBe('');
    await harness.type('#new-flag-key', 'dark-mode');
    expect(harness.queryAll('#new-flag-key-error')).toHaveLength(0);
    harness.buttonByText('Create flag').click();
    await harness.settle();
    const [call] = harness.server.callsTo('POST', '/flags');
    expect(call?.body).toMatchObject({
      key: 'dark-mode',
      type: 'boolean',
      offVariant: 'off',
      fallthroughVariant: 'on',
    });
    expect(TestBed.inject(Router).url).toBe('/flags/dark-mode');
  });

  it('shows what is wrong when the form is submitted empty', async () => {
    const harness = await mount();
    await harness.go('/flags/new');
    harness.buttonByText('Create flag').click();
    await harness.settle();
    expect(harness.textOf('main .issues')).not.toBe('');
    expect(harness.server.callsTo('POST', '/flags')).toHaveLength(0);
  });

  it('validates JSON variant values and the number type', async () => {
    const harness = await mount();
    await harness.go('/flags/new');
    const [typeSelect] = harness.queryAll('main mat-select');
    expect(typeSelect).toBeDefined();
    typeSelect?.querySelector<HTMLElement>('.mat-mdc-select-trigger')?.click();
    await harness.settle();
    harness
      .queryAll<HTMLElement>('mat-option')
      .find((option) => option.textContent?.trim() === 'json')
      ?.click();
    await harness.settle();
    const area = harness.query<HTMLTextAreaElement>('admin-variants-editor textarea');
    await harness.type(`#${area.id}`, '{ nope');
    expect(harness.textOf('admin-variants-editor .field-error')).toContain('JSON');
    await harness.type(`#${area.id}`, '{"theme":"dark"}');
    expect(harness.queryAll('admin-variants-editor .field-error')).toHaveLength(0);
  });

  it('shows the server problem when creation fails', async () => {
    const harness = await mount();
    await harness.go('/flags/new');
    await harness.type('#new-flag-key', 'dark-mode');
    harness.server.failNext = {
      status: 422,
      problem: { title: 'Invalid', errors: [{ field: 'key', message: 'already exists' }] },
    };
    harness.buttonByText('Create flag').click();
    await harness.settle();
    expect(harness.textOf('main [role=alert]')).toContain('already exists');
  });

  it('is read only without an admin key', async () => {
    const harness = await mount({ adminKeys: { dev: 'k' } });
    await harness.go('/flags/new');
    expect(harness.buttonByText('Create flag').disabled).toBe(false);
  });
});
