import { useState } from 'react';
import { useApp } from '../../context/AppContext.jsx';
import { isAdmin } from '../../utils/ranks.js';
import logo from '../../assets/logo.png';

const LEFT_LINKS = [
  { label: 'HOME',     view: 'home'     },
  { label: 'FORUMS',   view: null       },
  { label: 'STORE',    view: 'store'    },
  { label: 'DOWNLOAD', view: 'download' },
];

const RIGHT_LINKS = [
  { label: 'HISCORES', view: 'hiscores' },
  { label: 'VOTE',     view: 'vote'     },
];

const ALL_LINKS = [...LEFT_LINKS, ...RIGHT_LINKS];
const DISCORD_URL = 'https://discord.gg/XZ3E6Nur2r';

/* ── Shield SVG ornament ─────────────────────────────── */
function ShieldOrnament({ flip = false }) {
  return (
    <div
      className="shrink-0 select-none pointer-events-none"
      style={{
        transform: flip ? 'scaleX(-1)' : 'none',
        filter: 'drop-shadow(0 2px 6px rgba(0,0,0,0.9)) drop-shadow(0 0 8px rgba(212,175,55,0.25))',
        zIndex: 2,
      }}
    >
      <svg width="52" height="62" viewBox="0 0 52 62" fill="none" xmlns="http://www.w3.org/2000/svg">
        {/* Shield body */}
        <path d="M4 4 H48 V36 Q26 62 4 36 Z" fill="url(#shieldGrad)" stroke="url(#shieldBorder)" strokeWidth="1.5"/>
        {/* Inner border */}
        <path d="M8 8 H44 V35 Q26 56 8 35 Z" fill="none" stroke="rgba(212,175,55,0.3)" strokeWidth="0.8"/>
        {/* Sword icon */}
        <line x1="26" y1="13" x2="26" y2="44" stroke="url(#swordGrad)" strokeWidth="2" strokeLinecap="round"/>
        <line x1="19" y1="24" x2="33" y2="24" stroke="url(#swordGrad)" strokeWidth="2" strokeLinecap="round"/>
        <circle cx="26" cy="13" r="2" fill="#d4af37" opacity="0.8"/>
        {/* Rivet dots */}
        <circle cx="10" cy="10" r="1.2" fill="rgba(212,175,55,0.4)"/>
        <circle cx="42" cy="10" r="1.2" fill="rgba(212,175,55,0.4)"/>
        {/* Top banner notch */}
        <rect x="18" y="0" width="16" height="6" rx="1" fill="url(#notchGrad)" stroke="rgba(212,175,55,0.5)" strokeWidth="0.8"/>
        <defs>
          <linearGradient id="shieldGrad" x1="26" y1="0" x2="26" y2="62" gradientUnits="userSpaceOnUse">
            <stop offset="0%"   stopColor="#2a2a3a"/>
            <stop offset="40%"  stopColor="#181828"/>
            <stop offset="100%" stopColor="#0d0d18"/>
          </linearGradient>
          <linearGradient id="shieldBorder" x1="0" y1="0" x2="52" y2="62" gradientUnits="userSpaceOnUse">
            <stop offset="0%"   stopColor="#d4af37" stopOpacity="0.9"/>
            <stop offset="50%"  stopColor="#8b6914" stopOpacity="0.6"/>
            <stop offset="100%" stopColor="#d4af37" stopOpacity="0.4"/>
          </linearGradient>
          <linearGradient id="swordGrad" x1="26" y1="13" x2="26" y2="44" gradientUnits="userSpaceOnUse">
            <stop offset="0%"   stopColor="#f0d060"/>
            <stop offset="100%" stopColor="#8b6914"/>
          </linearGradient>
          <linearGradient id="notchGrad" x1="18" y1="0" x2="34" y2="6" gradientUnits="userSpaceOnUse">
            <stop offset="0%"   stopColor="#2e2e40"/>
            <stop offset="100%" stopColor="#1a1a28"/>
          </linearGradient>
        </defs>
      </svg>
    </div>
  );
}

