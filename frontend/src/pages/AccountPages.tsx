import { useEffect, useState, type FormEvent } from 'react';
import { Link } from 'react-router-dom';
import { api, ApiError } from '../api/client';
import type { AuthResponse, SavedAddress, User } from '../api/types';
import { useAuth } from '../auth/AuthContext';
import { useToast } from '../ui/Toast';
import { useDeliverTo } from '../location/DeliverToContext';
import { useAsync } from '../ui/useAsync';

const message = (e: unknown, fallback: string) => (e instanceof ApiError ? e.message : fallback);

/** "Your account": who you are, your password, and where to find everything else. */
export function AccountHome() {
  const { user, adopt, setName } = useAuth();
  const toast = useToast();
  const [name, setNameField] = useState(user?.name ?? '');
  const [profileBusy, setProfileBusy] = useState(false);
  const [current, setCurrent] = useState('');
  const [next, setNext] = useState('');
  const [again, setAgain] = useState('');
  const [pwError, setPwError] = useState('');
  const [pwBusy, setPwBusy] = useState(false);

  async function saveName(e: FormEvent) {
    e.preventDefault();
    setProfileBusy(true);
    try {
      const updated = await api<User>('/me', { method: 'PATCH', body: { name: name.trim() } });
      setName(updated.name);
      setNameField(updated.name);
      toast.show('Name updated');
    } catch (err) {
      toast.show(message(err, 'Could not update your name.'), 'error');
    } finally {
      setProfileBusy(false);
    }
  }

  async function changePassword(e: FormEvent) {
    e.preventDefault();
    setPwError('');
    if (next !== again) return setPwError("The two new passwords don't match.");
    setPwBusy(true);
    try {
      adopt(await api<AuthResponse>('/me/password', { method: 'POST', body: { currentPassword: current, newPassword: next } }));
      setCurrent('');
      setNext('');
      setAgain('');
      toast.show("Password changed. You've been signed out on your other devices.");
    } catch (err) {
      setPwError(message(err, 'Could not change your password.'));
    } finally {
      setPwBusy(false);
    }
  }

  return (
    <div className="page page-narrow">
      <h1 className="page-title">Your account</h1>

      <div className="account-tiles">
        <Link to="/orders" className="account-tile"><b>Your orders</b><span>Track, return or buy things again</span></Link>
        <Link to="/account/addresses" className="account-tile"><b>Your addresses</b><span>Save where you want things delivered</span></Link>
        <Link to="/account/reviews" className="account-tile"><b>Your reviews</b><span>What you've reviewed and what's waiting</span></Link>
        <Link to="/wishlist" className="account-tile"><b>Your wish list</b><span>Things you've saved for later</span></Link>
      </div>

      <form className="square-review-box static stack" onSubmit={saveName}>
        <h2 style={{ margin: 0 }}>Your details</h2>
        <div className="form-field">
          <label className="field-label small" htmlFor="acct-name">Name</label>
          <input id="acct-name" className="rounded-input" value={name} onChange={(e) => setNameField(e.target.value)} required maxLength={120} autoComplete="name" />
        </div>
        <div className="form-field">
          <label className="field-label small" htmlFor="acct-email">Email</label>
          <input id="acct-email" className="rounded-input" value={user?.email ?? ''} readOnly aria-readonly="true" />
          <span className="muted" style={{ fontSize: 12 }}>This is what you sign in with and where we send order emails.</span>
        </div>
        <div><button className="submit-btn" disabled={profileBusy || name.trim() === '' || name.trim() === user?.name}>{profileBusy ? 'Saving…' : 'Save name'}</button></div>
      </form>

      <form className="square-review-box static stack" onSubmit={changePassword}>
        <h2 style={{ margin: 0 }}>Change your password</h2>
        <p className="muted" style={{ margin: 0 }}>You'll stay signed in here and be signed out everywhere else.</p>
        <div className="form-field">
          <label className="field-label small" htmlFor="pw-current">Current password</label>
          <input id="pw-current" className="rounded-input" type="password" value={current} onChange={(e) => setCurrent(e.target.value)} required autoComplete="current-password" maxLength={72} />
        </div>
        <div className="form-grid">
          <div className="form-field">
            <label className="field-label small" htmlFor="pw-new">New password</label>
            <input id="pw-new" className="rounded-input" type="password" value={next} onChange={(e) => setNext(e.target.value)} required minLength={8} maxLength={72} autoComplete="new-password" />
          </div>
          <div className="form-field">
            <label className="field-label small" htmlFor="pw-again">Repeat the new password</label>
            <input id="pw-again" className="rounded-input" type="password" value={again} onChange={(e) => setAgain(e.target.value)} required minLength={8} maxLength={72} autoComplete="new-password" />
          </div>
        </div>
        {pwError && <div className="notice error" role="alert">{pwError}</div>}
        <div><button className="submit-btn" disabled={pwBusy}>{pwBusy ? 'Changing…' : 'Change password'}</button></div>
      </form>
    </div>
  );
}

interface AddressFields { name: string; line1: string; line2: string; city: string; postcode: string; country: string; makeDefault: boolean }

