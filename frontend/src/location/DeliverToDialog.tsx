import { useEffect, useRef, useState, type FormEvent } from 'react';
import { Link, useLocation } from 'react-router-dom';
import { api } from '../api/client';
import { useAuth } from '../auth/AuthContext';
import { useDeliverTo, type PostcodePlace } from './DeliverToContext';

/** Choosing where to deliver: a saved address, a UK postcode, or the postcode nearest this device. */
export function DeliverToDialog({ onClose }: { onClose: () => void }) {
  const { user } = useAuth();
  const where = useLocation();
  const deliver = useDeliverTo();
  const ref = useRef<HTMLDialogElement>(null);
  const [postcode, setPostcode] = useState(deliver.postcode?.postcode ?? '');
  const [error, setError] = useState('');
  const [busy, setBusy] = useState<'postcode' | 'device' | null>(null);
  const customer = user?.role === 'CUSTOMER';

  useEffect(() => {
    const d = ref.current;
    if (d && !d.open) d.showModal();
  }, []);

  function done(p: PostcodePlace) {
    deliver.choosePostcode(p);
    onClose();
  }

  async function lookUp(e: FormEvent) {
    e.preventDefault();
    if (!postcode.trim()) return setError('Enter a UK postcode, like UB8 1AA.');
    setBusy('postcode');
    setError('');
    try {
      done(await api<PostcodePlace>(`/location/postcode/${encodeURIComponent(postcode.trim())}`));
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Could not look up that postcode.');
    } finally {
      setBusy(null);
    }
  }

  function useDevice() {
    if (!('geolocation' in navigator)) return setError('This browser can\'t share your location. Please enter your postcode.');
    setBusy('device');
    setError('');
    navigator.geolocation.getCurrentPosition(async (pos) => {
      try {
        done(await api<PostcodePlace>('/location/near', { query: { lat: pos.coords.latitude.toFixed(5), lon: pos.coords.longitude.toFixed(5) } }));
      } catch (err) {
        setError(err instanceof Error ? err.message : 'Could not find your location.');
      } finally {
        setBusy(null);
      }
    }, () => {
      setBusy(null);
      setError('We couldn\'t get your location (it may be blocked for this site). Please enter your postcode.');
    }, { timeout: 10000, maximumAge: 600000 });
  }

  return (
    <dialog ref={ref} className="deliver-dialog" aria-labelledby="deliver-h" onClose={onClose}
      onClick={(e) => { if (e.target === ref.current) ref.current?.close(); }}>
      <div className="deliver-dialog-body">
        <div className="row">
          <h2 id="deliver-h">Choose your location</h2>
          <span className="spacer" />
          <button type="button" className="mini-icon-btn" aria-label="Close" onClick={() => ref.current?.close()}>✕</button>
        </div>
        <p className="muted">Pick where your order goes. Checkout starts with this address, and you can still change it there.</p>

        {customer && deliver.addresses && deliver.addresses.length > 0 && (
          <ul className="deliver-addresses" aria-label="Your addresses">
            {deliver.addresses.map((a) => {
              const selected = deliver.address?.id === a.id;
              return (
                <li key={a.id}>
                  <button type="button" className={`deliver-address ${selected ? 'selected' : ''}`} aria-pressed={selected}
                    onClick={() => { deliver.chooseAddress(a.id); onClose(); }}>
                    <b>{a.name}</b> {a.line1}, {a.city} {a.postcode}
                    {a.isDefault && <span className="muted"> · Default address</span>}
                  </button>
                </li>
              );
            })}
          </ul>
        )}
        {customer ? (
          <Link to="/account/addresses" className="see-more" onClick={onClose}>
            {deliver.addresses && deliver.addresses.length > 0 ? 'Manage your addresses' : 'Add an address to your account'}
          </Link>
        ) : !user && (
          <Link to="/login" state={{ next: where.pathname + where.search }} className="submit-btn deliver-signin" onClick={onClose}>Sign in to see your addresses</Link>
        )}

        <div className="deliver-or"><span>or enter a UK postcode</span></div>
        <form className="row" onSubmit={lookUp}>
          <input className="rounded-input" value={postcode} onChange={(e) => setPostcode(e.target.value)} aria-label="UK postcode"
            placeholder="e.g. UB8 3PH" maxLength={10} autoComplete="postal-code" autoCapitalize="characters" />
          <button className="submit-btn" disabled={busy !== null}>{busy === 'postcode' ? 'Checking…' : 'Apply'}</button>
        </form>
        <button type="button" className="cart-link-btn deliver-device" onClick={useDevice} disabled={busy !== null}>
          <span aria-hidden="true">📍 </span>{busy === 'device' ? 'Finding you…' : 'Use my current location'}
        </button>
        {error && <div className="notice error" role="alert">{error}</div>}
      </div>
    </dialog>
  );
}
