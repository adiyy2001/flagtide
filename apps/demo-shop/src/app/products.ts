export interface Product {
  readonly id: string;
  readonly name: string;
  readonly description: string;
  readonly priceCents: number;
  readonly hue: number;
}

export const PRODUCTS: readonly Product[] = [
  {
    id: 'kettle',
    name: 'Gooseneck kettle',
    description: 'Steel, 0.8 litre, temperature hold.',
    priceCents: 6900,
    hue: 18,
  },
  {
    id: 'grinder',
    name: 'Hand grinder',
    description: 'Burr grinder with a steel handle.',
    priceCents: 4900,
    hue: 32,
  },
  {
    id: 'dripper',
    name: 'Ceramic dripper',
    description: 'Slow brewing cone for one or two cups.',
    priceCents: 2800,
    hue: 28,
  },
  {
    id: 'scale',
    name: 'Brew scale',
    description: 'Timer and 0.1 gram resolution.',
    priceCents: 5400,
    hue: 200,
  },
  {
    id: 'beans',
    name: 'Filter roast, 250 g',
    description: 'Light roast, washed, floral.',
    priceCents: 1600,
    hue: 12,
  },
  {
    id: 'filters',
    name: 'Paper filters, 100',
    description: 'Unbleached cones, size 02.',
    priceCents: 900,
    hue: 45,
  },
];

export function formatMoney(cents: number): string {
  return new Intl.NumberFormat('en-IE', { style: 'currency', currency: 'EUR' }).format(cents / 100);
}
