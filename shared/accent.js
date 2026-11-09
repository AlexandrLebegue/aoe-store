const PALETTE = ['#5B8CFF', '#7C5CFF', '#22C55E', '#F59E0B', '#EC4899', '#06B6D4', '#EF4444', '#8B5CF6'];

export function defaultAccent(slug) {
  let hash = 2166136261;
  for (let i = 0; i < slug.length; i += 1) {
    hash ^= slug.charCodeAt(i);
    hash = Math.imul(hash, 16777619);
  }
  return PALETTE[(hash >>> 0) % PALETTE.length];
}
