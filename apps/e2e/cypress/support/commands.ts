export interface E2eUrls {
  readonly adminUrl: string;
  readonly shopUrl: string;
  readonly apiA: string;
  readonly apiB: string;
  readonly harnessUrl: string;
}

export const DEV_ADMIN_KEY = 'fwa_demo_dev_admin_000000000000';
export const DEV_SDK_KEY = 'fws_demo_dev_sdk_0000000000000';
export const PROJECT = 'demo';

export interface Serve {
  variant?: string;
  rollout?: { variant: string; weight: number }[];
}

export interface RuleBody {
  id: string;
  order: number;
  conditions: unknown[];
  serve: Serve;
}

export interface EnvironmentBody {
  enabled: boolean;
  killSwitch: boolean;
  offVariant: string;
  salt: string;
  rules: RuleBody[];
  fallthrough: Serve;
}

export interface FlagBody {
  key: string;
  revision: number;
  environments: Record<string, EnvironmentBody>;
}

export function urls(): E2eUrls {
  return {
    adminUrl: Cypress.expose('adminUrl') as string,
    shopUrl: Cypress.expose('shopUrl') as string,
    apiA: Cypress.expose('apiA') as string,
    apiB: Cypress.expose('apiB') as string,
    harnessUrl: Cypress.expose('harnessUrl') as string,
  };
}

export function flagsUrl(): string {
  return `${urls().apiA}/api/v1/projects/${PROJECT}/flags`;
}

export function adminHeaders(): Record<string, string> {
  return { Authorization: `Bearer ${DEV_ADMIN_KEY}` };
}

export function getFlag(key: string): Cypress.Chainable<FlagBody> {
  return cy
    .request<FlagBody>({ url: `${flagsUrl()}/${key}`, headers: adminHeaders() })
    .then((response) => response.body);
}

export function putEnvironment(
  key: string,
  body: Pick<EnvironmentBody, 'enabled' | 'offVariant' | 'rules' | 'fallthrough'>,
): Cypress.Chainable<Cypress.Response<unknown>> {
  return cy.request({
    method: 'PUT',
    url: `${flagsUrl()}/${key}/environments/dev`,
    headers: adminHeaders(),
    body,
  });
}

export function putEnabled(key: string, enabled: boolean): Cypress.Chainable<Cypress.Response<unknown>> {
  return cy.request({
    method: 'PUT',
    url: `${flagsUrl()}/${key}/environments/dev/enabled`,
    headers: adminHeaders(),
    body: { enabled },
  });
}

export function settingsOf(
  environment: EnvironmentBody,
): Pick<EnvironmentBody, 'enabled' | 'offVariant' | 'rules' | 'fallthrough'> {
  return {
    enabled: environment.enabled,
    offVariant: environment.offVariant,
    rules: environment.rules,
    fallthrough: environment.fallthrough,
  };
}

export function uniqueKey(prefix: string): string {
  return `${prefix}-${Date.now().toString(36)}`;
}

export function frameBody(selector: string): Cypress.Chainable<JQuery<HTMLElement>> {
  return cy
    .get(selector)
    .its('0.contentDocument.body')
    .should('not.be.empty')
    .then((body) => cy.wrap(body as unknown as JQuery<HTMLElement>));
}

export function fill(selector: string, text: string): Cypress.Chainable<JQuery<HTMLElement>> {
  return cy.get(selector).focus().clear({ force: true }).type(text, { force: true });
}

export interface SnapshotBody {
  v: number;
  flags: Record<string, unknown>[];
  segments: unknown[];
}

export function sdkSnapshot(): Cypress.Chainable<SnapshotBody> {
  return cy
    .request<SnapshotBody>({
      url: `${urls().apiA}/sdk/v1/snapshot`,
      headers: { Authorization: `Bearer ${DEV_SDK_KEY}` },
    })
    .then((response) => response.body);
}

export function predictedVariant(flagKey: string, visitorKey: string): Cypress.Chainable<string> {
  return sdkSnapshot().then((snapshot) => {
    const flag = snapshot.flags.find((candidate) => candidate['key'] === flagKey);
    return cy
      .task<{ variantKey: string }>('evaluateFlag', {
        flag,
        segments: snapshot.segments,
        key: visitorKey,
        attributes: { country: 'PL', plan: 'standard' },
      })
      .then((result) => result.variantKey);
  });
}
