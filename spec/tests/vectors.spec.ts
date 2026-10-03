import { spawnSync } from 'node:child_process';
import { readdirSync, readFileSync } from 'node:fs';
import { join, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';
import Ajv2020 from 'ajv/dist/2020.js';
import { describe, expect, it } from 'vitest';

const specDirectory = resolve(fileURLToPath(new URL('.', import.meta.url)), '..');
const vectorsDirectory = join(specDirectory, 'vectors');
const schemaPath = join(specDirectory, 'schema', 'flag-config.schema.json');

interface Serve {
  variant?: string;
  rollout?: { variant: string; weight: number }[];
}

interface EvaluationCase {
  id: string;
  flag: {
    key: string;
    variants: { key: string; value: unknown }[];
    offVariant: string;
    rules: { id: string; conditions: Record<string, unknown>[]; serve: Serve }[];
    fallthrough: Serve;
  };
  segments: { key: string; rules: Record<string, unknown>[][] }[];
  context: unknown;
  expected: {
    variantKey: string;
    value: unknown;
    reason: string;
    ruleIndex: number | null;
    ruleId: string | null;
    bucket: number | null;
  };
}

function loadDocuments(): Map<string, Record<string, unknown>> {
  const documents = new Map<string, Record<string, unknown>>();
  for (const name of readdirSync(vectorsDirectory).filter((file) => file.endsWith('.json'))) {
    documents.set(
      name,
      JSON.parse(readFileSync(join(vectorsDirectory, name), 'utf8')) as Record<string, unknown>,
    );
  }
  return documents;
}

function evaluationCases(documents: Map<string, Record<string, unknown>>): EvaluationCase[] {
  return Array.from(documents)
    .filter(([name]) => name.startsWith('eval-'))
    .flatMap(([, document]) => document['cases'] as EvaluationCase[]);
}

function allIds(documents: Map<string, Record<string, unknown>>): string[] {
  const ids: string[] = [];
  for (const document of documents.values()) {
    for (const value of Object.values(document)) {
      if (Array.isArray(value)) {
        for (const entry of value) {
          if (typeof entry === 'object' && entry !== null && 'id' in entry) {
            ids.push(String((entry as { id: unknown }).id));
          }
        }
      }
    }
  }
  return ids;
}

function servedRollout(serve: Serve): boolean {
  return serve.rollout !== undefined;
}

const documents = loadDocuments();
const cases = evaluationCases(documents);
const schema = JSON.parse(readFileSync(schemaPath, 'utf8')) as Record<string, unknown>;
const ajv = new Ajv2020({ strict: false, allErrors: true });
ajv.addSchema(schema);
const validateCase = ajv.compile({ $ref: `${String(schema['$id'])}#/$defs/evaluationCase` });

describe('vector files', () => {
  it('declares the spec version and a description in every file', () => {
    expect(documents.size).toBeGreaterThanOrEqual(10);
    for (const [name, document] of documents) {
      expect(document['specVersion'], name).toBe(1);
      expect(typeof document['description'], name).toBe('string');
    }
  });

  it('holds at least 200 evaluation cases and 200 cases in total', () => {
    expect(cases.length).toBeGreaterThanOrEqual(200);
    expect(allIds(documents).length).toBeGreaterThanOrEqual(200);
  });

  it('uses unique ids across all files', () => {
    const ids = allIds(documents);
    expect(new Set(ids).size).toBe(ids.length);
  });

  it('covers every category the spec calls out', () => {
    const ids = cases.map((entry) => entry.id);
    const required = [
      'missing-',
      'unicode-lone-high',
      'unicode-emoji',
      'semver-lt-prerelease',
      'rollouts-quarter-bucket-0',
      'rollouts-quarter-bucket-99999',
      'core-empty-rules',
      'core-disabled',
      'core-kill-switch',
      'segments-excluded',
      'operators-equals-list',
    ];
    for (const prefix of required) {
      expect(
        ids.some((id) => id.startsWith(prefix)),
        prefix,
      ).toBe(true);
    }
  });
});

describe('evaluation cases', () => {
  it.each(cases.map((entry) => [entry.id, entry] as const))('%s is well formed', (_id, entry) => {
    expect(validateCase(entry), JSON.stringify(validateCase.errors)).toBe(true);
    const variantKeys = new Set(entry.flag.variants.map((variant) => variant.key));
    expect(variantKeys.size).toBe(entry.flag.variants.length);
    expect(variantKeys.has(entry.flag.offVariant)).toBe(true);
    const ruleIds = entry.flag.rules.map((rule) => rule.id);
    expect(new Set(ruleIds).size).toBe(ruleIds.length);

    const serves = [entry.flag.fallthrough, ...entry.flag.rules.map((rule) => rule.serve)];
    for (const serve of serves) {
      if (serve.rollout !== undefined) {
        expect(serve.rollout.reduce((sum, item) => sum + item.weight, 0)).toBe(100000);
        expect(new Set(serve.rollout.map((item) => item.variant)).size).toBe(serve.rollout.length);
        for (const item of serve.rollout) {
          expect(variantKeys.has(item.variant)).toBe(true);
        }
      } else {
        expect(variantKeys.has(serve.variant as string)).toBe(true);
      }
    }

    const expected = entry.expected;
    const served = entry.flag.variants.find((variant) => variant.key === expected.variantKey);
    expect(served?.value).toEqual(expected.value);
    if (expected.reason === 'RULE_MATCH') {
      expect(expected.ruleIndex).not.toBeNull();
      expect(entry.flag.rules[expected.ruleIndex as number]?.id).toBe(expected.ruleId);
    } else {
      expect(expected.ruleIndex).toBeNull();
      expect(expected.ruleId).toBeNull();
    }
    const producingServe =
      expected.reason === 'RULE_MATCH'
        ? entry.flag.rules[expected.ruleIndex as number]?.serve
        : expected.reason === 'FALLTHROUGH'
          ? entry.flag.fallthrough
          : undefined;
    expect(expected.bucket !== null).toBe(producingServe !== undefined && servedRollout(producingServe));
  });

  it('references only segments that exist, except in the cases about unknown segments', () => {
    for (const entry of cases) {
      const known = new Set(entry.segments.map((segment) => segment.key));
      const referenced = entry.flag.rules.flatMap((rule) =>
        rule.conditions
          .filter((condition) => 'segment' in condition)
          .map((condition) => condition['segment'] as string),
      );
      const unknown = referenced.filter((key) => !known.has(key));
      if (unknown.length > 0) {
        expect(entry.id).toMatch(/unknown-segment|no-segments-at-all/);
      }
    }
  });
});

describe('generators', () => {
  it('reproduces the committed vectors from the oracle and the generator', () => {
    const run = spawnSync('python3', ['spec/tools/generate_vectors.py', '--check'], {
      cwd: resolve(specDirectory, '..'),
      encoding: 'utf8',
    });
    expect(run.stderr).toBe('');
    expect(run.status).toBe(0);
    expect(run.stdout).toContain('vectors are up to date');
  });

  it('reproduces the published MurmurHash3 values with the independent oracle', () => {
    const run = spawnSync('python3', ['spec/tools/oracle.py'], {
      cwd: resolve(specDirectory, '..'),
      encoding: 'utf8',
    });
    expect(run.status).toBe(0);
    expect(run.stdout).toContain('oracle self test passed');
  });
});
