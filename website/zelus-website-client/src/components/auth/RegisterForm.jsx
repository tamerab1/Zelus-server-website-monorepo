import { useState, useEffect, useRef } from 'react';
import { useApp } from '../../context/AppContext.jsx';
import { register } from '../../services/authService.js';
import AlertBox from '../ui/AlertBox.jsx';

const TURNSTILE_SITE_KEY = import.meta.env.VITE_TURNSTILE_SITE_KEY || '';

export default function RegisterForm() {
  const { setCurrentView, authStatus, setAuthStatus } = useApp();
  const [regData,         setRegData]         = useState({ username: '', email: '', password: '' });
  const [isSubmitting,    setIsSubmitting]    = useState(false);
  const [turnstileToken,  setTurnstileToken]  = useState('');
  const turnstileRef = useRef(null);
  const widgetIdRef  = useRef(null);

  // Load Cloudflare Turnstile script once
  useEffect(() => {
    if (!TURNSTILE_SITE_KEY) return; // skip in dev if key not set

    const scriptId = 'cf-turnstile-script';
    if (!document.getElementById(scriptId)) {
      const script = document.createElement('script');
      script.id    = scriptId;
      script.src   = 'https://challenges.cloudflare.com/turnstile/v0/api.js';
      script.async = true;
      script.defer = true;
      document.head.appendChild(script);
    }

    const tryRender = () => {
      if (window.turnstile && turnstileRef.current && widgetIdRef.current === null) {
        widgetIdRef.current = window.turnstile.render(turnstileRef.current, {
          sitekey:           TURNSTILE_SITE_KEY,
          callback:          (token) => setTurnstileToken(token),
          'expired-callback': ()      => setTurnstileToken(''),
          'error-callback':   ()      => setTurnstileToken(''),
          theme: 'dark',
        });
      }
    };

    // Poll until the script is ready
    const interval = setInterval(() => {
      if (window.turnstile) { tryRender(); clearInterval(interval); }
    }, 200);

    return () => {
      clearInterval(interval);
      if (widgetIdRef.current !== null && window.turnstile) {
        window.turnstile.remove(widgetIdRef.current);
        widgetIdRef.current = null;
      }
    };
  }, []);

  const handleRegister = async (e) => {
    e.preventDefault();

    if (TURNSTILE_SITE_KEY && !turnstileToken) {
      setAuthStatus({ type: 'error', message: 'Please complete the CAPTCHA challenge.' });
      return;
    }

    setIsSubmitting(true);
    setAuthStatus({ type: 'info', message: 'Creating account...' });
    try {
      await register({ ...regData, turnstile_token: turnstileToken });
      setAuthStatus({
        type: 'success',
        message: 'Account created! Check your email to verify before logging in.',
      });
      setRegData({ username: '', email: '', password: '' });
      setTurnstileToken('');
      if (widgetIdRef.current !== null && window.turnstile) {
        window.turnstile.reset(widgetIdRef.current);
      }
    } catch (error) {
      setAuthStatus({ type: 'error', message: error.message });
      if (widgetIdRef.current !== null && window.turnstile) {
        window.turnstile.reset(widgetIdRef.current);
      }
      setTurnstileToken('');
    } finally {
      setIsSubmitting(false);
    }
  };

  return (
    <main className="flex justify-center items-center min-h-[80vh] px-4 py-16">
      <div className="stone-panel w-full max-w-md" style={{ borderRadius: 2 }}>

        <div className="panel-header text-center py-6">
          <h2 className="font-fantasy text-2xl font-bold tracking-widest text-white">JOIN THE REALM</h2>
          <p className="font-fantasy text-xs tracking-[0.3em] mt-2" style={{ color: '#555' }}>
            FORGE YOUR ACCOUNT
          </p>
        </div>

        <div className="p-8">
          <AlertBox status={authStatus} />

          <form onSubmit={handleRegister} className="flex flex-col gap-4">
            <input
              type="text"
              placeholder="Character Name"
              required
              maxLength="12"
              className="rpg-input font-fantasy text-sm tracking-wide px-4 py-3"
              value={regData.username}
              onChange={e => setRegData({ ...regData, username: e.target.value })}
            />
            <input
              type="email"
              placeholder="Email Address"
              required
              className="rpg-input font-fantasy text-sm tracking-wide px-4 py-3"
              value={regData.email}
              onChange={e => setRegData({ ...regData, email: e.target.value })}
            />
            <input
              type="password"
              placeholder="Password"
              required
              minLength="6"
              className="rpg-input font-fantasy text-sm tracking-wide px-4 py-3"
              value={regData.password}
              onChange={e => setRegData({ ...regData, password: e.target.value })}
            />

            {/* Cloudflare Turnstile — only rendered when site key is set */}
            {TURNSTILE_SITE_KEY && (
              <div className="flex justify-center mt-1">
                <div ref={turnstileRef} />
              </div>
            )}

            <button
              type="submit"
              disabled={isSubmitting || (TURNSTILE_SITE_KEY && !turnstileToken)}
              className="btn-download mt-3 py-4 w-full font-fantasy text-sm tracking-widest uppercase"
            >
              {isSubmitting ? 'Creating...' : 'Create Account'}
            </button>
          </form>

          <div className="text-center mt-6">
            <button
              onClick={() => setCurrentView('login')}
              className="font-fantasy text-xs tracking-widest transition-colors"
              style={{ color: '#555' }}
              onMouseOver={e => e.currentTarget.style.color = '#d4af37'}
              onMouseOut={e  => e.currentTarget.style.color = '#555'}
            >
              Already have an account? LOGIN
            </button>
          </div>
        </div>
      </div>
    </main>
  );
}