function AddressForm({ initial, onSaved, onCancel }: { initial: SavedAddress | null; onSaved: () => void; onCancel: () => void }) {
  const [f, setF] = useState<AddressFields>({
    name: initial?.name ?? '', line1: initial?.line1 ?? '', line2: initial?.line2 ?? '', city: initial?.city ?? '',
    postcode: initial?.postcode ?? '', country: initial?.country ?? 'United Kingdom', makeDefault: initial?.isDefault ?? false,
  });
  const [error, setError] = useState('');
  const [busy, setBusy] = useState(false);
  const set = (k: keyof AddressFields) => (e: React.ChangeEvent<HTMLInputElement>) => setF({ ...f, [k]: e.target.type === 'checkbox' ? e.target.checked : e.target.value });

  async function submit(e: FormEvent) {
    e.preventDefault();
    setError('');
    setBusy(true);
    try {
      const body = { ...f, line2: f.line2.trim() || null };
      if (initial) await api(`/me/addresses/${initial.id}`, { method: 'PUT', body });
      else await api('/me/addresses', { method: 'POST', body });
      onSaved();
    } catch (err) {
      setError(message(err, 'Could not save the address.'));
    } finally {
      setBusy(false);
    }
  }

  return (
    <form className="square-review-box static stack" onSubmit={submit}>
      <h2 style={{ margin: 0 }}>{initial ? 'Edit address' : 'Add an address'}</h2>
      <div className="form-grid">
        <div className="form-field full"><label className="field-label small" htmlFor="ad-name">Full name</label><input id="ad-name" className="rounded-input" value={f.name} onChange={set('name')} required maxLength={120} autoComplete="name" /></div>
        <div className="form-field full"><label className="field-label small" htmlFor="ad-line1">Address line 1</label><input id="ad-line1" className="rounded-input" value={f.line1} onChange={set('line1')} required maxLength={160} autoComplete="address-line1" /></div>
        <div className="form-field full"><label className="field-label small" htmlFor="ad-line2">Address line 2 (optional)</label><input id="ad-line2" className="rounded-input" value={f.line2} onChange={set('line2')} maxLength={160} autoComplete="address-line2" /></div>
        <div className="form-field"><label className="field-label small" htmlFor="ad-city">Town / city</label><input id="ad-city" className="rounded-input" value={f.city} onChange={set('city')} required maxLength={80} autoComplete="address-level2" /></div>
        <div className="form-field"><label className="field-label small" htmlFor="ad-postcode">Postcode</label><input id="ad-postcode" className="rounded-input" value={f.postcode} onChange={set('postcode')} required maxLength={20} autoComplete="postal-code" /></div>
        <div className="form-field full"><label className="field-label small" htmlFor="ad-country">Country</label><input id="ad-country" className="rounded-input" value={f.country} onChange={set('country')} required maxLength={80} autoComplete="country-name" /></div>
      </div>
      <label className="row" style={{ gap: 8 }}><input type="checkbox" checked={f.makeDefault} onChange={set('makeDefault')} disabled={initial?.isDefault} /> Make this my default address</label>
      {error && <div className="notice error" role="alert">{error}</div>}
      <div className="row-wrap">
        <button className="submit-btn" disabled={busy}>{busy ? 'Saving…' : 'Save address'}</button>
        <button type="button" className="link-plain" onClick={onCancel}>Cancel</button>
      </div>
    </form>
  );
}

/** Saved delivery addresses, so checkout is one click. */
export function AddressBook() {
  const toast = useToast();
  const { data, error, loading, reload } = useAsync(() => api<SavedAddress[]>('/me/addresses'), []);
  const { syncAddresses } = useDeliverTo();
  useEffect(() => { if (data) syncAddresses(data); }, [data, syncAddresses]); // keep "Deliver to" in step
  const [editing, setEditing] = useState<SavedAddress | 'new' | null>(null);

  async function run(action: () => Promise<unknown>, ok: string) {
    try {
      await action();
      toast.show(ok);
      reload();
    } catch (err) {
      toast.show(message(err, 'That did not work.'), 'error');
    }
  }

  return (
    <div className="page page-narrow">
      <nav className="crumbs"><Link to="/account">Your account</Link> <span aria-hidden="true">›</span> Addresses</nav>
      <div className="row-wrap">
        <h1 className="page-title">Your addresses</h1>
        <span className="spacer" />
        {editing === null && (data?.length ?? 0) < 10 && <button className="submit-btn" onClick={() => setEditing('new')}>+ Add an address</button>}
      </div>
      {error && <div className="notice error">{error}</div>}
      {editing !== null && <AddressForm initial={editing === 'new' ? null : editing} onCancel={() => setEditing(null)} onSaved={() => { setEditing(null); toast.show('Address saved'); reload(); }} />}
      {loading && !data ? <div className="loading">Loading…</div> : data && data.length === 0 && editing === null ? (
        <div className="empty">No saved addresses yet. Add one and checkout will fill it in for you.</div>
      ) : (
        <div className="address-list">
          {data?.map((a) => (
            <article key={a.id} className={`address-card ${a.isDefault ? 'is-default' : ''}`}>
              {a.isDefault && <span className="status-pill status-APPROVED">Default</span>}
              <address>{a.name}<br />{a.line1}<br />{a.line2 && <>{a.line2}<br /></>}{a.city} {a.postcode}<br />{a.country}</address>
              <div className="row-wrap">
                <button className="side-btn" onClick={() => setEditing(a)}>Edit</button>
                {!a.isDefault && <button className="side-btn" onClick={() => run(() => api(`/me/addresses/${a.id}/default`, { method: 'POST' }), 'Default address changed')}>Make default</button>}
                <button className="link-plain" onClick={() => { if (window.confirm('Delete this address?')) void run(() => api(`/me/addresses/${a.id}`, { method: 'DELETE' }), 'Address deleted'); }}>Delete</button>
              </div>
            </article>
          ))}
        </div>
      )}
    </div>
  );
}
