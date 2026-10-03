export const TOTAL_UNITS = 100000;

const PERCENT_PATTERN = /^(\d{1,3})(?:\.(\d{1,3}))?$/u;

export function parsePercent(text: string): number | null {
  const match = PERCENT_PATTERN.exec(text.trim());
  if (match === null) {
    return null;
  }
  const whole = Number(match[1]);
  const fraction = Number((match[2] ?? '').padEnd(3, '0'));
  const units = whole * 1000 + fraction;
  return units <= TOTAL_UNITS ? units : null;
}

export function formatPercent(units: number): string {
  const whole = Math.trunc(units / 1000);
  const fraction = String(units % 1000)
    .padStart(3, '0')
    .replace(/0+$/u, '');
  return fraction === '' ? String(whole) : `${whole}.${fraction}`;
}

export function sumUnits(percentTexts: readonly string[]): number {
  return percentTexts.reduce((total, text) => total + (parsePercent(text) ?? 0), 0);
}

export function remainingUnits(percentTexts: readonly string[]): number {
  return TOTAL_UNITS - sumUnits(percentTexts);
}

export function describeRemaining(units: number): string {
  if (units === 0) {
    return 'Weights add up to 100%';
  }
  if (units > 0) {
    return `${formatPercent(units)}% still to assign`;
  }
  return `Over by ${formatPercent(-units)}%`;
}

export function splitEvenly(count: number): string[] {
  if (count <= 0) {
    return [];
  }
  const base = Math.floor(TOTAL_UNITS / count);
  const extra = TOTAL_UNITS - base * count;
  return Array.from({ length: count }, (_, index) => formatPercent(index === 0 ? base + extra : base));
}
