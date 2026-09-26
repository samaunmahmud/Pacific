import { useNavigate } from 'react-router-dom';
import type { VariationOption, Variations } from '../api/types';
import { money } from '../ui/format';
import { ProductImage } from './ProductImage';

const same = (a: string | null, b: string | null) => (a ?? '').toLowerCase() === (b ?? '').toLowerCase();
const distinct = (values: string[]) => values.filter((v, i) => values.findIndex((w) => same(v, w)) === i);

/**
 * Switches between a product's variations (colours, sizes...). Each variation is its own product page, so picking one
 * opens that page. With two dimensions, a first-dimension choice keeps the current second one when it exists.
 */
export function VariationPicker({ variations: v, categoryName }: { variations: Variations; categoryName?: string | null }) {
  const navigate = useNavigate();
  const go = (o: VariationOption) => navigate(`/products/${o.productId}`, { replace: true });
  const firsts = distinct(v.options.map((o) => o.option1));
  const seconds = v.dim2 ? distinct(v.options.map((o) => o.option2 ?? '')) : [];
  const pickFirst = (value: string) => v.options.find((o) => same(o.option1, value) && same(o.option2, v.option2))
    ?? v.options.find((o) => same(o.option1, value))!;
  const pickSecond = (value: string) => v.options.find((o) => same(o.option1, v.option1) && same(o.option2, value));
  // Pictures on the first choices only help when they differ (a colour, not a size).
  const pictures = new Set(firsts.map((f) => pickFirst(f).imageUrl)).size > 1;

  return (
    <div className="variations">
      <div className="var-dim">
        <div className="var-label" id="var-dim1">{v.dim1}: <b>{v.option1}</b></div>
        <div className="var-options" role="group" aria-labelledby="var-dim1">
          {firsts.map((value) => {
            const target = pickFirst(value);
            const selected = same(value, v.option1);
            return (
              <button key={value} type="button" aria-pressed={selected} className={`var-swatch ${pictures ? 'with-picture' : ''} ${selected ? 'selected' : ''} ${target.inStock ? '' : 'unavailable'}`}
                onClick={() => !selected && go(target)} aria-label={`${v.dim1} ${value}${target.inStock ? '' : ', currently unavailable'}`}>
                {pictures && <span className="var-thumb" aria-hidden="true"><ProductImage imageUrl={target.imageUrl} categoryName={categoryName} alt="" /></span>}
                <span className="var-name">{value}</span>
                <span className="var-price">{target.inStock ? money(target.price) : 'Unavailable'}</span>
              </button>
            );
          })}
        </div>
      </div>
      {v.dim2 && (
        <div className="var-dim">
          <div className="var-label" id="var-dim2">{v.dim2}: <b>{v.option2}</b></div>
          <div className="var-options" role="group" aria-labelledby="var-dim2">
            {seconds.map((value) => {
              const target = pickSecond(value);
              const selected = same(value, v.option2);
              return (
                <button key={value} type="button" aria-pressed={selected} disabled={!target}
                  className={`var-size ${selected ? 'selected' : ''} ${target && !target.inStock ? 'unavailable' : ''}`}
                  title={!target ? `Not available in ${v.option1}` : target.inStock ? money(target.price) : 'Currently unavailable'}
                  onClick={() => target && !selected && go(target)}>
                  {value}
                </button>
              );
            })}
          </div>
        </div>
      )}
    </div>
  );
}
