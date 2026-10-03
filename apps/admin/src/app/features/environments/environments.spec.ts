import { TestBed } from '@angular/core/testing';
import { beforeEach, describe, expect, it } from 'vitest';
import { mount } from '../../../test-support/harness';

describe('environments page', () => {
  beforeEach(() => TestBed.resetTestingModule());

  it('lists each environment with its SDK key and the source of its admin key', async () => {
    const harness = await mount({ adminKeys: { dev: 'dev-key' } });
    await harness.go('/environments');
    const text = harness.textOf('table');
    expect(text).toContain('sdk-dev');
    expect(text).toContain('Set in config.json');
    expect(text).toContain('Missing');
  });

  it('remembers an admin key typed for an environment and can forget it', async () => {
    const harness = await mount({ adminKeys: { dev: 'dev-key' } });
    await harness.go('/environments');
    const input = harness.queryAll<HTMLInputElement>('input[type=password]')[1];
    expect(input).toBeDefined();
    input?.focus();
    if (input !== undefined) {
      input.value = 'typed-key';
      input.dispatchEvent(new Event('input', { bubbles: true }));
    }
    await harness.settle();
    input?.closest('.key-entry')?.querySelector('button')?.click();
    await harness.settle();
    expect(harness.store.get('flagwire.admin.keys')).toContain('typed-key');
    expect(harness.textOf('table')).toContain('Saved in this browser');
    harness.buttonByText('Forget').click();
    await harness.settle();
    expect(harness.store.get('flagwire.admin.keys') ?? '').not.toContain('typed-key');
  });

  it('creates an environment, shows the issued keys once and remembers the admin key', async () => {
    const harness = await mount();
    await harness.go('/environments');
    expect(harness.buttonByText('Create environment').disabled).toBe(true);
    const inputs = harness.queryAll<HTMLInputElement>(
      'form.create input[matInput], form.create input.mat-mdc-input-element',
    );
    const [key, name] = inputs;
    await harness.type(`#${key?.id}`, 'qa');
    await harness.type(`#${name?.id}`, 'QA');
    expect(harness.buttonByText('Create environment').disabled).toBe(false);
    harness.buttonByText('Create environment').click();
    await harness.settle();
    expect(harness.textOf('.issued')).toContain('fw_admin_secret');
    expect(harness.store.get('flagwire.admin.keys')).toContain('fw_admin_secret');
    harness.buttonByText('I have copied the keys').click();
    await harness.settle();
    expect(harness.queryAll('.issued')).toHaveLength(0);
    expect(harness.textOf('table')).toContain('qa');
  });

  it('rejects a duplicate or malformed key', async () => {
    const harness = await mount();
    await harness.go('/environments');
    const [key] = harness.queryAll<HTMLInputElement>('form.create input');
    await harness.type(`#${key?.id}`, 'dev');
    expect(harness.textOf('#environment-key-error')).toContain('already exists');
    await harness.type(`#${key?.id}`, 'Bad Key');
    expect(harness.textOf('#environment-key-error')).toContain('lowercase');
  });

  it('shows the server failure when creation is refused', async () => {
    const harness = await mount();
    await harness.go('/environments');
    const [key, name] = harness.queryAll<HTMLInputElement>('form.create input');
    await harness.type(`#${key?.id}`, 'qa');
    await harness.type(`#${name?.id}`, 'QA');
    harness.server.failNext = {
      status: 403,
      problem: { title: 'Forbidden', detail: 'The key cannot create environments' },
    };
    harness.buttonByText('Create environment').click();
    await harness.settle();
    expect(harness.textOf('.issues-box')).toContain('cannot create');
  });
});
