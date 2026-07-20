import { useState, useEffect } from 'react';
import { useApp } from '../../context/AppContext.jsx';
import AlertBox from '../ui/AlertBox.jsx';
const API = import.meta.env.VITE_API_URL ?? '';

export default function ResetPasswordView() {
  const { setCurrentView } = useApp();
  const [token,      setToken]      = useState('');
  const [password,   setPassword]   = useState('');
  const [confirm,    setConfirm]    = useState('');
  const [status,     setStatus]     = useState({ type: '', message: '' });
  const [submitting, setSubmitting] = useState(false);
  const [done,       setDone]       = useState(false);

  // Extract token from URL on mount
  useEffect(() => {
    const params = new URLSearchParams(window.location.search);
    const t = params.get('token');
    if (t) {
      setToken(t);
    } else {
      setStatus({ type: 'error', message: 'Invalid or missing reset token.' });
    }
  }, []);

  const handleSubmit = async (e) => {
    e.preventDefault();
    if (password !== confirm) {
      setStatus({ type: 'error', message: 'Passwords do not match.' });
      return;
    }
    if (password.length < 8) {
      setStatus({ type: 'error', message: 'Password must be at least 8 characters.' });
      return;
    }
    setSubmitting(true);
    setStatus({ type: 'info', message: 'Resetting password...' });
    try {
      const res = await fetch(`${API}/reset-password`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ token, new_password: password }),
      });
      const data = await res.json();
      if (!res.ok) {
        setStatus({ type: 'error', message: data.detail ?? 'Reset failed.' });
      } else {
        setDone(true);
        setStatus({ type: 'success', message: 'Password reset! You can now log in.' });
      }
    } catch {
      setStatus({ type: 'error', message: 'Network error. Please try again.' });
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <main className="flex justify-center items-center min-h-[80vh] px-4 py-16">
      <div className="stone-panel w-full max-w-md" style={{ borderRadius: 2 }}>
        <div className="panel-header text-center py-6">
          <h2 className="font-fantasy text-2xl font-bold tracking-widest text-white">RESET PASSWORD</h2>
          <p className="font-fantasy text-xs tracking-[0.3em] mt-2" style={{ color: '#555' }}>
            CHOOSE A NEW PASSWORD
          </p>
        </div>
        <div className="p-8">
          <AlertBox status={status} />
          {!done && token && (
            <form onSubmit={handleSubmit} className="flex flex-col gap-4">
              <input
                type="password"
                placeholder="New password"
                required
                minLength={8}
                className="rpg-input font-fantasy text-sm tracking-wide px-4 py-3"
                value={password}
                onChange={e => setPassword(e.target.value)}
              />
              <input
                type="password"
                placeholder="Confirm new password"
                required
                className="rpg-input font-fantasy text-sm tracking-wide px-4 py-3"
                value={confirm}
                onChange={e => setConfirm(e.target.value)}
              />
              <button
                type="submit"
                disabled={submitting}
                className="btn-download mt-3 py-4 w-full font-fantasy text-sm tracking-widest uppercase"
              >
                {submitting ? 'Resetting...' : 'Reset Password'}
              </button>
            </form>
          )}
          <div className="text-center mt-6">
            <button
              onClick={() => setCurrentView('login')}
              className="font-fantasy text-xs tracking-widest transition-colors"
              style={{ color: '#555' }}
              onMouseOver={e => e.currentTarget.style.color = '#d4af37'}
              onMouseOut={e  => e.currentTarget.style.color = '#555'}
            >
              {done ? 'Go to LOGIN' : 'Back to LOGIN'}
            </button>
          </div>
        </div>
      </div>
    </main>
  );
}
