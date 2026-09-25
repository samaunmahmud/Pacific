import { useState, type KeyboardEvent } from 'react';
import type { Product } from '../api/types';
import { ProductImage } from './ProductImage';

/** The product page picture: the main photo, plus a row of thumbnails to switch between photos when there are more. */
export function ProductGallery({ product }: { product: Product }) {
  const photos = [product.imageUrl, ...(product.moreImages ?? [])].filter((u): u is string => !!u);
  const [shown, setShown] = useState(0);
  const current = photos[shown] ?? null;
  const label = (i: number) => (photos.length > 1 ? `${product.name}, photo ${i + 1} of ${photos.length}` : product.name);

  // Left/right arrows step through the thumbnails, like a tab list.
  function onKey(e: KeyboardEvent<HTMLUListElement>) {
    if (e.key !== 'ArrowLeft' && e.key !== 'ArrowRight') return;
    e.preventDefault();
    const next = (shown + (e.key === 'ArrowRight' ? 1 : -1) + photos.length) % photos.length;
    setShown(next);
    e.currentTarget.querySelectorAll('button')[next]?.focus();
  }

  return (
    <div className="pdp-image">
      <div className="image-container">
        <ProductImage imageUrl={current} categoryName={product.category?.name} alt={label(shown)} />
      </div>
      {photos.length > 1 && (
        <ul className="pdp-thumbs" aria-label="Product photos" onKeyDown={onKey}>
          {photos.map((url, i) => (
            <li key={url}>
              <button type="button" className="pdp-thumb" aria-current={i === shown} aria-label={`Show photo ${i + 1} of ${photos.length}`}
                tabIndex={i === shown ? 0 : -1} onClick={() => setShown(i)}>
                <ProductImage imageUrl={url} categoryName={product.category?.name} alt="" />
              </button>
            </li>
          ))}
        </ul>
      )}
    </div>
  );
}
