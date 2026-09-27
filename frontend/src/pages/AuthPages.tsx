import { useEffect, useId, useState, type FormEvent, type InputHTMLAttributes, type ReactNode } from 'react';
import { Link, Navigate, useLocation, useNavigate, useSearchParams } from 'react-router-dom';
import adminAvatar from '../assets/avatar.png';
import customerAvatar from '../assets/avatarCustomerLogin.png';
import { api, ApiError } from '../api/client';
import type { User } from '../api/types';
import { useAuth } from '../auth/AuthContext';
import { useTheme } from '../theme/ThemeContext';

type Portal = 'customer' | 'admin';

const BRAND: Record<Portal, { image: string; eyebrow: string; headline: string; points: string[] }> = {
  customer: {
    image: customerAvatar,
    eyebrow: 'Pacific Marketplace',
    headline: 'Everything you love, from stores you can trust.',
    points: ['Secure checkout by card or pay on delivery', 'Track every order, with easy returns', 'Products from independent sellers, all in one basket'],
  },
  admin: {
    image: adminAvatar,
    eyebrow: 'Pacific Admin',
    headline: 'Run the marketplace from one place.',
    points: ['Orders, returns and refunds', 'Sellers, commission and payouts', 'Review moderation and the email log'],
  },
};

/** Sign-in pages: a branded panel with the avatar on the left, the form on the right (stacked on phones). */
function AuthShell({ portal = 'customer', title, subtitle, children, footer }: {
  portal?: Portal;
  title: string;
  subtitle?: ReactNode;
  children: ReactNode;
  footer?: ReactNode;
}) {
  const brand = BRAND[portal];
  const { theme, toggle: toggleTheme } = useTheme();
  return (
    <div className={`auth-page auth-${portal}`}>
      <aside className="auth-brand">
        <Link to={portal === 'admin' ? '/admin/login' : '/'} className="auth-logo" aria-label="Pacific home">
          Pacific<span aria-hidden="true">★</span>
        </Link>
        <div className="auth-brand-body">
          <div className="auth-avatar"><img src={brand.image} alt="" /></div>
          <p className="auth-eyebrow">{brand.eyebrow}</p>
          <p className="auth-headline">{brand.headline}</p>
          <ul className="auth-points">
            {brand.points.map((p) => <li key={p}>{p}</li>)}
          </ul>
        </div>
        <p className="auth-brand-foot">© Pacific Marketplace</p>
      </aside>
      <main className="auth-main">
        <div className="auth-top">
          {portal === 'admin' ? <span className="auth-badge">Staff only</span> : <Link to="/" className="auth-back">← Back to the shop</Link>}
          <button type="button" className="auth-theme" onClick={toggleTheme}>{theme === 'dark' ? '☀ Light' : '☾ Dark'}</button>
        </div>
        <div className="auth-panel">
          <h1 className="login-header">{title}</h1>
          {subtitle && <p className="auth-subtitle">{subtitle}</p>}
          {children}
        </div>
        {footer && <div className="auth-footer">{footer}</div>}
      </main>
    </div>
  );
}

/** A labelled text field. The label stays visible (placeholders vanish as soon as you type). */
function Field({ label, hint, aside, ...input }: { label: string; hint?: string; aside?: ReactNode } & InputHTMLAttributes<HTMLInputElement>) {
  const id = useId();
  return (
    <div className="auth-field">
      <div className="auth-label-row">
        <label htmlFor={id}>{label}</label>
        {aside}
      </div>
      <input id={id} className="modern-input" aria-label={label} aria-describedby={hint ? `${id}-hint` : undefined} {...input} />
      {hint && <span id={`${id}-hint`} className="auth-hint">{hint}</span>}
    </div>
  );
}

