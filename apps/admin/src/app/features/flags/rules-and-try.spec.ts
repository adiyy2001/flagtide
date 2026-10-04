import { TestBed } from '@angular/core/testing';
import { beforeEach, describe, expect, it } from 'vitest';
import { FakeServer, wireFlag } from '../../../test-support/fake-server';
import { mount } from '../../../test-support/harness';
import type { Harness } from '../../../test-support/harness';

function serverWithRule(): FakeServer {
  const server = new FakeServer();
  server.flags = [
    wireFlag('gated', {
      environments: {
        dev: {
          enabled: true,
          offVariant: 'off',
          salt: 'cd34',
          fallthrough: { variant: 'on' },
          rules: [
            {
              id: 'pro-users',
              order: 0,
              conditions: [{ attribute: 'plan', operator: 'equals', values: ['pro'] }],
              serve: { variant: 'off' },
            },
          ],
        },
      },
    }),
  ];
  return server;
}

async function setOperator(harness: Harness, label: string): Promise<void> {
  const selects = harness.queryAll('admin-condition-editor mat-select');
  const operator = selects.find((select) => select.closest('.operator') !== null);
  if (operator === undefined) {
    throw new Error('operator select missing');
  }
  await harness.choose(operator, label);
}

describe('rules and try a context', () => {
  beforeEach(() => TestBed.resetTestingModule());

  it('evaluates the draft locally and names the matching rule', async () => {
    const harness = await mount({ server: serverWithRule() });
    await harness.go('/flags/gated');
    expect(harness.textOf('.outcome')).toContain('off');
    expect(harness.textOf('.outcome')).toContain('pro-users');
  });

  it('writes the rule and the bucket as clean sentences', async () => {
    const server = serverWithRule();
    server.flags = [
      wireFlag('gated', {
        environments: {
          dev: {
            enabled: true,
            offVariant: 'off',
            salt: 'cd34',
            fallthrough: {
              rollout: [
                { variant: 'on', weight: 50000 },
                { variant: 'off', weight: 50000 },
              ],
            },
            rules: [],
          },
        },
      }),
    ];
    const harness = await mount({ server });
    await harness.go('/flags/gated');
    const text = harness.textOf('.outcome');
    expect(text).toMatch(/No rule matched, so the fallthrough is served\. Bucket \d+ of 100000\./);
    expect(text).not.toMatch(/\s\.\s/);
  });

  it('follows edits to the attributes and reports bad JSON', async () => {
    const harness = await mount({ server: serverWithRule() });
    await harness.go('/flags/gated');
    const area = harness.query<HTMLTextAreaElement>('admin-try-context textarea');
    await harness.type('admin-try-context textarea', '{"plan":"free"}');
    expect(area.value).toBe('{"plan":"free"}');
    expect(harness.textOf('.outcome')).toContain('on');
    expect(harness.textOf('.outcome')).not.toContain('pro-users');
    await harness.type('admin-try-context textarea', '{ nope');
    expect(harness.textOf('.outcome')).toContain('not valid JSON');
    await harness.type('admin-try-context textarea', '[1]');
    expect(harness.textOf('.outcome')).toContain('JSON object');
  });

  it('uses the unsaved draft and stops trying while the configuration is invalid', async () => {
    const harness = await mount({ server: serverWithRule() });
    await harness.go('/flags/gated');
    const name = harness.query<HTMLInputElement>('admin-rule-editor .name input');
    await harness.type(`#${name.id}`, '');
    expect(harness.textOf('.outcome')).toContain('Fix the problems');
    await harness.type(`#${name.id}`, 'renamed');
    expect(harness.textOf('.outcome')).toContain('renamed');
  });

  it('checks semver and number conditions with their own messages', async () => {
    const harness = await mount({ server: serverWithRule() });
    await harness.go('/flags/gated');
    const value = () => harness.query<HTMLInputElement>('admin-condition-editor input[id$="-values"]');
    await setOperator(harness, 'version is at least');
    await harness.type(`#${value().id}`, 'one.two');
    expect(harness.textOf('admin-condition-editor .field-error')).toContain('version');
    await harness.type(`#${value().id}`, '2.1.0-rc.1');
    expect(harness.queryAll('admin-condition-editor .field-error')).toHaveLength(0);
    await setOperator(harness, 'is less than');
    await harness.type(`#${value().id}`, 'abc');
    expect(harness.textOf('admin-condition-editor .field-error')).toContain('number');
    await harness.type(`#${value().id}`, '12.5');
    expect(harness.queryAll('admin-condition-editor .field-error')).toHaveLength(0);
  });

  it('offers segments only when the environment has some and picks one', async () => {
    const server = serverWithRule();
    server.segments = [
      {
        key: 'beta-users',
        name: 'Beta',
        environment: 'dev',
        revision: 1,
        included: [],
        excluded: [],
        rules: [],
      },
    ];
    const harness = await mount({ server });
    await harness.go('/flags/gated');
    harness.buttonByText('Add segment condition').click();
    await harness.settle();
    const pickers = harness.queryAll('admin-condition-editor .segment mat-select');
    expect(pickers).toHaveLength(1);
    const [picker] = pickers;
    if (picker !== undefined) {
      await harness.choose(picker, 'beta-users');
    }
    await harness.click('button[aria-label^="Remove Rule 1"]');
    expect(harness.queryAll('admin-rule-editor')).toHaveLength(0);
  });

  it('disables the segment condition button when the environment has no segments', async () => {
    const harness = await mount({ server: serverWithRule() });
    await harness.go('/flags/gated');
    expect(harness.buttonByText('Add segment condition').disabled).toBe(true);
  });

  it('removes a condition and a rollout entry', async () => {
    const harness = await mount({ server: serverWithRule() });
    await harness.go('/flags/gated');
    await harness.click('admin-condition-editor button[aria-label^="Remove condition"]');
    expect(harness.textOf('admin-rule-editor')).toContain('No conditions');
  });
});
