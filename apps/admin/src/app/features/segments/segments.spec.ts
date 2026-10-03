import { TestBed } from '@angular/core/testing';
import { beforeEach, describe, expect, it } from 'vitest';
import { FakeServer } from '../../../test-support/fake-server';
import { mount } from '../../../test-support/harness';

function serverWithSegment(): FakeServer {
  const server = new FakeServer();
  server.segments = [
    {
      key: 'beta-users',
      name: 'Beta users',
      environment: 'dev',
      revision: 2,
      included: ['u1'],
      excluded: [],
      rules: [[{ attribute: 'plan', operator: 'equals', values: ['pro'] }]],
    },
  ];
  return server;
}

describe('segments page', () => {
  beforeEach(() => TestBed.resetTestingModule());

  it('lists segments of the selected environment and opens one', async () => {
    const harness = await mount({ server: serverWithSegment() });
    await harness.go('/segments');
    expect(harness.textOf('nav.list')).toContain('beta-users');
    await harness.click('nav.list button.item');
    expect(harness.query<HTMLInputElement>('#segment-key').value).toBe('beta-users');
    expect(harness.query<HTMLInputElement>('#segment-key').disabled).toBe(true);
    expect(harness.queryAll('.group')).toHaveLength(1);
  });

  it('creates a segment with a group and sends it with the environment key', async () => {
    const harness = await mount();
    await harness.go('/segments');
    expect(harness.textOf('nav.list')).toContain('No segments');
    harness.buttonByText('New segment').click();
    await harness.settle();
    await harness.type('#segment-key', 'testers');
    await harness.type('#segment-name', 'Testers');
    harness.buttonByText('Add group').click();
    await harness.settle();
    expect(harness.queryAll('.group').length).toBeGreaterThan(0);
    const attribute = harness.query<HTMLInputElement>('.group admin-condition-editor input');
    await harness.type(`#${attribute.id}`, 'plan');
    const values = harness
      .queryAll<HTMLInputElement>('.group input')
      .find((input) => input.id.endsWith('-values'));
    await harness.type(`#${values?.id}`, 'pro');
    harness.buttonByText('Save segment').click();
    await harness.settle();
    const [call] = harness.server.callsTo('PUT', '/segments/testers');
    expect(call?.authorization).toBe('Bearer dev-key');
    expect(call?.body).toMatchObject({ name: 'Testers' });
    expect(harness.server.segments.map((segment) => segment.key)).toEqual(['testers']);
  });

  it('shows issues for an invalid new segment and keeps Save disabled', async () => {
    const harness = await mount();
    await harness.go('/segments');
    harness.buttonByText('New segment').click();
    await harness.settle();
    await harness.type('#segment-key', 'Not A Slug');
    expect(harness.textOf('#segment-key-error')).not.toBe('');
    expect(harness.buttonByText('Save segment').disabled).toBe(true);
  });

  it('deletes a segment after confirmation', async () => {
    const harness = await mount({ server: serverWithSegment() });
    await harness.go('/segments');
    await harness.click('nav.list button.item');
    harness.buttonByText('Delete segment').click();
    await harness.settle();
    const confirm = harness
      .queryAll<HTMLButtonElement>('.mat-mdc-dialog-actions button')
      .find((button) => button.textContent?.includes('Delete'));
    confirm?.click();
    await harness.settle();
    expect(harness.server.callsTo('DELETE', '/segments/beta-users')).toHaveLength(1);
  });

  it('discards edits', async () => {
    const harness = await mount({ server: serverWithSegment() });
    await harness.go('/segments');
    await harness.click('nav.list button.item');
    await harness.type('#segment-name', 'Renamed');
    expect(harness.buttonByText('Discard changes').disabled).toBe(false);
    await harness.click('.editor .actions button:nth-last-child(2)');
    expect(harness.query<HTMLInputElement>('#segment-name').value).toBe('Beta users');
  });
});
