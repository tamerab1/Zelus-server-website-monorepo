import { useState, useEffect } from 'react';
import { useApp } from '../../context/AppContext.jsx';
import AlertBox from '../ui/AlertBox.jsx';
const API = import.meta.env.VITE_API_URL ?? '';

export default function VerifyEmailView() {
  const { setCurrentView } = useApp();
  const [status, setStatus] = useState({ type: 'info', message: 'Verifying your email...' });
  const [done,   setDone]   = useState(false);

  useEffect(() => {
    const params = new URLSearchParams(window.location.search);
    const token = params.get('token');
    if (!token) {
      setStatus({ type: 'error', message: 'Invalid verification link.' });
      setDone(true);
      return;
    }
    fetch(`${API}/verify-email?token=${encodeURIComponent(token)}`)
      .then(res => res.json().then(data => ({ ok: res.ok, data })))
      .then(({ ok, data }) => {
        if (ok) {
          setStatus({ type: 'success', message: 'Email verified! You can now log in.' });
        } else {
          setStatus({ type: 'error', message: data.detail ?? 'Verification failed or link has expired.' });
        }
        setDone(true);
      })
      .catch(() => {
        setStatus({ type: 'error', message: 'Network error. Please try again.' });
        setDone(true);
      });
  }, []);

  return (
    <main className="flex justify-center items-center min-h-[80vh] px-4 py-16">
      <div className="stone-panel w-full max-w-md" style={{ borderRadius: 2 }}>
        <div className="panel-header text-center py-6">
          <h2 className="font-fantasy text-2xl font-bold tracking-widest text-white">EMAIL VERIFICATION</h2>
        </div>
        <div className="p-8">
          <AlertBox status={status} />
          {done && (
            <div className="text-center mt-6">
              <button
                onClick={() => setCurrentView('login')}
                className="btn-download py-4 w-full font-fantasy text-sm tracking-widest uppercase"
              >
                Go to Login
              </button>
            </div>
          )}
        </div>
      </div>
    </main>
  );
}
