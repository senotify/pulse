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
    if (!form.displayName.trim()) return 'Name is required.';
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
    <div className="auth">
      <div className="auth-art" aria-hidden="true">
        <svg viewBox="0 0 32 32">
          <path d="M3 17h7l3.5-9 5 17 3.5-8h7" fill="none" stroke="#fff" strokeWidth="2.2" strokeLinecap="round" strokeLinejoin="round" />
        </svg>
      </div>
      <main className="auth-main">
        <form className="auth-form" onSubmit={submit}>
          <PulseMark className="mark" />
          <h1>{register ? 'Join Pulse today.' : 'Happening now'}</h1>
          <p className="lead">{register ? 'Create your account' : 'Sign in to Pulse'}</p>
          {register ? (
            <>
              <input className="input" placeholder="Name" aria-label="Name" value={form.displayName} onChange={set('displayName')} maxLength={50} required />
              <input className="input" placeholder="Username" aria-label="Username" value={form.username} onChange={set('username')} autoComplete="username" required />
              <input className="input" type="email" placeholder="Email" aria-label="Email" value={form.email} onChange={set('email')} autoComplete="email" required />
            </>
          ) : (
            <input className="input" placeholder="Username or email" aria-label="Username or email" value={form.login} onChange={set('login')} autoComplete="username" required />
          )}
          <input
            className="input"
            type="password"
            placeholder="Password"
            aria-label="Password"
            value={form.password}
            onChange={set('password')}
            autoComplete={register ? 'new-password' : 'current-password'}
            maxLength={72}
            required
          />
          {error && (
            <div className="error" role="alert">
              {error}
            </div>
          )}
          <button className="btn btn-primary btn-large btn-block" disabled={busy}>
            {busy ? 'Please wait…' : register ? 'Create account' : 'Sign in'}
          </button>
          <p className="muted" style={{ margin: 0 }}>
            {register ? (
              <>
                Already have an account? <Link to="/login" className="link">Sign in</Link>
              </>
            ) : (
              <>
                Don't have an account? <Link to="/register" className="link">Sign up</Link>
              </>
            )}
          </p>
        </form>
      </main>
    </div>
  );
}
