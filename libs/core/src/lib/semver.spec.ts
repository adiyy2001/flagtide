import fc from 'fast-check';
import semver from 'semver';
import { describe, expect, it } from 'vitest';
import { compareSemanticVersions, parseSemanticVersion } from './semver.js';

function compareText(left: string, right: string): number {
  const parsedLeft = parseSemanticVersion(left);
  const parsedRight = parseSemanticVersion(right);
  if (parsedLeft === null || parsedRight === null) {
    throw new Error('invalid test version');
  }
  return Math.sign(compareSemanticVersions(parsedLeft, parsedRight));
}

const numericIdentifier = fc.integer({ min: 0, max: 1000 }).map(String);
const alphanumericIdentifier = fc
  .stringMatching(/^[0-9A-Za-z-]{1,6}$/)
  .filter((identifier) => /[A-Za-z-]/.test(identifier));
const prereleaseIdentifier = fc.oneof(numericIdentifier, alphanumericIdentifier);
const buildIdentifier = fc.stringMatching(/^[0-9A-Za-z-]{1,6}$/);

const version = fc
  .record({
    major: fc.integer({ min: 0, max: 30 }),
    minor: fc.integer({ min: 0, max: 30 }),
    patch: fc.integer({ min: 0, max: 30 }),
    prerelease: fc.array(prereleaseIdentifier, { maxLength: 3 }),
    build: fc.array(buildIdentifier, { maxLength: 2 }),
  })
  .map(({ major, minor, patch, prerelease, build }) => {
    const pre = prerelease.length > 0 ? `-${prerelease.join('.')}` : '';
    const meta = build.length > 0 ? `+${build.join('.')}` : '';
    return `${major}.${minor}.${patch}${pre}${meta}`;
  });

describe('parseSemanticVersion', () => {
  it('splits the parts and drops build metadata', () => {
    expect(parseSemanticVersion('1.2.3-alpha.1+build.5')).toEqual({
      major: '1',
      minor: '2',
      patch: '3',
      prerelease: ['alpha', '1'],
    });
  });

  it('keeps arbitrarily large numbers as digit strings', () => {
    expect(parseSemanticVersion('99999999999999999999.0.0')?.major).toBe('99999999999999999999');
  });

  it.each([
    '',
    '1',
    '1.2',
    'v1.2.3',
    '01.2.3',
    '1.2.3-01',
    '1.2.3-',
    '1.2.3+',
    ' 1.2.3',
    '1.2.3\n',
    '１.2.3',
  ])('rejects %j', (text) => {
    expect(parseSemanticVersion(text)).toBeNull();
  });
});

describe('compareSemanticVersions', () => {
  it('agrees with the semver package on generated version pairs', () => {
    fc.assert(
      fc.property(version, version, (left, right) => {
        expect(compareText(left, right)).toBe(semver.compare(left, right));
      }),
      { numRuns: 5000 },
    );
  });

  it('agrees with the semver package on which strings are valid', () => {
    const alphabet = fc.stringMatching(/^[0-9A-Za-z.+-]{0,14}$/).filter((text) => !/^[vV=]/.test(text));
    fc.assert(
      fc.property(alphabet, (text) => {
        expect(parseSemanticVersion(text) !== null).toBe(semver.valid(text) !== null);
      }),
      { numRuns: 20000 },
    );
  });

  it('orders the precedence example of semver.org', () => {
    const ordered = [
      '1.0.0-alpha',
      '1.0.0-alpha.1',
      '1.0.0-alpha.beta',
      '1.0.0-beta',
      '1.0.0-beta.2',
      '1.0.0-beta.11',
      '1.0.0-rc.1',
      '1.0.0',
    ];
    for (let index = 0; index + 1 < ordered.length; index++) {
      expect(compareText(ordered[index], ordered[index + 1])).toBe(-1);
    }
  });

  it('compares numbers beyond 2^53 exactly', () => {
    expect(compareText('9007199254740993.0.0', '9007199254740992.0.0')).toBe(1);
    expect(compareText('1.0.0-18446744073709551616', '1.0.0-18446744073709551615')).toBe(1);
  });

  it('ignores build metadata', () => {
    expect(compareText('1.0.0+a', '1.0.0+b')).toBe(0);
  });
});
