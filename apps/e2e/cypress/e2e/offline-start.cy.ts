import { urls } from '../support/commands';

const VISITOR = 'visitor-beta';
const BANNER = '[data-testid="promo-banner"]';
const NAV = '[data-testid="nav-recommendations"]';

describe('offline start', { retries: 0 }, () => {
  before(() => {
    cy.task('startServers', null, { timeout: 180000 });
  });

  after(() => {
    cy.task('startServers', null, { timeout: 180000 });
  });

  it('renders flag-gated content from the stored snapshot while the API is unreachable', () => {
    const shop = `${urls().shopUrl}/?visitor=${VISITOR}`;
    cy.visit(shop);
    cy.contains('[role="status"]', 'Flags: Live');
    cy.get(BANNER).should('exist');
    cy.get(NAV).should('exist');

    cy.task('stopServers', null, { timeout: 120000 });

    cy.visit(shop);
    cy.get(BANNER).should('exist');
    cy.contains(BANNER, 'Spring sale');
    cy.get(NAV).should('exist');
    cy.get('[role="status"]')
      .invoke('text')
      .should('match', /Flags: (Cached flags|Offline)/);
  });
});