/** A password field with a show/hide button. */
function PasswordField(props: { label: string; hint?: string; aside?: ReactNode } & InputHTMLAttributes<HTMLInputElement>) {
  const [shown, setShown] = useState(false);
  const id = useId();
  const { label, hint, aside, ...input } = props;
  return (
    <div className="auth-field">
      <div className="auth-label-row">
        <label htmlFor={id}>{label}</label>
        {aside}
      </div>
      <div className="auth-password">
        <input id={id} className="modern-input" type={shown ? 'text' : 'password'} aria-label={label} aria-describedby={hint ? `${id}-hint` : undefined} {...input} />
        <button type="button" className="auth-reveal" onClick={() => setShown((s) => !s)} aria-label={shown ? `Hide ${label.toLowerCase()}` : `Show ${label.toLowerCase()}`} aria-pressed={shown}>
          {shown ? 'Hide' : 'Show'}
        </button>
      </div>
      {hint && <span id={`${id}-hint`} className="auth-hint">{hint}</span>}
    </div>
  );
}

function nextPath(state: unknown): string | undefined {
  const next = (state as { next?: unknown } | null)?.next;
  // only follow in-app paths (avoids being bounced to another site)
  return typeof next === 'string' && next.startsWith('/') && !next.startsWith('//') ? next : undefined;
}

export function CustomerLogin() {
  const { user, login } = useAuth();
  const navigate = useNavigate();
  const location = useLocation();
  const [identifier, setIdentifier] = useState('');
  const [password, setPassword] = useState('');
  const [error, setError] = useState('');
  const [busy, setBusy] = useState(false);

  if (user) return <Navigate to={user.role === 'ADMIN' ? '/admin' : nextPath(location.state) ?? '/'} replace />;

  async function submit(e: FormEvent) {
    e.preventDefault();
    setError('');
    setBusy(true);
    try {
      await login(identifier, password, 'customer');
      navigate(nextPath(location.state) ?? '/', { replace: true });
    } catch (err) {
      setError(err instanceof ApiError ? err.message : 'Sign-in failed. Please try again.');
    } finally {
      setBusy(false);
    }
  }

  return (
    <AuthShell title="Sign in" subtitle="Welcome back. Sign in to your Pacific account."
      footer={<>Pacific staff? <Link to="/admin/login">Sign in to the admin portal</Link></>}>
      <form className="auth-form" onSubmit={submit}>
        <Field label="Email" type="email" autoComplete="username" value={identifier} onChange={(e) => setIdentifier(e.target.value)} required autoFocus />
        <PasswordField label="Password" autoComplete="current-password" value={password} onChange={(e) => setPassword(e.target.value)} required
          aside={<Link className="auth-aside-link" to="/forgot-password">Forgot password?</Link>} />
        {error && <div className="notice error" role="alert">{error}</div>}
        <button className="login-button" disabled={busy}>{busy ? 'Signing in…' : 'Sign in'}</button>
        <div className="auth-divider"><span>New to Pacific?</span></div>
        <Link className="auth-secondary" to="/register" state={location.state}>Create an account</Link>
      </form>
    </AuthShell>
  );
}

export function Register() {
  const { user, register } = useAuth();
  const navigate = useNavigate();
  const location = useLocation();
  const [name, setName] = useState('');
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [error, setError] = useState('');
  const [busy, setBusy] = useState(false);

  if (user) return <Navigate to={nextPath(location.state) ?? '/'} replace />;

  async function submit(e: FormEvent) {
    e.preventDefault();
    setError('');
    setBusy(true);
    try {
      await register(name, email, password);
      navigate(nextPath(location.state) ?? '/', { replace: true });
    } catch (err) {
      setError(err instanceof ApiError ? err.message : 'Could not create your account.');
    } finally {
      setBusy(false);
    }
  }

  return (
    <AuthShell title="Create your account" subtitle="It takes a minute. We'll email you a link to confirm your address."
      footer={<>Already have an account? <Link to="/login" state={location.state}>Sign in</Link></>}>
      <form className="auth-form" onSubmit={submit}>
        <Field label="Full name" autoComplete="name" value={name} onChange={(e) => setName(e.target.value)} required maxLength={120} autoFocus />
        <Field label="Email" type="email" autoComplete="email" value={email} onChange={(e) => setEmail(e.target.value)} required maxLength={190} />
        <PasswordField label="Password" hint="At least 8 characters." autoComplete="new-password" value={password} onChange={(e) => setPassword(e.target.value)} required minLength={8} maxLength={72} />
        {error && <div className="notice error" role="alert">{error}</div>}
        <button className="login-button" disabled={busy}>{busy ? 'Creating your account…' : 'Create account'}</button>
      </form>
    </AuthShell>
  );
}

