import { readdirSync, readFileSync } from 'node:fs';
import { join } from 'node:path';
import { isDeepStrictEqual } from 'node:util';
import {
  bucketOf,
  compareSemanticVersions,
  encodeUtf8,
  evaluate,
  indexSegments,
  murmur3x86_32,
  parseSemanticVersion,
} from '../src/index';
import type { EvaluationResult, FlagConfig, Segment } from '../src/index';

export interface ConformanceReport {
  readonly language: 'typescript';
  readonly total: number;
  readonly passed: number;
  readonly failures: readonly string[];
}

interface CaseDefinition {
  readonly id: string;
  readonly [field: string]: unknown;
}

interface EvaluationCase extends CaseDefinition {
  readonly flag: FlagConfig;
  readonly segments: readonly Segment[];
  readonly context: { readonly key: string; readonly attributes: Record<string, unknown> };
  readonly expected: EvaluationResult;
}

interface Tally {
  total: number;
  passed: number;
  failures: string[];
}

function parseHex32(text: string): number {
  return Number.parseInt(text, 16) >>> 0;
}

function bytesFromHex(hex: string): Uint8Array {
  const bytes = new Uint8Array(hex.length / 2);
  for (let index = 0; index < bytes.length; index++) {
    bytes[index] = Number.parseInt(hex.slice(index * 2, index * 2 + 2), 16);
  }
  return bytes;
}

function hexOf(bytes: Uint8Array): string {
  return Array.from(bytes, (byte) => byte.toString(16).padStart(2, '0')).join('');
}

function sign(value: number): number {
  return Math.sign(value);
}

function record(tally: Tally, id: string, check: () => string | null): void {
  tally.total++;
  let problem: string | null;
  try {
    problem = check();
  } catch (error) {
    problem = `threw ${error instanceof Error ? error.message : String(error)}`;
  }
  if (problem === null) {
    tally.passed++;
  } else {
    tally.failures.push(`${id}: ${problem}`);
  }
}

function expectEqual(actual: unknown, expected: unknown): string | null {
  return isDeepStrictEqual(actual, expected)
    ? null
    : `expected ${JSON.stringify(expected)} but got ${JSON.stringify(actual)}`;
}

function casesOf(document: Record<string, unknown>, field = 'cases'): readonly CaseDefinition[] {
  const cases = document[field];
  return Array.isArray(cases) ? (cases as CaseDefinition[]) : [];
}

function verificationValue(): number {
  const collected = new Uint8Array(256 * 4);
  for (let length = 0; length < 256; length++) {
    const key = Uint8Array.from({ length }, (_, index) => index);
    new DataView(collected.buffer).setUint32(length * 4, murmur3x86_32(key, 256 - length), true);
  }
  return murmur3x86_32(collected, 0);
}

function runMurmur(document: Record<string, unknown>, tally: Tally): void {
  for (const vector of casesOf(document, 'vectors')) {
    record(tally, vector.id, () =>
      expectEqual(
        murmur3x86_32(bytesFromHex(vector.hex as string), vector.seed as number),
        parseHex32(vector.hash as string),
      ),
    );
  }
  const verification = document['verification'] as { expected: string };
  record(tally, 'murmur3-verification', () =>
    expectEqual(verificationValue(), parseHex32(verification.expected)),
  );
}

function runBuckets(document: Record<string, unknown>, tally: Tally): void {
  for (const bucketCase of casesOf(document)) {
    const flagKey = bucketCase['flagKey'] as string;
    const salt = bucketCase['salt'] as string;
    const contextKey = bucketCase['contextKey'] as string;
    record(tally, bucketCase.id, () => {
      const hash = murmur3x86_32(encodeUtf8(`${flagKey}.${salt}.${contextKey}`));
      return (
        expectEqual(hash, parseHex32(bucketCase['hash'] as string)) ??
        expectEqual(bucketOf(flagKey, salt, contextKey), bucketCase['bucket'])
      );
    });
  }
}

function runUtf8(document: Record<string, unknown>, tally: Tally): void {
  for (const utf8Case of casesOf(document)) {
    record(tally, utf8Case.id, () =>
      expectEqual(hexOf(encodeUtf8(utf8Case['text'] as string)), utf8Case['hex']),
    );
  }
}

function runSemver(document: Record<string, unknown>, tally: Tally): void {
  for (const valid of casesOf(document, 'valid')) {
    record(tally, valid.id, () => {
      const parsed = parseSemanticVersion(valid['input'] as string);
      if (parsed === null) {
        return 'parsed as invalid';
      }
      return expectEqual(
        { major: parsed.major, minor: parsed.minor, patch: parsed.patch, prerelease: parsed.prerelease },
        {
          major: valid['major'],
          minor: valid['minor'],
          patch: valid['patch'],
          prerelease: valid['prerelease'],
        },
      );
    });
  }
  for (const invalid of casesOf(document, 'invalid')) {
    record(tally, invalid.id, () => expectEqual(parseSemanticVersion(invalid['input'] as string), null));
  }
  for (const comparison of casesOf(document, 'compare')) {
    record(tally, comparison.id, () => {
      const left = parseSemanticVersion(comparison['a'] as string);
      const right = parseSemanticVersion(comparison['b'] as string);
      if (left === null || right === null) {
        return 'a side parsed as invalid';
      }
      const expected = comparison['result'] as number;
      return (
        expectEqual(sign(compareSemanticVersions(left, right)), expected) ??
        expectEqual(sign(compareSemanticVersions(right, left)), expected === 0 ? 0 : -expected)
      );
    });
  }
}

function runEvaluations(document: Record<string, unknown>, tally: Tally): void {
  for (const definition of casesOf(document)) {
    const evaluation = definition as EvaluationCase;
    record(tally, evaluation.id, () =>
      expectEqual(
        evaluate(evaluation.flag, evaluation.context, indexSegments(evaluation.segments)),
        evaluation.expected,
      ),
    );
  }
}

function runFile(name: string, document: Record<string, unknown>, tally: Tally): void {
  if (name === 'murmur3.json') {
    runMurmur(document, tally);
  } else if (name === 'bucket.json') {
    runBuckets(document, tally);
  } else if (name === 'utf8.json') {
    runUtf8(document, tally);
  } else if (name === 'semver.json') {
    runSemver(document, tally);
  } else if (name.startsWith('eval-')) {
    runEvaluations(document, tally);
  } else {
    record(tally, name, () => 'unknown vector file');
  }
}

export function runConformance(vectorsDirectory: string): ConformanceReport {
  const tally: Tally = { total: 0, passed: 0, failures: [] };
  const names = readdirSync(vectorsDirectory)
    .filter((name) => name.endsWith('.json'))
    .sort();
  for (const name of names) {
    const document = JSON.parse(readFileSync(join(vectorsDirectory, name), 'utf8')) as Record<
      string,
      unknown
    >;
    runFile(name, document, tally);
  }
  if (tally.total === 0) {
    tally.failures.push('no vectors found');
  }
  return { language: 'typescript', total: tally.total, passed: tally.passed, failures: tally.failures };
}

export function summaryLine(report: ConformanceReport): string {
  return `conformance ${report.language} cases=${report.total} passed=${report.passed} failed=${report.total - report.passed}`;
}
