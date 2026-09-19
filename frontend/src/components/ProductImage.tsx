const ART: Record<string, { emoji: string; bg: string }> = {
  Audio: { emoji: '🎧', bg: 'linear-gradient(135deg,#f9dcd1,#f1e0ea)' },
  'Computer Accessories': { emoji: '⌨️', bg: 'linear-gradient(135deg,#f1e0ea,#e8def8)' },
  'Cables & Power': { emoji: '🔌', bg: 'linear-gradient(135deg,#fff1d6,#f9dcd1)' },
  'PC Components': { emoji: '🖥️', bg: 'linear-gradient(135deg,#e3eefb,#f1e0ea)' },
  'Home Office': { emoji: '🪑', bg: 'linear-gradient(135deg,#e9f3e6,#f9dcd1)' },
  'Photo & Video': { emoji: '📷', bg: 'linear-gradient(135deg,#f9dcd1,#fde7f1)' },
  Lifestyle: { emoji: '🎒', bg: 'linear-gradient(135deg,#f1e0ea,#fff1d6)' },
};
const FALLBACK = { emoji: '🛍️', bg: 'linear-gradient(135deg,#f9dcd1,#f1e0ea)' };

/** Product photo, or a category-tinted placeholder when the product has no image. */
export function ProductImage({ imageUrl, categoryName, alt }: { imageUrl: string | null; categoryName?: string | null; alt: string }) {
  if (imageUrl) return <img src={imageUrl} alt={alt} loading="lazy" referrerPolicy="no-referrer" />;
  const art = (categoryName && ART[categoryName]) || FALLBACK;
  return (
    <div className="placeholder-art" style={{ background: art.bg }} role="img" aria-label={alt}>
      <span aria-hidden="true">{art.emoji}</span>
    </div>
  );
}
