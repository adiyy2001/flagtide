import { adminHeaders, fill, flagsUrl, getFlag, putEnabled, uniqueKey, urls } from '../support/commands';

describe('admin: flags', () => {
  beforeEach(() => {
    cy.visit(`${urls().adminUrl}/flags`);
    cy.contains('h1', 'Flags');
  });

  it('creates a flag and opens its editor', () => {
    const key = uniqueKey('e2e-new');
    cy.contains('a', 'New flag').click();
    cy.get('#new-flag-key').type(key);
    cy.contains('button', 'Create flag').click();
    cy.contains('h1', key);
    cy.location('pathname').should('eq', `/flags/${key}`);
    getFlag(key).then((flag) => {
      expect(Object.keys(flag.environments).sort()).to.deep.equal(['dev', 'prod', 'staging']);
    });
  });

  it('refuses a malformed key and shows why', () => {
    cy.contains('a', 'New flag').click();
    cy.get('#new-flag-key').type('Not A Key');
    cy.get('#new-flag-key-error').should('be.visible');
    cy.get('#new-flag-key').should('have.attr', 'aria-invalid', 'true');
  });

  it('edits a targeting rule and saves it', () => {
    const key = uniqueKey('e2e-rule');
    cy.request({
      method: 'POST',
      url: flagsUrl(),
      headers: adminHeaders(),
      body: {
        key,
        description: 'rule editing',
        type: 'boolean',
        variants: [
          { key: 'on', value: true },
          { key: 'off', value: false },
        ],
        offVariant: 'off',
        fallthroughVariant: 'off',
      },
    });
    cy.visit(`${urls().adminUrl}/flags/${key}`);
    cy.contains('The flag is off in this environment').should('be.visible');
    cy.contains('button', 'Add rule').click();
    fill('input[id$="-condition-0-attribute"]', 'country');
    fill('input[id$="-condition-0-values"]', 'PL');
    cy.contains('button', 'Save Development').click();
    cy.contains('button', 'Save Development').should('be.disabled');
    getFlag(key).then((flag) => {
      const [rule] = flag.environments['dev']?.rules ?? [];
      expect(rule?.conditions).to.have.length(1);
      expect(JSON.stringify(rule?.conditions)).to.contain('country');
    });
  });

  it('blocks saving a rollout that does not add up to 100 percent', () => {
    cy.contains('a', 'beta-recommendations').click();
    cy.contains('h1', 'beta-recommendations');
    fill('input[id$="-fallthrough-percent-0"]', '45');
    cy.contains('Over by 25%').should('be.visible');
    cy.contains('button', 'Save Development').should('be.disabled');
    cy.get('admin-environment-editor').contains('button', 'Discard changes').click();
    cy.get('input[id$="-fallthrough-percent-0"]').should('have.value', '20');
  });

  it('shows the conflict dialog when someone else saved first and keeps the server version on request', () => {
    const key = uniqueKey('e2e-conflict');
    cy.request({
      method: 'POST',
      url: flagsUrl(),
      headers: adminHeaders(),
      body: {
        key,
        description: 'conflict',
        type: 'boolean',
        variants: [
          { key: 'on', value: true },
          { key: 'off', value: false },
        ],
        offVariant: 'off',
        fallthroughVariant: 'on',
      },
    });
    cy.visit(`${urls().adminUrl}/flags/${key}`);
    cy.contains('mat-slide-toggle', 'Flag is off').find('button').click();
    cy.contains('mat-slide-toggle', 'Flag is on');
    putEnabled(key, true);
    cy.contains('button', 'Save Development').click();
    cy.contains('h2', 'Someone else changed this flag').should('be.visible');
    cy.contains('button', 'Load theirs').click();
    cy.contains('h2', 'Someone else changed this flag').should('not.exist');
    cy.contains('mat-slide-toggle', 'Flag is on');
    getFlag(key).then((flag) => expect(flag.environments['dev']?.enabled).to.equal(true));
  });

  it('lists a change in the audit log with its author and before and after', () => {
    const key = uniqueKey('e2e-audit');
    cy.request({
      method: 'POST',
      url: flagsUrl(),
      headers: adminHeaders(),
      body: {
        key,
        description: 'audit',
        type: 'boolean',
        variants: [
          { key: 'on', value: true },
          { key: 'off', value: false },
        ],
        offVariant: 'off',
        fallthroughVariant: 'on',
      },
    });
    putEnabled(key, true);
    cy.visit(`${urls().adminUrl}/audit`);
    cy.contains('mat-form-field', 'Flag or segment key').find('input').focus().type(key, { force: true });
    cy.contains('li', 'Flag toggled').should('contain', key).find('button.toggle').click();
    cy.contains('seed-dev-admin').should('be.visible');
    cy.get('table').should('contain', 'enabled');
  });
});
