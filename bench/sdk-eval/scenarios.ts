import { evaluate, indexSegments } from '../../libs/core/src/index';
import type { EvaluationContext, FlagConfig, Segment } from '../../libs/core/src/index';

export interface Scenario {
  readonly name: string;
  readonly run: () => unknown;
}

const BOOLEAN_VARIANTS = [
  { key: 'on', value: true },
  { key: 'off', value: false },
] as const;

const SEGMENTS: readonly Segment[] = [
  {
    key: 'beta-testers',
    included: ['user-7', 'user-9'],
    excluded: [],
    rules: [[{ attribute: 'email', operator: 'contains', values: ['@example.com'] }]],
  },
];

const PERCENTAGE_ROLLOUT: FlagConfig = {
  key: 'checkout-redesign',
  type: 'boolean',
  enabled: true,
  killSwitch: false,
  salt: 'a41f',
  variants: BOOLEAN_VARIANTS,
  offVariant: 'off',
  rules: [],
  fallthrough: {
    rollout: [
      { variant: 'on', weight: 25000 },
      { variant: 'off', weight: 75000 },
    ],
  },
};

const TARGETED_RULES: FlagConfig = {
  key: 'new-pricing',
  type: 'string',
  enabled: true,
  killSwitch: false,
  salt: 'c09e',
  variants: [
    { key: 'control', value: 'control' },
    { key: 'annual', value: 'annual' },
    { key: 'monthly', value: 'monthly' },
  ],
  offVariant: 'control',
  rules: [
    {
      id: 'beta-segment',
      conditions: [{ segment: 'beta-testers' }],
      serve: { variant: 'annual' },
    },
    {
      id: 'recent-app-in-poland',
      conditions: [
        { attribute: 'country', operator: 'in', values: ['PL', 'DE', 'CZ'] },
        { attribute: 'appVersion', operator: 'semverGte', values: ['2.4.0'] },
        { attribute: 'plan', operator: 'equals', values: ['pro'], negate: true },
      ],
      serve: {
        rollout: [
          { variant: 'annual', weight: 50000 },
          { variant: 'monthly', weight: 50000 },
        ],
      },
    },
  ],
  fallthrough: { variant: 'control' },
};

const KILLED: FlagConfig = { ...PERCENTAGE_ROLLOUT, key: 'killed-flag', killSwitch: true };

function contextPool(size: number): readonly EvaluationContext[] {
  const countries = ['PL', 'DE', 'US', 'CZ', 'FR'];
  const plans = ['free', 'pro', 'team'];
  return Array.from({ length: size }, (_, index) => ({
    key: `user-${index}`,
    attributes: {
      email: `person${index}@${index % 7 === 0 ? 'example.com' : 'mail.test'}`,
      country: countries[index % countries.length],
      plan: plans[index % plans.length],
      appVersion: `2.${index % 9}.${index % 4}`,
    },
  }));
}

export function buildScenarios(poolSize = 4096): readonly Scenario[] {
  const contexts = contextPool(poolSize);
  const segments = indexSegments(SEGMENTS);
  let cursor = 0;
  const nextContext = (): EvaluationContext => {
    cursor = (cursor + 1) % poolSize;
    return contexts[cursor] as EvaluationContext;
  };
  return [
    { name: 'kill switch', run: () => evaluate(KILLED, nextContext(), segments) },
    { name: 'percentage rollout', run: () => evaluate(PERCENTAGE_ROLLOUT, nextContext(), segments) },
    {
      name: 'targeted rules with segment and rollout',
      run: () => evaluate(TARGETED_RULES, nextContext(), segments),
    },
  ];
}
