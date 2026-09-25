import { useState } from 'react';
import { api, ApiError } from '../api/client';
import type { User } from '../api/types';
import { useAuth } from '../auth/AuthContext';

/** True for a signed-in customer who hasn't opened the link from their sign-up email yet. */
export function needsConfirmation(user: User | null): boolean {
  return user?.role === 'CUSTOMER' && !user.emailVerified;
}

/**
 * Asks the customer to confirm their email, with "send it again" and "I've done it" buttons. {@code what} finishes
 * "Confirm your email to …"; without it the notice is the site-wide reminder.
 */
export function ConfirmEmailNotice({ what }: { what?: string }) {
  const { user, setUserData } = useAuth();
  const [message, setMessage] = useState('');
  const [error, setError] = useState('');
  const [busy, setBusy] = useState(false);
  if (!user || !needsConfirmation(user)) return null;

  async function resend() {
    setBusy(true);
    setError('');
    setMessage('');
    try {
      setMessage((await api<{ message: string }>('/auth/verify-email/resend', { method: 'POST' })).message);
    } catch (err) {
      setError(err instanceof ApiError ? err.message : 'Could not send the email.');
    } finally {
      setBusy(false);
    }
  }

  async function check() {
    setError('');
    const fresh = await api<User>('/auth/me');
    setUserData(fresh);
    if (!fresh.emailVerified) setError("We haven't seen the link opened yet. Check your inbox (and spam folder) for an email from Pacific.");
  }

  return (
    <div className={`notice confirm-email${what ? '' : ' banner'}`} role="status">
      <span>
        <b>{what ? `Confirm your email to ${what}.` : 'Please confirm your email address.'}</b>{' '}
        We sent a link to <b>{user.email}</b>.
      </span>
      <span className="confirm-email-actions">
        <button type="button" className="photo-link" onClick={resend} disabled={busy}>{busy ? 'Sending…' : 'Send it again'}</button>
        <button type="button" className="photo-link" onClick={() => void check()}>I've confirmed it</button>
      </span>
      {message && <span className="confirm-email-msg">{message}</span>}
      {error && <span className="confirm-email-msg error" role="alert">{error}</span>}
    </div>
  );
}
