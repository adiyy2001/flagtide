import { urls } from '../support/commands';

const BETA_VISITOR = 'visitor-beta';
const ORDINARY_VISITOR = 'visitor-ordinary';

function shopHtml(visitor: string): Cypress.Chainable<string> {
  return cy
    .request({ url: `${urls().shopUrl}/?visitor=${visitor}`, headers: { Accept: 'text/html' } })
    .then((response) => {
      expect(response.status).to.equal(200);
      expect(response.headers['content-type']).to.contain('text/html');
      return response.body as string;
    });
}

describe('server rendering', () => {
  it('returns HTML that already contains the flag-gated content', () => {
    shopHtml(BETA_VISITOR).then((html) => {
      expect(html).to.contain('data-testid="promo-banner"');
      expect(html).to.contain('Spring sale: 10% off brewing gear');
      expect(html).to.contain('data-testid="nav-recommendations"');
    });
  });

  it('renders different content for a visitor outside the beta segment', () => {
    shopHtml(ORDINARY_VISITOR).then((html) => {
      expect(html).to.contain('data-testid="promo-banner"');
      expect(html).not.to.contain('data-testid="nav-recommendations"');
    });
  });

  it('hands the snapshot to the browser through the transfer state', () => {
    shopHtml(BETA_VISITOR).then((html) => {
      expect(html).to.match(/<script id="ng-state" type="application\/json">/);
      expect(html).to.contain('"beta-recommendations"');
    });
  });

  it('answers unknown paths with a 404 page', () => {
    cy.request({ url: `${urls().shopUrl}/no-such-page`, failOnStatusCode: false }).then((response) => {
      expect(response.status).to.equal(404);
    });
  });
});
