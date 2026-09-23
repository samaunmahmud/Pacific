import { useRef, useState, type DragEvent } from 'react';
import { api } from '../api/client';
import type { UploadedImage } from '../api/types';
import { ProductImage } from './ProductImage';

const MAX_MB = 10;
const ACCEPTED = ['image/jpeg', 'image/png', 'image/gif'];

/**
 * A product's photo: upload one (click or drop), remove it, or paste a link to a photo hosted elsewhere. The shop
 * re-saves uploads at a sensible size, so sellers can send photos straight from their phone.
 */
export function PhotoField({ value, onChange, onBusyChange, name, categoryName }: {
  value: string;
  onChange: (url: string) => void;
  onBusyChange?: (busy: boolean) => void;
  name: string;
  categoryName?: string | null;
}) {
  const input = useRef<HTMLInputElement>(null);
  const [uploading, setUploading] = useState(false);
  const [error, setError] = useState('');
  const [dragging, setDragging] = useState(false);
  const [showLink, setShowLink] = useState(/^https?:\/\//.test(value));

  async function upload(file: File) {
    setError('');
    if (!ACCEPTED.includes(file.type)) return setError('Choose a JPEG, PNG or GIF photo.');
    if (file.size > MAX_MB * 1024 * 1024) return setError(`That photo is over ${MAX_MB} MB. Choose a smaller one.`);
    const form = new FormData();
    form.append('file', file);
    setUploading(true);
    onBusyChange?.(true);
    try {
      const uploaded = await api<UploadedImage>('/images', { method: 'POST', body: form });
      onChange(uploaded.url);
      setShowLink(false);
    } catch (err) {
      setError(err instanceof Error ? err.message : 'The photo could not be uploaded.');
    } finally {
      setUploading(false);
      onBusyChange?.(false);
      if (input.current) input.current.value = '';
    }
  }

  function drop(e: DragEvent) {
    e.preventDefault();
    setDragging(false);
    const file = e.dataTransfer.files[0];
    if (file && !uploading) void upload(file);
  }

  const hasPhoto = value.trim() !== '';
  // Uploaded photos and demo drawings aren't links the seller typed, so the link box starts empty for them.
  const isLink = !value.startsWith('/api/images/') && !value.startsWith('demo:');

  return (
    <div className="photo-field">
      <div
        className={`photo-drop${dragging ? ' dragging' : ''}`}
        onDragOver={(e) => { e.preventDefault(); setDragging(true); }}
        onDragLeave={() => setDragging(false)}
        onDrop={drop}
      >
        <div className="photo-preview" aria-hidden={!hasPhoto}>
          {uploading ? <span className="muted">Uploading…</span> : <ProductImage imageUrl={value.trim() || null} categoryName={categoryName} alt={name || 'Product photo'} />}
        </div>
        <div className="stack photo-actions">
          <span className="photo-hint">
            {hasPhoto ? 'This photo shows on the storefront.' : 'No photo yet: shoppers see a drawing for the category.'}
            {' '}Drop a photo here or choose one. JPEG, PNG or GIF, up to {MAX_MB} MB.
          </span>
          <div className="row-wrap">
            <button type="button" className="ghost-btn" onClick={() => input.current?.click()} disabled={uploading}>
              {uploading ? 'Uploading…' : hasPhoto ? 'Replace photo' : 'Upload photo'}
            </button>
            {hasPhoto && !uploading && <button type="button" className="ghost-btn" onClick={() => { onChange(''); setError(''); }}>Remove</button>}
            {!showLink && <button type="button" className="photo-link" onClick={() => setShowLink(true)}>Use a link instead</button>}
          </div>
        </div>
        <input ref={input} type="file" accept={ACCEPTED.join(',')} hidden onChange={(e) => { const f = e.target.files?.[0]; if (f) void upload(f); }} />
      </div>
      {showLink && (
        <div className="form-field">
          <label className="field-label small" htmlFor="pi">Link to a photo</label>
          <input id="pi" className="rounded-input" type="url" value={isLink ? value : ''} onChange={(e) => onChange(e.target.value)} placeholder="https://…" maxLength={500} />
        </div>
      )}
      {error && <div className="notice error" role="alert">{error}</div>}
    </div>
  );
}
