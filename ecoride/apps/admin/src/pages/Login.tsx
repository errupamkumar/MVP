import { FormEvent, useState } from 'react';
import { Navigate, useNavigate } from 'react-router-dom';

import { Logo } from '@/components/Layout';
import { Button, ErrorNote, Input } from '@/components/ui';
import { API_URL } from '@/lib/api';
import { useAuth } from '@/lib/auth';

export default function Login() {
  const { session, signIn, notice } = useAuth();
  const navigate = useNavigate();
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<unknown>(null);
  if (session) return <Navigate to="/" replace />;

  async function submit(e?: FormEvent, em = email, pw = password) {
    e?.preventDefault();
    setBusy(true);
    setError(null);
    try {
      await signIn(em, pw);
      navigate('/');
    } catch (err) {
      setError(err);
    } finally {
      setBusy(false);
    }
  }

  return (
    <div className="grid min-h-full lg:grid-cols-2">
      <div className="hidden flex-col justify-between bg-ink p-12 lg:flex">
        <Logo light />
        <div>
          <h1 className="text-4xl font-extrabold leading-tight tracking-tight text-white">
            Every ride, driver and rupee
            <br />
            in one console.
          </h1>
          <p className="mt-4 max-w-md text-ink-4">
            Live fleet map, rides with their GPS traces, driver approvals, fares and surge, promotions, payouts and safety alerts.
          </p>
        </div>
        <p className="text-xs text-ink-3">SRM ECO TECH · EcoRide demo</p>
      </div>
      <div className="flex items-center justify-center p-6">
        <form onSubmit={submit} className="w-full max-w-sm">
          <div className="lg:hidden">
            <Logo />
          </div>
          <h2 className="mt-8 text-2xl font-bold tracking-tight">Sign in to the admin console</h2>
          <p className="mt-1 text-sm text-ink-3">Operations accounts only. Riders and drivers use their own apps.</p>
          {notice ? <div className="mt-4 rounded-lg bg-[#FEF3C7] px-3 py-2 text-sm text-[#92400E]">{notice}</div> : null}
          <div className="mt-6 space-y-4">
            <Input label="Email" type="email" value={email} onChange={(e) => setEmail(e.target.value)} autoComplete="username" data-testid="admin-email" />
            <Input label="Password" type="password" value={password} onChange={(e) => setPassword(e.target.value)} autoComplete="current-password" data-testid="admin-password" />
            <ErrorNote error={error} />
            <div className="grid">
              <Button type="submit" variant="primary" loading={busy} disabled={!email || !password}>
                Sign in
              </Button>
            </div>
          </div>
          <div className="mt-8 rounded-xl bg-mist p-4 text-sm">
            <div className="text-xs font-semibold uppercase tracking-wide text-ink-3">Demo admin</div>
            <div className="mt-1 text-ink-2">admin@ecoride.in · Admin@123</div>
            <button
              type="button"
              className="mt-3 text-sm font-semibold text-brand hover:underline"
              onClick={() => {
                setEmail('admin@ecoride.in');
                setPassword('Admin@123');
                void submit(undefined, 'admin@ecoride.in', 'Admin@123');
              }}
              data-testid="admin-demo-login"
            >
              Sign in as the demo admin →
            </button>
          </div>
          <p className="mt-6 text-center text-xs text-ink-4">Server: {API_URL}</p>
        </form>
      </div>
    </div>
  );
}