export function AdminLogin() {
  const { user, login } = useAuth();
  const navigate = useNavigate();
  const [identifier, setIdentifier] = useState('');
  const [password, setPassword] = useState('');
  const [error, setError] = useState('');
  const [busy, setBusy] = useState(false);

  if (user) return <Navigate to={user.role === 'ADMIN' ? '/admin' : '/'} replace />;

  async function submit(e: FormEvent) {
    e.preventDefault();
    setError('');
    setBusy(true);
    try {
      await login(identifier, password, 'admin');
      navigate('/admin', { replace: true });
    } catch (err) {
      setError(err instanceof ApiError ? err.message : 'Sign-in failed. Please try again.');
    } finally {
      setBusy(false);
    }
  }

  return (
    <AuthShell portal="admin" title="Admin sign in" subtitle="Sign in with your Admin ID to manage the marketplace."
      footer={<>Shopping instead? <Link to="/login">Go to customer sign in</Link></>}>
      <form className="auth-form" onSubmit={submit}>
        <Field label="Admin ID" autoComplete="username" value={identifier} onChange={(e) => setIdentifier(e.target.value)} required autoFocus />
        <PasswordField label="Password" autoComplete="current-password" value={password} onChange={(e) => setPassword(e.target.value)} required />
        {error && <div className="notice error" role="alert">{error}</div>}
        <button className="login-button" disabled={busy}>{busy ? 'Signing in…' : 'Sign in to admin'}</button>
      </form>
    </AuthShell>
  );
}

/** Ask for a password reset email. The answer is the same whether or not the address has an account. */
export function ForgotPassword() {
  const [email, setEmail] = useState('');
  const [sent, setSent] = useState('');
  const [error, setError] = useState('');
  const [busy, setBusy] = useState(false);

  async function submit(e: FormEvent) {
    e.preventDefault();
    setError('');
    setBusy(true);
    try {
      const res = await api<{ message: string }>('/auth/forgot-password', { method: 'POST', body: { email: email.trim() } });
      setSent(res.message);
    } catch (err) {
      setError(err instanceof ApiError ? err.message : 'Could not send the email. Please try again.');
    } finally {
      setBusy(false);
    }
  }

  return (
    <AuthShell title="Reset your password" subtitle={sent ? undefined : "Enter the email you signed up with and we'll send you a link to choose a new password."}
      footer={<>Remembered it? <Link to="/login">Back to sign in</Link></>}>
      {sent ? (
        <div className="auth-form">
          <div className="notice ok" role="status">{sent}</div>
          <p className="auth-hint">The link works for an hour. Check your spam folder if it doesn't arrive.</p>
        </div>
      ) : (
        <form className="auth-form" onSubmit={submit}>
          <Field label="Email" type="email" autoComplete="email" value={email} onChange={(e) => setEmail(e.target.value)} required autoFocus />
          {error && <div className="notice error" role="alert">{error}</div>}
          <button className="login-button" disabled={busy}>{busy ? 'Sending…' : 'Send reset link'}</button>
        </form>
      )}
    </AuthShell>
  );
}

