/** A parsed semantic version 2.0.0. Numeric parts stay strings so that values beyond 2^53 compare correctly. */
export interface SemanticVersion {
  readonly major: string;
  readonly minor: string;
  readonly patch: string;
  readonly prerelease: readonly string[];
}

const IDENTIFIER = String.raw`(?:0|[1-9][0-9]*|[0-9]*[A-Za-z-][0-9A-Za-z-]*)`;
const NUMBER = String.raw`(0|[1-9][0-9]*)`;
const SEMVER_PATTERN = new RegExp(
  `^${NUMBER}\\.${NUMBER}\\.${NUMBER}(?:-(${IDENTIFIER}(?:\\.${IDENTIFIER})*))?(?:\\+[0-9A-Za-z-]+(?:\\.[0-9A-Za-z-]+)*)?$`,
);

const NUMERIC_IDENTIFIER = /^[0-9]+$/;

/** Parses a version such as `1.2.3-rc.1+build5`, or returns `null` when the text is not a valid semantic version. */
export function parseSemanticVersion(text: string): SemanticVersion | null {
  const match = SEMVER_PATTERN.exec(text);
  if (match === null) {
    return null;
  }
  const prerelease = match[4];
  return {
    major: match[1],
    minor: match[2],
    patch: match[3],
    prerelease: prerelease === undefined ? [] : prerelease.split('.'),
  };
}

function isNumeric(identifier: string): boolean {
  return NUMERIC_IDENTIFIER.test(identifier);
}

function compareDigitStrings(left: string, right: string): number {
  if (left.length !== right.length) {
    return left.length < right.length ? -1 : 1;
  }
  if (left === right) {
    return 0;
  }
  return left < right ? -1 : 1;
}

function compareIdentifiers(left: string, right: string): number {
  const leftNumeric = isNumeric(left);
  const rightNumeric = isNumeric(right);
  if (leftNumeric && rightNumeric) {
    return compareDigitStrings(left, right);
  }
  if (leftNumeric) {
    return -1;
  }
  if (rightNumeric) {
    return 1;
  }
  if (left === right) {
    return 0;
  }
  return left < right ? -1 : 1;
}

function comparePrerelease(left: readonly string[], right: readonly string[]): number {
  if (left.length === 0 || right.length === 0) {
    if (left.length === right.length) {
      return 0;
    }
    return left.length === 0 ? 1 : -1;
  }
  const shared = Math.min(left.length, right.length);
  for (let index = 0; index < shared; index++) {
    const result = compareIdentifiers(left[index], right[index]);
    if (result !== 0) {
      return result;
    }
  }
  if (left.length === right.length) {
    return 0;
  }
  return left.length < right.length ? -1 : 1;
}

/** Orders two versions by semver precedence: negative when `left` is lower, zero when equal, positive when higher. Build metadata is ignored. */
export function compareSemanticVersions(left: SemanticVersion, right: SemanticVersion): number {
  return (
    compareDigitStrings(left.major, right.major) ||
    compareDigitStrings(left.minor, right.minor) ||
    compareDigitStrings(left.patch, right.patch) ||
    comparePrerelease(left.prerelease, right.prerelease)
  );
}
