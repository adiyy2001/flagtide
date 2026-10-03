export interface ChartPoint {
  readonly p50: number;
  readonly p95: number;
  readonly p99: number;
}

export const MAX_POINTS = 120;

export function appendPoint<T>(points: readonly T[], point: T, limit = MAX_POINTS): T[] {
  return [...points, point].slice(-limit);
}

const STEPS = [10, 20, 50, 100, 200, 300, 500, 1000, 2000, 5000];

export function niceMax(value: number): number {
  return STEPS.find((step) => step >= value) ?? Math.ceil(value / 1000) * 1000;
}

export function linePath(
  values: readonly number[],
  width: number,
  height: number,
  maxValue: number,
  limit = MAX_POINTS,
): string {
  if (values.length === 0 || maxValue <= 0) {
    return '';
  }
  const step = limit > 1 ? width / (limit - 1) : 0;
  const offset = limit - values.length;
  return values
    .map((value, index) => {
      const x = (offset + index) * step;
      const y = height - (Math.min(value, maxValue) / maxValue) * height;
      return `${index === 0 ? 'M' : 'L'}${x.toFixed(1)} ${y.toFixed(1)}`;
    })
    .join(' ');
}