export function ResetPassword() {
  const [params] = useSearchParams();
  const [token] = useState(() => params.get('token') ?? '');
  const [password, setPassword] = useState('');
  const [again, setAgain] = useState('');
  const [error, setError] = useState('');
  const [done, setDone] = useState(false);
  const [busy, setBusy] = useState(false);

  // keep the secret out of the browser history and out of any Referer header
  useEffect(() => {
    if (window.location.search) window.history.replaceState(null, '', window.location.pathname);
  }, []);

  async function submit(e: FormEvent) {
    e.preventDefault();
    setError('');
    if (password !== again) return setError("The two passwords don't match.");
    setBusy(true);
    try {
      await api('/auth/reset-password', { method: 'POST', body: { token, password } });
      setDone(true);
    } catch (err) {
      setError(err instanceof ApiError ? err.message : 'Could not reset your password.');
    } finally {
      setBusy(false);
    }
  }

  if (!token) {
    return (
      <AuthShell title="Reset your password">
        <div className="auth-form">
          <div className="notice error" role="alert">This reset link is incomplete. Please use the link from your email, or ask for a new one.</div>
          <Link className="auth-secondary" to="/forgot-password">Ask for a new link</Link>
        </div>
      </AuthShell>
    );
  }
  return (
    <AuthShell title="Choose a new password" subtitle={done ? undefined : 'Pick something you haven\'t used on Pacific before.'}>
      {done ? (
        <div className="auth-form">
          <div className="notice ok" role="status">Your password has been changed. You've been signed out everywhere else.</div>
          <Link className="login-button link-btn" to="/login">Sign in</Link>
        </div>
      ) : (
        <form className="auth-form" onSubmit={submit}>
          <PasswordField label="New password" hint="At least 8 characters." autoComplete="new-password" value={password} onChange={(e) => setPassword(e.target.value)} required minLength={8} maxLength={72} autoFocus />
          <PasswordField label="Repeat the new password" autoComplete="new-password" value={again} onChange={(e) => setAgain(e.target.value)} required minLength={8} maxLength={72} />
          {error && <div className="notice error" role="alert">{error} {error.includes('expired') && <Link to="/forgot-password">Ask for a new link</Link>}</div>}
          <button className="login-button" disabled={busy}>{busy ? 'Saving…' : 'Change password'}</button>
        </form>
      )}
    </AuthShell>
  );
}

/** Where the link in the sign-up email lands. Works signed in or not: people often open it on their phone. */
export function VerifyEmail() {
  const [params] = useSearchParams();
  const [token] = useState(() => params.get('token') ?? '');
  const { user, setUserData } = useAuth();
  const [state, setState] = useState<'working' | 'done' | 'failed'>(token ? 'working' : 'failed');
  const [error, setError] = useState(token ? '' : 'This confirmation link is incomplete. Please use the link from your email.');

  // keep the secret out of the browser history and out of any Referer header
  useEffect(() => {
    if (window.location.search) window.history.replaceState(null, '', window.location.pathname);
  }, []);

  useEffect(() => {
    if (!token) return;
    api<User>('/auth/verify-email', { method: 'POST', body: { token } })
      .then((confirmed) => {
        setState('done');
        if (user?.id === confirmed.id) setUserData(confirmed);
      })
      .catch((err) => {
        setState('failed');
        setError(err instanceof ApiError ? err.message : 'Could not confirm your email.');
      });
    // once per link (the signed-in user may still be loading, which is fine: they are only updated if it is them)
  }, [token]);

  return (
    <AuthShell title="Confirm your email">
      <div className="auth-form">
        {state === 'working' && <div className="notice" role="status">Confirming your email…</div>}
        {state === 'done' && (
          <>
            <div className="notice ok" role="status">Thanks, your email is confirmed. You can now place orders and sell on Pacific.</div>
            <Link className="login-button link-btn" to={user ? '/' : '/login'}>{user ? 'Continue shopping' : 'Sign in'}</Link>
          </>
        )}
        {state === 'failed' && (
          <>
            <div className="notice error" role="alert">{error}</div>
            <Link className="auth-secondary" to={user ? '/' : '/login'}>{user ? 'Back to the shop (you can ask for a new link there)' : 'Sign in to ask for a new link'}</Link>
          </>
        )}
      </div>
    </AuthShell>
  );
}
