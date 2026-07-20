import { useState } from 'react';
import { useApp } from '../../context/AppContext.jsx';
import AlertBox from '../ui/AlertBox.jsx';
const API = import.meta.env.VITE_API_URL ?? '';

export default function ForgotPasswordForm() {
  const { setCurrentView } = useApp();
  const [email,      setEmail]      = useState('');
  const [status,     setStatus]     = useState({ type: '', message: '' });
  const [submitting, setSubmitting] = useState(false);
  const [sent,       setSent]       = useState(false);

  const handleSubmit = async (e) => {
    e.preventDefault();
    setSubmitting(true);
    setStatus({ type: 'info', message: 'Sending reset link...' });
    try {
      await fetch(`${API}/forgot-password`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ email }),
      });
      // Always show the same message — never reveal whether the email exists
      setSent(true);
      setStatus({ type: 'success', message: 'If that email is registered, a reset link has been sent.' });
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
          <h2 className="font-fantasy text-2xl font-bold tracking-widest text-white">FORGOT PASSWORD</h2>
          <p className="font-fantasy text-xs tracking-[0.3em] mt-2" style={{ color: '#555' }}>
            ENTER YOUR EMAIL ADDRESS
          </p>
        </div>
        <div className="p-8">
          <AlertBox status={status} />
          {!sent && (
            <form onSubmit={handleSubmit} className="flex flex-col gap-4">
              <input
                type="email"
                placeholder="Email address"
                required
                className="rpg-input font-fantasy text-sm tracking-wide px-4 py-3"
                value={email}
                onChange={e => setEmail(e.target.value)}
              />
              <button
                type="submit"
                disabled={submitting}
                className="btn-download mt-3 py-4 w-full font-fantasy text-sm tracking-widest uppercase"
              >
                {submitting ? 'Sending...' : 'Send Reset Link'}
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
              Back to LOGIN
            </button>
          </div>
        </div>
      </div>
    </main>
  );
}