/* ── Nav link ────────────────────────────────────────── */
function NavLink({ label, view, currentView, navigate }) {
  const active = currentView === view;
  return (
    <button
      onClick={() => navigate(view)}
      className="font-fantasy text-xs tracking-[0.2em] relative transition-all duration-150 px-1"
      style={{
        color:      active ? '#f0d060' : '#c8bfa8',
        textShadow: active ? '0 0 10px rgba(212,175,55,0.7)' : '0 1px 3px rgba(0,0,0,0.9)',
        cursor:     view ? 'pointer' : 'default',
        opacity:    view ? 1 : 0.4,
        letterSpacing: '0.18em',
      }}
      onMouseOver={e => { if (view && !active) e.currentTarget.style.color = '#f0d060'; }}
      onMouseOut={e  => { if (!active) e.currentTarget.style.color = '#c8bfa8'; }}
    >
      {label}
      {active && (
        <span
          className="absolute left-0 right-0"
          style={{
            bottom: '-3px',
            height: '1px',
            background: 'linear-gradient(90deg, transparent, #d4af37, transparent)',
          }}
        />
      )}
    </button>
  );
}

export default function Navbar() {
  const { currentView, setCurrentView, currentUser, handleLogout } = useApp();
  const [menuOpen, setMenuOpen] = useState(false);

  const navigate = (view) => {
    if (view) setCurrentView(view);
    setMenuOpen(false);
  };

  return (
    <nav className="fixed top-0 left-0 right-0 z-[500]" style={{ overflow: 'visible' }}>

      {/* ── Top gold accent line ─────────────────────────── */}
      <div style={{
        height: '2px',
        background: 'linear-gradient(90deg, transparent 0%, #8b6914 15%, #d4af37 40%, #f0d060 50%, #d4af37 60%, #8b6914 85%, transparent 100%)',
        boxShadow: '0 0 8px rgba(212,175,55,0.4)',
      }} />

      {/* ── Main bar ─────────────────────────────────────── */}
      <div style={{
        background: 'linear-gradient(180deg, #1c1c2a 0%, #12121e 50%, #0e0e1a 100%)',
        borderBottom: '1px solid rgba(212,175,55,0.15)',
        boxShadow: '0 4px 24px rgba(0,0,0,0.8), inset 0 1px 0 rgba(255,255,255,0.04), inset 0 -1px 0 rgba(0,0,0,0.5)',
        position: 'relative',
      }}>

        {/* Desktop layout */}
        <div className="hidden md:flex items-center h-16 max-w-full px-0">

          {/* Left shield */}
          <div className="flex items-end pl-4" style={{ marginBottom: '-2px' }}>
            <ShieldOrnament flip={false} />
          </div>

          {/* Left divider line */}
          <div style={{ width: '1px', height: '32px', background: 'linear-gradient(180deg, transparent, rgba(212,175,55,0.25), transparent)', margin: '0 16px' }} />

          {/* Logo */}
          <button onClick={() => navigate('home')} className="shrink-0 mr-6">
            <img src={logo} alt="Zelus" className="h-10 w-auto object-contain"
                 style={{ filter: 'drop-shadow(0 2px 8px rgba(212,175,55,0.4))' }} />
          </button>

          {/* Left nav links */}
          <div className="flex items-center gap-7">
            {LEFT_LINKS.map(link => (
              <NavLink key={link.label} {...link} currentView={currentView} navigate={navigate} />
            ))}
          </div>

          {/* Spacer */}
          <div className="flex-1" />

          {/* Right nav links */}
          <div className="flex items-center gap-7">
            {RIGHT_LINKS.map(link => (
              <NavLink key={link.label} {...link} currentView={currentView} navigate={navigate} />
            ))}

            {isAdmin(currentUser?.privilege) && (
              <button
                onClick={() => navigate('admin')}
                className="font-fantasy text-xs tracking-[0.18em] transition-all"
                style={{ color: currentView === 'admin' ? '#f87171' : '#7f1d1d' }}
              >
                ADMIN CP
              </button>
            )}

            {/* Discord */}
            <a
              href={DISCORD_URL}
              target="_blank"
              rel="noopener noreferrer"
              className="font-fantasy text-xs tracking-[0.18em] transition-all flex items-center gap-1.5"
              style={{ color: '#c8bfa8', textShadow: '0 1px 3px rgba(0,0,0,0.9)' }}
              onMouseOver={e => e.currentTarget.style.color = '#7289da'}
              onMouseOut={e  => e.currentTarget.style.color = '#c8bfa8'}
            >
              <svg width="13" height="13" viewBox="0 0 24 24" fill="currentColor">
                <path d="M20.317 4.37a19.791 19.791 0 0 0-4.885-1.515.074.074 0 0 0-.079.037c-.21.375-.444.864-.608 1.25a18.27 18.27 0 0 0-5.487 0 12.64 12.64 0 0 0-.617-1.25.077.077 0 0 0-.079-.037A19.736 19.736 0 0 0 3.677 4.37a.07.07 0 0 0-.032.027C.533 9.046-.32 13.58.099 18.057a.082.082 0 0 0 .031.057 19.9 19.9 0 0 0 5.993 3.03.078.078 0 0 0 .084-.028c.462-.63.874-1.295 1.226-1.994a.076.076 0 0 0-.041-.106 13.107 13.107 0 0 1-1.872-.892.077.077 0 0 1-.008-.128 10.2 10.2 0 0 0 .372-.292.074.074 0 0 1 .077-.01c3.928 1.793 8.18 1.793 12.062 0a.074.074 0 0 1 .078.01c.12.098.246.198.373.292a.077.077 0 0 1-.006.127 12.299 12.299 0 0 1-1.873.892.077.077 0 0 0-.041.107c.36.698.772 1.362 1.225 1.993a.076.076 0 0 0 .084.028 19.839 19.839 0 0 0 6.002-3.03.077.077 0 0 0 .032-.054c.5-5.177-.838-9.674-3.549-13.66a.061.061 0 0 0-.031-.03z"/>
              </svg>
              DISCORD
            </a>
          </div>

          {/* Auth divider */}
          <div style={{ width: '1px', height: '32px', background: 'linear-gradient(180deg, transparent, rgba(212,175,55,0.25), transparent)', margin: '0 16px' }} />

          {/* Auth buttons */}
          <div className="flex items-center gap-4 shrink-0">
            {!currentUser ? (
              <>
                <button
                  onClick={() => navigate('login')}
                  className="font-fantasy text-xs tracking-[0.18em] transition-all"
                  style={{ color: '#c8bfa8' }}
                  onMouseOver={e => e.currentTarget.style.color = '#f0d060'}
                  onMouseOut={e  => e.currentTarget.style.color = '#c8bfa8'}
                >
                  LOGIN
                </button>
                <button
                  onClick={() => navigate('register')}
                  className="font-fantasy text-xs tracking-[0.18em] transition-all"
                  style={{
                    background: 'linear-gradient(180deg, #2a2010 0%, #1a1408 100%)',
                    border: '1px solid rgba(212,175,55,0.5)',
                    color: '#d4af37',
                    padding: '5px 14px',
                    boxShadow: 'inset 0 1px 0 rgba(255,255,255,0.05), 0 2px 8px rgba(0,0,0,0.5)',
                  }}
                  onMouseOver={e => e.currentTarget.style.borderColor = '#d4af37'}
                  onMouseOut={e  => e.currentTarget.style.borderColor = 'rgba(212,175,55,0.5)'}
                >
                  REGISTER
                </button>
              </>
            ) : (
              <>
                <button
                  onClick={() => navigate('panel')}
                  className="font-fantasy text-xs tracking-[0.18em]"
                  style={{ color: '#d4af37', textShadow: '0 0 8px rgba(212,175,55,0.4)' }}
                >
                  {currentUser.username}
                </button>
                <button
                  onClick={handleLogout}
                  title="Logout"
                  className="text-xs transition-colors"
                  style={{ color: '#555' }}
                  onMouseOver={e => e.currentTarget.style.color = '#f87171'}
                  onMouseOut={e  => e.currentTarget.style.color = '#555'}
                >
                  ✕
                </button>
              </>
            )}
          </div>

          {/* Right divider */}
          <div style={{ width: '1px', height: '32px', background: 'linear-gradient(180deg, transparent, rgba(212,175,55,0.25), transparent)', margin: '0 16px' }} />

          {/* Right shield */}
          <div className="flex items-end pr-4" style={{ marginBottom: '-2px' }}>
            <ShieldOrnament flip={true} />
          </div>
        </div>

        {/* ── Mobile header ──────────────────────────────── */}
        <div className="md:hidden flex items-center justify-between px-4 h-14">
          <div className="flex items-center gap-3">
            <ShieldOrnament flip={false} />
            <button onClick={() => navigate('home')}>
              <img src={logo} alt="Zelus" className="h-8 w-auto object-contain" />
            </button>
          </div>
          <button
            className="font-fantasy text-lg transition-colors"
            style={{ color: menuOpen ? '#d4af37' : '#c8bfa8' }}
            onClick={() => setMenuOpen(v => !v)}
            aria-label="Toggle menu"
          >
            {menuOpen ? '✕' : '☰'}
          </button>
        </div>
      </div>

      {/* Bottom gold line */}
      <div style={{
        height: '1px',
        background: 'linear-gradient(90deg, transparent 0%, rgba(212,175,55,0.08) 30%, rgba(212,175,55,0.18) 50%, rgba(212,175,55,0.08) 70%, transparent 100%)',
      }} />

      {/* ── Mobile dropdown ────────────────────────────── */}
      {menuOpen && (
        <div style={{
          background: 'linear-gradient(180deg, #12121e 0%, #0e0e1a 100%)',
          borderBottom: '1px solid rgba(212,175,55,0.2)',
          boxShadow: '0 8px 24px rgba(0,0,0,0.8)',
        }}>
          <div className="flex flex-col px-6 py-3 gap-0">
            {ALL_LINKS.map(({ label, view }) => (
              <button
                key={label}
                onClick={() => navigate(view)}
                className="font-fantasy text-xs tracking-[0.18em] text-left py-3 transition-colors"
                style={{
                  color:        currentView === view ? '#f0d060' : '#c8bfa8',
                  borderBottom: '1px solid rgba(212,175,55,0.1)',
                  cursor:       view ? 'pointer' : 'default',
                  opacity:      view ? 1 : 0.4,
                }}
              >
                {label}
              </button>
            ))}
            <a
              href={DISCORD_URL}
              target="_blank"
              rel="noopener noreferrer"
              className="font-fantasy text-xs tracking-[0.18em] text-left py-3"
              style={{ color: '#7289da', borderBottom: '1px solid rgba(212,175,55,0.1)' }}
            >
              DISCORD
            </a>
            {isAdmin(currentUser?.privilege) && (
              <button onClick={() => navigate('admin')} className="font-fantasy text-xs tracking-[0.18em] text-left py-3 text-red-700" style={{ borderBottom: '1px solid rgba(212,175,55,0.1)' }}>
                ADMIN CP
              </button>
            )}
            <div className="pt-4 pb-2 flex flex-col gap-3">
              {!currentUser ? (
                <>
                  <button onClick={() => navigate('login')} className="font-fantasy text-xs tracking-[0.18em] text-left py-2" style={{ color: '#c8bfa8' }}>LOGIN</button>
                  <button onClick={() => navigate('register')} className="font-fantasy text-xs tracking-[0.18em] py-3 w-full"
                    style={{ background: 'linear-gradient(180deg,#2a2010,#1a1408)', border: '1px solid rgba(212,175,55,0.5)', color: '#d4af37' }}>
                    REGISTER
                  </button>
                </>
              ) : (
                <div className="flex justify-between items-center py-2">
                  <button onClick={() => navigate('panel')} className="font-fantasy text-xs tracking-[0.18em]" style={{ color: '#d4af37' }}>{currentUser.username}</button>
                  <button onClick={() => { handleLogout(); setMenuOpen(false); }} className="font-fantasy text-xs tracking-[0.18em]" style={{ color: '#666' }}>LOGOUT</button>
                </div>
              )}
            </div>
          </div>
        </div>
      )}
    </nav>
  );
}
