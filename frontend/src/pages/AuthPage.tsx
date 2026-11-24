import { useState, type FormEvent } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { api } from '../api/client';
import { useAuth } from '../auth/AuthContext';
import { PulseMark } from '../components/Icons';

const USERNAME = /^[A-Za-z0-9_]{3,20}$/;

export function AuthPage({ mode }: { mode: 'login' | 'register' }) {
  const { signIn } = useAuth();
  const navigate = useNavigate();
  const [form, setForm] = useState({ login: '', username: '', email: '', displayName: '', password: '' });
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);
  const register = mode === 'register';

  const set = (key: keyof typeof form) => (e: React.ChangeEvent<HTMLInputElement>) =>
    setForm((f) => ({ ...f, [key]: e.target.value }));

  function validate(): string | null {
    if (!register) return null;
    if (!USERNAME.test(form.username)) return 'Username must be 3-20 letters, digits or underscores.';
    if (form.password.length < 8) return 'Password must be at least 8 characters.';
    if (!form.displayName.trim()) return 'Display name is required.';
    return null;
  }

  async function submit(e: FormEvent) {
    e.preventDefault();
    const problem = validate();
    if (problem) return setError(problem);
    setBusy(true);
    setError(null);
    try {
      const response = register
        ? await api.register({
            username: form.username,
            email: form.email,
            displayName: form.displayName.trim(),
            password: form.password,
          })
        : await api.login({ login: form.login, password: form.password });
      signIn(response);
      navigate('/', { replace: true });
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Something went wrong');
    } finally {
      setBusy(false);
    }
  }

  return (
    <div className="auth-page">
      <form className="card auth-card stack" onSubmit={submit}>
        <div className="auth-hero">
          <PulseMark className="brand-mark" /> Pulse
        </div>
        <div>
          <h1>{register ? 'Create your account' : 'Welcome back'}</h1>
          <p className="muted" style={{ margin: 0 }}>
            {register ? 'A small network with a feed that explains itself.' : 'Sign in to see your feed.'}
          </p>
        </div>
        {register ? (
          <>
            <label className="label">
              Username
              <input className="input" value={form.username} onChange={set('username')} autoComplete="username" required />
            </label>
            <label className="label">
              Display name
              <input className="input" value={form.displayName} onChange={set('displayName')} maxLength={50} required />
            </label>
            <label className="label">
              Email
              <input className="input" type="email" value={form.email} onChange={set('email')} autoComplete="email" required />
            </label>
          </>
        ) : (
          <label className="label">
            Username or email
            <input className="input" value={form.login} onChange={set('login')} autoComplete="username" required />
          </label>
        )}
        <label className="label">
          Password
          <input
            className="input"
            type="password"
            value={form.password}
            onChange={set('password')}
            autoComplete={register ? 'new-password' : 'current-password'}
            minLength={register ? 8 : undefined}
            maxLength={72}
            required
          />
        </label>
        {error && (
          <div className="error" role="alert">
            {error}
          </div>
        )}
        <button className="btn btn-primary btn-block" disabled={busy}>
          {busy ? 'Please wait…' : register ? 'Create account' : 'Sign in'}
        </button>
        <p className="muted small" style={{ textAlign: 'center', margin: 0 }}>
          {register ? (
            <>
              Already have an account? <Link to="/login" className="bold">Sign in</Link>
            </>
          ) : (
            <>
              New to Pulse? <Link to="/register" className="bold">Create an account</Link>
            </>
          )}
        </p>
      </form>
    </div>
  );
}
