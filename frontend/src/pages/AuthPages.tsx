import { useState, type FormEvent, type ReactNode } from 'react';
import { Link, Navigate, useLocation, useNavigate } from 'react-router-dom';
import adminAvatar from '../assets/avatar.png';
import customerAvatar from '../assets/avatarCustomerLogin.png';
import { ApiError } from '../api/client';
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
