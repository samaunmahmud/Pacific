// Clothing sizes from smallest to largest; spelled-out names share a rank with their letters.
const RANKS: Record<string, number> = {
  xxxs: 0, '3xs': 0, xxs: 1, '2xs': 1, xs: 2, 'extra small': 2,
  s: 3, small: 3, m: 4, medium: 4, l: 5, large: 5,
  xl: 6, 'extra large': 6, xxl: 7, '2xl': 7, xxxl: 8, '3xl': 8, '4xl': 9, '5xl': 10,
};
const rank = (value: string) => RANKS[value.trim().toLowerCase()];
const number = (value: string) => {
  const m = /\d+(\.\d+)?/.exec(value);
  return m ? Number(m[0]) : undefined;
};

/**
 * Puts option values in size order when every one of them is a size: letter sizes (XS, S, M, L, XL...) or numbered
 * ones (8, 10, 12 or UK 6, UK 7). Anything else, such as colours, keeps the order the seller added them in.
 */
export function sizeOrder(values: string[]): string[] {
  if (values.length < 2) return values;
  if (values.every((v) => rank(v) !== undefined)) return [...values].sort((a, b) => rank(a) - rank(b));
  if (values.every((v) => number(v) !== undefined)) return [...values].sort((a, b) => number(a)! - number(b)!);
  return values;
}
