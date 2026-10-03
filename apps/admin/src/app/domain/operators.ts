import type { Operator, Scalar } from '@flagwire/core';
import { parseSemanticVersion } from '@flagwire/core';

export type ValueKind = 'scalar' | 'text' | 'number' | 'semver';
export type ScalarType = 'string' | 'number' | 'boolean';

export interface OperatorInfo {
  readonly operator: Operator;
  readonly label: string;
  readonly many: boolean;
  readonly kind: ValueKind;
}

export const OPERATORS: readonly OperatorInfo[] = [
  { operator: 'equals', label: 'equals', many: false, kind: 'scalar' },
  { operator: 'in', label: 'is one of', many: true, kind: 'scalar' },
  { operator: 'contains', label: 'contains', many: true, kind: 'text' },
  { operator: 'startsWith', label: 'starts with', many: true, kind: 'text' },
  { operator: 'lt', label: 'is less than', many: false, kind: 'number' },
  { operator: 'lte', label: 'is at most', many: false, kind: 'number' },
  { operator: 'gt', label: 'is greater than', many: false, kind: 'number' },
  { operator: 'gte', label: 'is at least', many: false, kind: 'number' },
  { operator: 'semverEquals', label: 'version equals', many: false, kind: 'semver' },
  { operator: 'semverLt', label: 'version is below', many: false, kind: 'semver' },
  { operator: 'semverLte', label: 'version is at most', many: false, kind: 'semver' },
  { operator: 'semverGt', label: 'version is above', many: false, kind: 'semver' },
  { operator: 'semverGte', label: 'version is at least', many: false, kind: 'semver' },
];

const BY_OPERATOR = new Map(OPERATORS.map((info) => [info.operator, info]));

export function operatorInfo(operator: Operator): OperatorInfo {
  const info = BY_OPERATOR.get(operator);
  if (info === undefined) {
    throw new Error(`unknown operator ${operator}`);
  }
  return info;
}

export function splitValues(text: string): string[] {
  return text
    .split(',')
    .map((token) => token.trim())
    .filter((token) => token !== '');
}

export function formatValues(values: readonly Scalar[]): string {
  return values.map((value) => String(value)).join(', ');
}

export function inferScalarType(values: readonly Scalar[]): ScalarType {
  const first = values[0];
  if (typeof first === 'number') {
    return 'number';
  }
  if (typeof first === 'boolean') {
    return 'boolean';
  }
  return 'string';
}

export type ParsedValues = { readonly values: Scalar[] } | { readonly error: string };

export function parseNumberText(token: string): number | null {
  if (!/^[+-]?(\d+\.?\d*|\.\d+)([eE][+-]?\d+)?$/u.test(token)) {
    return null;
  }
  const value = Number(token);
  return Number.isFinite(value) ? value : null;
}

function parseScalar(token: string, type: ScalarType): Scalar | { error: string } {
  if (type === 'string') {
    return token;
  }
  if (type === 'number') {
    const value = parseNumberText(token);
    return value === null ? { error: `${token} is not a number` } : value;
  }
  if (token === 'true' || token === 'false') {
    return token === 'true';
  }
  return { error: `${token} is not true or false` };
}

export function parseConditionValues(operator: Operator, type: ScalarType, text: string): ParsedValues {
  const info = operatorInfo(operator);
  const tokens = splitValues(text);
  if (tokens.length === 0) {
    return { error: info.many ? 'Enter at least one value' : 'Enter a value' };
  }
  if (!info.many && tokens.length > 1) {
    return { error: 'This operator takes exactly one value' };
  }
  const values: Scalar[] = [];
  for (const token of tokens) {
    const parsed = parseToken(info.kind, type, token);
    if (typeof parsed === 'object') {
      return { error: parsed.error };
    }
    values.push(parsed);
  }
  return { values };
}

function parseToken(kind: ValueKind, type: ScalarType, token: string): Scalar | { error: string } {
  if (kind === 'text') {
    return token;
  }
  if (kind === 'number') {
    return parseScalar(token, 'number');
  }
  if (kind === 'semver') {
    return parseSemanticVersion(token) === null
      ? { error: `${token} is not a semantic version such as 2.1.0 or 2.0.0-rc.1` }
      : token;
  }
  return parseScalar(token, type);
}
