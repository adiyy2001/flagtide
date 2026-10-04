import { cpSync, mkdtempSync, readFileSync, rmSync, writeFileSync } from 'node:fs';
import { tmpdir } from 'node:os';
import { join, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';
import { afterEach, describe, expect, it } from 'vitest';
import { runConformance, summaryLine } from './conformance';

const vectors = resolve(fileURLToPath(new URL('.', import.meta.url)), '../../../spec/vectors');
const scratch: string[] = [];

function copyOfVectors(): string {
  const directory = mkdtempSync(join(tmpdir(), 'flagtide-vectors-'));
  scratch.push(directory);
  cpSync(vectors, directory, { recursive: true });
  return directory;
}

function corruptedCopy(file: string, corrupt: (text: string) => string): string {
  const directory = copyOfVectors();
  const path = join(directory, file);
  const original = readFileSync(path, 'utf8');
  const corrupted = corrupt(original);
  expect(corrupted).not.toBe(original);
  writeFileSync(path, corrupted);
  return directory;
}

afterEach(() => {
  for (const directory of scratch.splice(0)) {
    rmSync(directory, { recursive: true, force: true });
  }
});

describe('typescript conformance runner', () => {
  it('passes every shared vector', () => {
    const report = runConformance(vectors);
    expect(report.failures).toEqual([]);
    expect(report.total).toBeGreaterThanOrEqual(200);
    expect(report.passed).toBe(report.total);
    expect(summaryLine(report)).toBe(
      `conformance typescript cases=${report.total} passed=${report.total} failed=0`,
    );
  });

  it('fails when an evaluation vector expects another reason', () => {
    const directory = corruptedCopy('eval-core.json', (text) =>
      text.replace('"reason":"OFF"', '"reason":"FALLTHROUGH"'),
    );
    expect(runConformance(directory).failures.length).toBeGreaterThan(0);
  });

  it('fails when an evaluation vector expects another bucket', () => {
    const directory = corruptedCopy('eval-rollouts.json', (text) =>
      text.replace('"bucket":24999', '"bucket":24998'),
    );
    expect(runConformance(directory).failures.length).toBeGreaterThan(0);
  });

  it('fails when a hash vector is wrong', () => {
    const directory = corruptedCopy('murmur3.json', (text) =>
      text.replace('"hash":"0xBA6BD213"', '"hash":"0xBA6BD214"'),
    );
    expect(runConformance(directory).failures.length).toBeGreaterThan(0);
  });

  it('fails when the verification value is wrong', () => {
    const directory = corruptedCopy('murmur3.json', (text) => text.replace('0xB0F57EE3', '0xB0F57EE4'));
    expect(runConformance(directory).failures).toEqual([expect.stringContaining('murmur3-verification')]);
  });

  it('fails when a semver comparison is flipped', () => {
    const directory = corruptedCopy('semver.json', (text) => text.replace('"result":-1', '"result":1'));
    expect(runConformance(directory).failures.length).toBeGreaterThan(0);
  });

  it('fails on an unknown vector file', () => {
    const directory = copyOfVectors();
    writeFileSync(join(directory, 'mystery.json'), '{}');
    expect(runConformance(directory).failures).toEqual([expect.stringContaining('unknown vector file')]);
  });
});
