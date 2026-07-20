import { useState } from 'react';
import { useApp } from '../../context/AppContext.jsx';
import { login } from '../../services/authService.js';
import AlertBox from '../ui/AlertBox.jsx';

export default function LoginForm() {
  const { setCurrentUser, setCurrentView, authStatus, setAuthStatus } = useApp();
  const [loginData,    setLoginData]    = useState({ username: '', password: '' });
  const [isSubmitting, setIsSubmitting] = useState(false);

  const handleLogin = async (e) => {
    e.preventDefault();
    setIsSubmitting(true);
    setAuthStatus({ type: 'info', message: 'Authenticating...' });
    try {
      const data = await login(loginData);
      setCurrentUser(data.user);
      setAuthStatus({ type: '', message: '' });
      setLoginData({ username: '', password: '' });
      setCurrentView('panel');
    } catch (error) {
      setAuthStatus({ type: 'error', message: error.message });
    } finally {
      setIsSubmitting(false);
    }
  };

  return (
    <main className="flex justify-center items-center min-h-[80vh] px-4 py-16">
      <div className="stone-panel w-full max-w-md" style={{ borderRadius: 2 }}>

        <div className="panel-header text-center py-6">
          <h2 className="font-fantasy text-2xl font-bold tracking-widest text-white">WELCOME BACK</h2>
          <p className="font-fantasy text-xs tracking-[0.3em] mt-2" style={{ color: '#555' }}>
            ENTER YOUR CREDENTIALS
          </p>
        </div>

        <div className="p-8">
          <AlertBox status={authStatus} />

          <form onSubmit={handleLogin} className="flex flex-col gap-4">
            <input
              type="text"
              placeholder="Character Name"
              required
              className="rpg-input font-fantasy text-sm tracking-wide px-4 py-3"
              value={loginData.username}
              onChange={e => setLoginData({ ...loginData, username: e.target.value })}
            />
            <input
              type="password"
              placeholder="Password"
              required
              className="rpg-input font-fantasy text-sm tracking-wide px-4 py-3"
              value={loginData.password}
              onChange={e => setLoginData({ ...loginData, password: e.target.value })}
            />
            <button
              type="submit"
              disabled={isSubmitting}
              className="btn-download mt-3 py-4 w-full font-fantasy text-sm tracking-widest uppercase"
            >
              {isSubmitting ? 'Authenticating...' : 'Sign In'}
            </button>
          </form>

          <div className="text-center mt-6 flex flex-col gap-3">
            <button
              onClick={() => setCurrentView('forgot_password')}
              className="font-fantasy text-xs tracking-widest transition-colors"
              style={{ color: '#555' }}
              onMouseOver={e => e.currentTarget.style.color = '#d4af37'}
              onMouseOut={e  => e.currentTarget.style.color = '#555'}
            >
              Forgot your password?
            </button>
            <button
              onClick={() => setCurrentView('register')}
              className="font-fantasy text-xs tracking-widest transition-colors"
              style={{ color: '#555' }}
              onMouseOver={e => e.currentTarget.style.color = '#d4af37'}
              onMouseOut={e  => e.currentTarget.style.color = '#555'}
            >
              New adventurer? REGISTER
            </button>
          </div>
        </div>
      </div>
    </main>
  );
}
