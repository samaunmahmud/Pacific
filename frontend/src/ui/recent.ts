const KEY = 'pacific.recentlyViewed';
const MAX = 12;

/** Recently viewed product ids live in this browser only (a per-visitor convenience, not account data). */
export function getRecent(): number[] {
  try {
    const parsed: unknown = JSON.parse(localStorage.getItem(KEY) ?? '[]');
    return Array.isArray(parsed) ? parsed.filter((n): n is number => Number.isInteger(n)) : [];
  } catch {
    return [];
  }
}

export function recordView(productId: number) {
  try {
    const next = [productId, ...getRecent().filter((id) => id !== productId)].slice(0, MAX);
    localStorage.setItem(KEY, JSON.stringify(next));
  } catch {
    /* storage unavailable: the strip just stays empty */
  }
}
