import { useEffect, useState, type FormEvent, type ReactNode } from 'react';
import { Link, Navigate, useLocation, useNavigate, useSearchParams } from 'react-router-dom';
import adminAvatar from '../assets/avatar.png';
import customerAvatar from '../assets/avatarCustomerLogin.png';
import { api, ApiError } from '../api/client';
import type { User } from '../api/types';
import { useAuth } from '../auth/AuthContext';

/** The old split login layout: blush panel with the avatar card on the left, form on the right. */
function AuthShell({ image, heading, title, children }: { image: string; heading: string; title: string; children: ReactNode }) {
  return (
    <div className="auth-page">
      <div className="graphic-container">
        <div className="graphic-card">
          <img src={image} alt="" />
          <h2>{heading}</h2>
        </div>
      </div>
      <div className="auth-form-side">
        <Link to="/" className="logo-text">Pacific ★</Link>
        <h1 className="login-header">{title}</h1>
        {children}
      </div>
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
      setError(err instanceof ApiError ? err.message : 'Login failed.');
    } finally {
      setBusy(false);
    }
  }

  return (
    <AuthShell image={customerAvatar} heading="WELCOME TO PACIFIC" title="Customer Login">
      <form className="auth-form" onSubmit={submit}>
        <input className="modern-input" type="email" placeholder="Email" autoComplete="username" value={identifier} onChange={(e) => setIdentifier(e.target.value)} required aria-label="Email" />
        <input className="modern-input" type="password" placeholder="Password" autoComplete="current-password" value={password} onChange={(e) => setPassword(e.target.value)} required aria-label="Password" />
        {error && <div className="notice error" role="alert">{error}</div>}
        <button className="login-button" disabled={busy}>{busy ? 'SIGNING IN…' : 'LOGIN'}</button>
        <Link className="link" to="/forgot-password">Forgot your password?</Link>
        <Link className="link" to="/register" state={location.state}>New to Pacific? Create an account</Link>
        <Link className="link" to="/admin/login">Are you an Admin? Login here</Link>
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
    <AuthShell image={customerAvatar} heading="JOIN PACIFIC" title="Create Account">
      <form className="auth-form" onSubmit={submit}>
        <input className="modern-input" placeholder="Full name" autoComplete="name" value={name} onChange={(e) => setName(e.target.value)} required maxLength={120} aria-label="Full name" />
        <input className="modern-input" type="email" placeholder="Email" autoComplete="email" value={email} onChange={(e) => setEmail(e.target.value)} required maxLength={190} aria-label="Email" />
        <input className="modern-input" type="password" placeholder="Password (8+ characters)" autoComplete="new-password" value={password} onChange={(e) => setPassword(e.target.value)} required minLength={8} maxLength={72} aria-label="Password" />
        {error && <div className="notice error" role="alert">{error}</div>}
        <button className="login-button" disabled={busy}>{busy ? 'CREATING…' : 'CREATE ACCOUNT'}</button>
        <Link className="link" to="/login" state={location.state}>Already have an account? Login</Link>
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
      setError(err instanceof ApiError ? err.message : 'Login failed.');
    } finally {
      setBusy(false);
    }
  }

  return (
    <AuthShell image={adminAvatar} heading="ADMIN PORTAL" title="Admin Access">
      <form className="auth-form" onSubmit={submit}>
        <input className="modern-input" placeholder="Admin ID" autoComplete="username" value={identifier} onChange={(e) => setIdentifier(e.target.value)} required aria-label="Admin ID" />
        <input className="modern-input" type="password" placeholder="Password" autoComplete="current-password" value={password} onChange={(e) => setPassword(e.target.value)} required aria-label="Password" />
        {error && <div className="notice error" role="alert">{error}</div>}
        <button className="login-button" disabled={busy}>{busy ? 'SIGNING IN…' : 'ADMIN LOGIN'}</button>
        <Link className="link" to="/login">Return to Customer Login</Link>
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
    <AuthShell image={customerAvatar} heading="WELCOME TO PACIFIC" title="Reset your password">
      {sent ? (
        <div className="auth-form">
          <div className="notice ok" role="status">{sent}</div>
          <p className="muted">The link works for an hour. Check your spam folder if it doesn't arrive.</p>
          <Link className="link" to="/login">Back to login</Link>
        </div>
      ) : (
        <form className="auth-form" onSubmit={submit}>
          <p className="muted">Enter the email you signed up with and we'll send you a link to choose a new password.</p>
          <input className="modern-input" type="email" placeholder="Email" autoComplete="email" value={email} onChange={(e) => setEmail(e.target.value)} required aria-label="Email" />
          {error && <div className="notice error" role="alert">{error}</div>}
          <button className="login-button" disabled={busy}>{busy ? 'SENDING…' : 'SEND RESET LINK'}</button>
          <Link className="link" to="/login">Back to login</Link>
        </form>
      )}
    </AuthShell>
  );
}

/** Where the emailed link lands: choose a new password. The token is taken out of the address bar straight away. */
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
      <AuthShell image={customerAvatar} heading="WELCOME TO PACIFIC" title="Reset your password">
        <div className="auth-form">
          <div className="notice error" role="alert">This reset link is incomplete. Please use the link from your email, or ask for a new one.</div>
          <Link className="link" to="/forgot-password">Ask for a new link</Link>
        </div>
      </AuthShell>
    );
  }
  return (
    <AuthShell image={customerAvatar} heading="WELCOME TO PACIFIC" title="Choose a new password">
      {done ? (
        <div className="auth-form">
          <div className="notice ok" role="status">Your password has been changed. You've been signed out everywhere else.</div>
          <Link className="login-button link-btn" to="/login">LOG IN</Link>
        </div>
      ) : (
        <form className="auth-form" onSubmit={submit}>
          <input className="modern-input" type="password" placeholder="New password (8 or more characters)" autoComplete="new-password" value={password} onChange={(e) => setPassword(e.target.value)} required minLength={8} maxLength={72} aria-label="New password" />
          <input className="modern-input" type="password" placeholder="Repeat the new password" autoComplete="new-password" value={again} onChange={(e) => setAgain(e.target.value)} required minLength={8} maxLength={72} aria-label="Repeat the new password" />
          {error && <div className="notice error" role="alert">{error} {error.includes('expired') && <Link to="/forgot-password">Ask for a new link</Link>}</div>}
          <button className="login-button" disabled={busy}>{busy ? 'SAVING…' : 'CHANGE PASSWORD'}</button>
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
    <AuthShell image={customerAvatar} heading="WELCOME TO PACIFIC" title="Confirm your email">
      <div className="auth-form">
        {state === 'working' && <div className="notice" role="status">Confirming your email…</div>}
        {state === 'done' && (
          <>
            <div className="notice ok" role="status">Thanks, your email is confirmed. You can now place orders and sell on Pacific.</div>
            <Link className="login-button link-btn" to={user ? '/' : '/login'}>{user ? 'CONTINUE SHOPPING' : 'LOG IN'}</Link>
          </>
        )}
        {state === 'failed' && (
          <>
            <div className="notice error" role="alert">{error}</div>
            <Link className="link" to={user ? '/' : '/login'}>{user ? 'Back to the shop (you can ask for a new link there)' : 'Sign in to ask for a new link'}</Link>
          </>
        )}
      </div>
    </AuthShell>
  );
}
