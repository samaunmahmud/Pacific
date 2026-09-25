import { useRef, useState, type DragEvent } from 'react';
import { ACCEPTED, MAX_MB, uploadPhoto } from './PhotoField';
import { ProductImage } from './ProductImage';

/** Photos after the main one; the shop allows eight in all. */
export const MAX_MORE_PHOTOS = 7;

/**
 * The extra photos shoppers can flick through on the product page: add several at once (click or drop), reorder,
 * remove, or make one the main photo.
 */
export function MorePhotosField({ value, onChange, onMakeMain, onBusyChange, name, categoryName }: {
  value: string[];
  onChange: (urls: string[]) => void;
  onMakeMain: (index: number) => void;
  onBusyChange?: (busy: boolean) => void;
  name: string;
  categoryName?: string | null;
}) {
  const input = useRef<HTMLInputElement>(null);
  const [uploading, setUploading] = useState(0);
  const [error, setError] = useState('');
  const [dragging, setDragging] = useState(false);
  const room = MAX_MORE_PHOTOS - value.length;

  async function add(files: File[]) {
    setError('');
    const chosen = files.slice(0, room);
    const problems: string[] = [];
    if (files.length > room) problems.push(`Only ${MAX_MORE_PHOTOS} more photos fit, so ${files.length - room} ${files.length - room === 1 ? 'was' : 'were'} left out.`);
    if (chosen.length === 0) return setError(problems.join(' '));
    setUploading(chosen.length);
    onBusyChange?.(true);
    let urls = value;
    for (const file of chosen) {
      try {
        urls = [...urls, await uploadPhoto(file)];
        onChange(urls);
      } catch (err) {
        problems.push(`${file.name}: ${err instanceof Error ? err.message : 'could not be uploaded.'}`);
      }
      setUploading((n) => n - 1);
    }
    onBusyChange?.(false);
    if (input.current) input.current.value = '';
    setError(problems.join(' '));
  }

  function move(i: number, by: number) {
    const next = [...value];
    [next[i], next[i + by]] = [next[i + by], next[i]];
    onChange(next);
  }

  function drop(e: DragEvent) {
    e.preventDefault();
    setDragging(false);
    if (!uploading) void add([...e.dataTransfer.files]);
  }

  return (
    <div className="more-photos">
      {value.length > 0 && (
        <ol className="more-photos-list">
          {value.map((url, i) => (
            <li key={url} className="more-photo">
              <div className="more-photo-img"><ProductImage imageUrl={url} categoryName={categoryName} alt={`${name || 'Product'} photo ${i + 2}`} /></div>
              <div className="more-photo-actions">
                <button type="button" className="more-photo-btn" onClick={() => move(i, -1)} disabled={i === 0} aria-label={`Move photo ${i + 2} earlier`}>←</button>
                <button type="button" className="more-photo-btn" onClick={() => move(i, 1)} disabled={i === value.length - 1} aria-label={`Move photo ${i + 2} later`}>→</button>
                <button type="button" className="more-photo-btn" onClick={() => onChange(value.filter((_, j) => j !== i))} aria-label={`Remove photo ${i + 2}`}>✕</button>
              </div>
              <button type="button" className="photo-link" onClick={() => onMakeMain(i)}>Make main photo</button>
            </li>
          ))}
        </ol>
      )}
      {room > 0 ? (
        <div
          className={`photo-drop more-photos-drop${dragging ? ' dragging' : ''}`}
          onDragOver={(e) => { e.preventDefault(); setDragging(true); }}
          onDragLeave={() => setDragging(false)}
          onDrop={drop}
        >
          <button type="button" className="ghost-btn" onClick={() => input.current?.click()} disabled={uploading > 0}>
            {uploading > 0 ? `Uploading ${uploading}…` : 'Add more photos'}
          </button>
          <span className="photo-hint">Drop photos here or choose several at once. Room for {room} more, up to {MAX_MB} MB each.</span>
          <input ref={input} type="file" accept={ACCEPTED.join(',')} multiple hidden onChange={(e) => void add([...(e.target.files ?? [])])} />
        </div>
      ) : (
        <span className="photo-hint">That's the most photos a product can have (8). Remove one to add another.</span>
      )}
      {error && <div className="notice error" role="alert">{error}</div>}
    </div>
  );
}
