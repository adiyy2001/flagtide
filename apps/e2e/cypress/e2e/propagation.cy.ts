import { frameBody, putEnabled, urls } from '../support/commands';

const BANNER_SELECTOR = '[data-testid="promo-banner"]';
const BUDGET_MS = 1000;
const TOGGLE = 'button[role="switch"]';

type ObservedFrame = Window &
  typeof globalThis & {
    flagwireSeenAt?: number;
  };

function adminToggle(): Cypress.Chainable<JQuery<HTMLElement>> {
  return frameBody('#admin').contains('tr', 'promo-banner').find(TOGGLE);
}

function shopWindow(): Cypress.Chainable<ObservedFrame> {
  return cy.get('#shop').its('0.contentWindow') as unknown as Cypress.Chainable<ObservedFrame>;
}

function watchBanner(shouldBePresent: boolean): void {
  shopWindow().then((win) => {
    delete win.flagwireSeenAt;
    const check = (): boolean => {
      const present = win.document.querySelector(BANNER_SELECTOR) !== null;
      if (present !== shouldBePresent) {
        return false;
      }
      win.flagwireSeenAt = Date.now();
      return true;
    };
    const observer = new win.MutationObserver(() => {
      if (check()) {
        observer.disconnect();
      }
    });
    observer.observe(win.document.body, { childList: true, subtree: true });
  });
}

function clickAndMeasure(label: string): void {
  let clickedAt = 0;
  adminToggle().then(($toggle) => {
    clickedAt = Date.now();
    $toggle[0]?.click();
  });
  shopWindow()
    .its('flagwireSeenAt', { timeout: BUDGET_MS * 3 })
    .should('be.a', 'number')
    .then((seenAt) => {
      const elapsed = seenAt - clickedAt;
      cy.task(
        'log',
        `propagation ${label}: ${elapsed} ms from click in the admin to the DOM change in the shop`,
      );
      cy.log(`propagation ${label}: ${elapsed} ms`);
      expect(elapsed).to.be.lessThan(BUDGET_MS);
    });
}

describe('propagation: admin on server-a, shop on server-b', { retries: 0 }, () => {
  beforeEach(() => {
    putEnabled('promo-banner', true);
    cy.visit(urls().harnessUrl);
    frameBody('#shop').find(BANNER_SELECTOR).should('exist');
    frameBody('#shop').contains('[role="status"]', 'Flags: Live');
    adminToggle().should('have.attr', 'aria-checked', 'true');
  });

  afterEach(() => {
    putEnabled('promo-banner', true);
  });

  it('removes the banner from the shop within one second of switching the flag off in the admin', () => {
    watchBanner(false);
    clickAndMeasure('off');
  });

  it('brings the banner back within one second of switching the flag on again', () => {
    watchBanner(false);
    clickAndMeasure('off');
    adminToggle().should('have.attr', 'aria-checked', 'false');
    watchBanner(true);
    clickAndMeasure('on');
  });
});
