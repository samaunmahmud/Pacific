import { useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import { api } from '../api/client';
import type { Page, Product } from '../api/types';
import { useAuth } from '../auth/AuthContext';
import { DemoArt } from '../components/DemoArt';
import type { DemoArtKind } from '../components/demoArtKinds';
import { ProductImage } from '../components/ProductImage';
import { ProductShelf } from '../components/ProductShelf';
import { RecentlyViewed } from '../components/RecentlyViewed';
import { useAsync } from '../ui/useAsync';
import { useCategories } from '../ui/useCategories';

interface Slide {
  title: string;
  text: string;
  cta: string;
  to: string;
  className: string;
  art: [DemoArtKind, number][];
}

const SLIDES: Slide[] = [
  { title: "Today's Deals", text: 'Big savings across every department, refreshed all the time.', cta: 'Shop deals', to: '/deals', className: 'slide-a', art: [['headphones', 330], ['camera', 20], ['backpack', 45]] },
  { title: 'Fresh on the shelves', text: 'The newest arrivals from Pacific and independent sellers.', cta: 'See what’s new', to: '/products?sort=newest', className: 'slide-b', art: [['watch', 200], ['kettle', 160], ['shoe', 340]] },
  { title: 'Sell on Pacific', text: 'Open your own storefront and reach shoppers today.', cta: 'Start selling', to: '/sell', className: 'slide-c', art: [['tent', 25], ['book', 300], ['gamepad', 260]] },
];

function Hero() {
  const [i, setI] = useState(0);
  const [paused, setPaused] = useState(false);
  useEffect(() => {
    if (paused) return;
    const t = window.setInterval(() => setI((n) => (n + 1) % SLIDES.length), 6500);
    return () => window.clearInterval(t);
  }, [paused]);
  const go = (n: number) => setI((n + SLIDES.length) % SLIDES.length);
  return (
    <section className="hero" aria-roledescription="carousel" aria-label="Featured" onMouseEnter={() => setPaused(true)} onMouseLeave={() => setPaused(false)}>
      {SLIDES.map((s, n) => (
        <div key={s.title} className={`hero-slide ${s.className} ${n === i ? 'active' : ''}`} aria-hidden={n !== i}>
          <div className="hero-text">
            <h2>{s.title}</h2>
            <p>{s.text}</p>
            <Link to={s.to} className="hero-cta" tabIndex={n === i ? 0 : -1}>{s.cta}</Link>
          </div>
          <div className="hero-art" aria-hidden="true">
            {s.art.map(([kind, hue], k) => (
              <span key={kind} className={`hero-icon hi-${k}`}><DemoArt kind={kind} hue={hue} label="" bare /></span>
            ))}
          </div>
        </div>
      ))}
      <button className="hero-arrow left" onClick={() => go(i - 1)} aria-label="Previous slide">‹</button>
      <button className="hero-arrow right" onClick={() => go(i + 1)} aria-label="Next slide">›</button>
      <div className="hero-dots">
        {SLIDES.map((s, n) => <button key={s.title} className={n === i ? 'on' : ''} onClick={() => setI(n)} aria-label={`Slide ${n + 1}`} />)}
      </div>
    </section>
  );
}

/** A "Shop <category>" tile showing four of its most popular products. */
function CategoryCard({ name, slug }: { name: string; slug: string }) {
  const { data } = useAsync(() => api<Page<Product>>('/products', { query: { category: slug, sort: 'popular', size: 4 } }), [slug]);
  if (data && data.items.length === 0) return null;
  return (
    <div className="cat-card">
      <h3>Shop {name}</h3>
      <div className="cat-grid">
        {(data?.items ?? [null, null, null, null]).map((p, n) => p ? (
          <Link key={p.id} to={`/products/${p.id}`} className="cat-tile" title={p.name}>
            <ProductImage imageUrl={p.imageUrl} categoryName={p.category?.name} alt={p.name} />
          </Link>
        ) : <span key={n} className="cat-tile skeleton" />)}
      </div>
      <Link to={`/products?category=${slug}`} className="see-more">See all in {name}</Link>
    </div>
  );
}

function Shelf({ title, query, more }: { title: string; query: Record<string, string>; more: string }) {
  const { data, error } = useAsync(() => api<Page<Product>>('/products', { query: { ...query, size: 12 } }), [JSON.stringify(query)]);
  return <ProductShelf title={title} products={data?.items} more={more} error={error} />;
}

/** Things the signed-in customer has had delivered, to reorder in one click. */
function BuyItAgain() {
  const { user } = useAuth();
  const { data } = useAsync(() => (user?.role === 'CUSTOMER' ? api<Product[]>('/me/buy-again').catch((): Product[] => []) : Promise.resolve([] as Product[])), [user?.id]);
  return <ProductShelf title="Buy it again" products={data} more="/orders" />;
}

export function Home() {
  const categories = useCategories();
  return (
    <div className="home">
      <Hero />
      <div className="home-body">
        <div className="cat-cards">
          {categories.slice(0, 8).map((c) => <CategoryCard key={c.id} name={c.name} slug={c.slug} />)}
        </div>
        <BuyItAgain />
        <Shelf title="Today's Deals" query={{ deals: 'true', sort: 'discount' }} more="/deals" />
        <Shelf title="Best sellers" query={{ sort: 'popular' }} more="/products?sort=popular" />
        <Shelf title="Top rated" query={{ sort: 'rating' }} more="/products?sort=rating&minRating=4" />
        <Shelf title="New arrivals" query={{ sort: 'newest' }} more="/products?sort=newest" />
        <RecentlyViewed />
      </div>
    </div>
  );
}
