import { DemoArt } from './DemoArt';
import { DEMO_ART_KINDS, type DemoArtKind } from './demoArtKinds';

/** Which drawing stands in for a product that has no picture, by category (covers the original catalogue). */
const CATEGORY_KIND: Record<string, DemoArtKind> = {
  Audio: 'headphones',
  'Computer Accessories': 'keyboard',
  'Cables & Power': 'cable',
  'PC Components': 'chip',
  'Home Office': 'chair',
  'Photo & Video': 'camera',
  Lifestyle: 'backpack',
};

function hueOf(text: string): number {
  let h = 0;
  for (const ch of text) h = (h * 31 + ch.charCodeAt(0)) >>> 0;
  return h % 360;
}

const isKind = (k: string): k is DemoArtKind => (DEMO_ART_KINDS as readonly string[]).includes(k);

/**
 * Product picture. "demo:<kind>:<hue>" (used by the demo data) is drawn in the browser; a normal URL is shown as a
 * photo; and a product with no picture gets a drawing chosen from its category.
 */
export function ProductImage({ imageUrl, categoryName, alt }: { imageUrl: string | null; categoryName?: string | null; alt: string }) {
  if (imageUrl?.startsWith('demo:')) {
    const [, kind = '', hue = '0'] = imageUrl.split(':');
    if (isKind(kind)) return <DemoArt kind={kind} hue={Number(hue) || 0} label={alt} />;
  } else if (imageUrl) {
    return <img src={imageUrl} alt={alt} loading="lazy" referrerPolicy="no-referrer" />;
  }
  const kind = (categoryName && CATEGORY_KIND[categoryName]) || 'backpack';
  return <DemoArt kind={kind} hue={hueOf(alt)} label={alt} />;
}
