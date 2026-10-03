import {
  getFlag,
  predictedVariant,
  putEnvironment,
  sdkSnapshot,
  settingsOf,
  urls,
  type EnvironmentBody,
} from '../support/commands';

const FLAG = 'beta-recommendations';
const REMEMBERED = 'rollout-original-settings';
const NAV = '[data-testid="nav-recommendations"]';
const IN_BETWEEN = { from: 45000, to: 55000 };

function rolloutOf(onWeight: number): EnvironmentBody['fallthrough'] {
  return {
    rollout: [
      { variant: 'on', weight: onWeight },
      { variant: 'off', weight: 100000 - onWeight },
    ],
  };
}

describe('rollout: the shop follows the evaluator for a known context key', () => {
  let original: EnvironmentBody;
  let visitor = { key: '', bucket: 0 };

  before(() => {
    getFlag(FLAG).then((flag) => {
      cy.task<EnvironmentBody>('remember', { name: REMEMBERED, value: flag.environments['dev'] }).then(
        (stored) => {
          original = stored;
        },
      );
    });
    sdkSnapshot().then((snapshot) => {
      const salt = snapshot.flags.find((flag) => flag['key'] === FLAG)?.['salt'];
      cy.task<{ key: string; bucket: number }>('visitorInBucketRange', {
        flagKey: FLAG,
        salt,
        ...IN_BETWEEN,
      }).then((found) => {
        visitor = found;
        cy.task('log', `rollout visitor ${found.key} sits in bucket ${found.bucket}`);
      });
    });
  });

  after(() => {
    putEnvironment(FLAG, settingsOf(original));
    cy.task('forget', REMEMBERED);
  });

  function applyRollout(onWeight: number): void {
    cy.then(() => {
      putEnvironment(FLAG, { ...settingsOf(original), fallthrough: rolloutOf(onWeight) });
    });
  }

  function expectNavToMatchPrediction(): void {
    cy.then(() => predictedVariant(FLAG, visitor.key)).then((variant) => {
      cy.get('body').should(($body) => {
        expect($body.find(NAV).length > 0).to.equal(variant === 'on');
      });
    });
  }

  it('hides the page below the visitor bucket, shows it above, and hides it again', () => {
    applyRollout(40000);
    cy.then(() => cy.visit(`${urls().shopUrl}/?visitor=${visitor.key}`));
    cy.contains('[role="status"]', 'Flags: Live');
    cy.then(() => predictedVariant(FLAG, visitor.key)).should('equal', 'off');
    cy.get(NAV).should('not.exist');

    applyRollout(60000);
    cy.then(() => predictedVariant(FLAG, visitor.key)).should('equal', 'on');
    cy.get(NAV).should('exist');
    expectNavToMatchPrediction();

    applyRollout(40000);
    cy.get(NAV).should('not.exist');
    expectNavToMatchPrediction();
  });

  it('puts every visitor in at 100 percent and nobody at 0 percent', () => {
    cy.then(() => cy.visit(`${urls().shopUrl}/?visitor=${visitor.key}`));
    cy.contains('[role="status"]', 'Flags: Live');

    applyRollout(100000);
    cy.get(NAV).should('exist');

    applyRollout(0);
    cy.get(NAV).should('not.exist');
  });
});
