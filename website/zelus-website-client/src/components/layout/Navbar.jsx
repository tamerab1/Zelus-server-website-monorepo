import { useEffect, useState } from 'react';
import { Menu, X, LogOut, LayoutDashboard, UserCircle, Download } from 'lucide-react';
import { useApp } from '../../context/AppContext.jsx';
import { isAdmin } from '../../utils/ranks.js';
import usePlayersOnline from '../../hooks/usePlayersOnline.js';
import useDiscordInvite from '../../hooks/useDiscordInvite.js';
import { DISCORD_URL, REVISION } from '../../data/links.js';
import DiscordIcon from '../ui/DiscordIcon.jsx';
import logo from '../../assets/karn-logo.webp';

const LINKS = [
  { label: 'Home',       view: 'home'     },
  { label: 'Play',       view: 'download' },
  { label: 'Highscores', view: 'hiscores' },
  { label: 'Store',      view: 'store'    },
  { label: 'Vote',       view: 'vote'     },
];

/* ── Top status bar: players online, revision, Discord ─────────────────── */
function StatusBar() {
  const { count, status } = usePlayersOnline();
  const discord = useDiscordInvite();

  const dot = status === 'online' ? '#22c55e' : status === 'offline' ? '#DC2626' : '#E5B84B';
  const label = status === 'online'
    ? <><b className="text-white tabular">{count.toLocaleString()}</b> {count === 1 ? 'player' : 'players'} online</>
    : status === 'offline' ? 'Server offline' : 'Connecting…';

  return (
    <div className="h-8 border-b border-karn-border/80" style={{ background: '#060507' }}>
      <div className="max-w-7xl mx-auto h-full px-4 sm:px-6 flex items-center justify-between gap-4 font-ui text-[0.78rem] font-semibold tracking-[0.12em] uppercase text-karn-muted">
        <div className="flex items-center gap-4 min-w-0">
          <span className="flex items-center gap-2 whitespace-nowrap" aria-live="polite">
            <span className="w-2 h-2 rounded-full k-live-dot" style={{ background: dot, boxShadow: `0 0 8px ${dot}` }} />
            {label}
          </span>
          <span className="hidden sm:inline-block w-px h-3.5 bg-karn-border" />
          <span className="hidden sm:inline-flex items-center gap-1.5 px-1.5 py-px rounded-sm border border-karn-gold/40 text-karn-gold-hi bg-karn-gold/[0.06] text-[0.7rem]">
            Rev {REVISION}
          </span>
        </div>
        <a
          href={DISCORD_URL}
          target="_blank"
          rel="noopener noreferrer"
          className="flex items-center gap-2 whitespace-nowrap hover:text-white transition-colors"
        >
          <DiscordIcon size={14} color="#8C9EFF" />
          <span className="hidden sm:inline">
            {discord?.online != null ? <><b className="text-white tabular">{discord.online.toLocaleString()}</b> on Discord</> : 'Join our Discord'}
          </span>
          <span className="sm:hidden">Discord</span>
        </a>
      </div>
    </div>
  );
}

function NavItem({ label, view, active, onClick }) {
  return (
    <button
      type="button"
      onClick={() => onClick(view)}
      aria-current={active ? 'page' : undefined}
      className={`relative h-16 px-3.5 font-ui text-[0.92rem] font-bold uppercase tracking-[0.14em] transition-colors ${active ? 'text-karn-gold-hi' : 'text-karn-text/80 hover:text-white'}`}
    >
      {label}
      <span
        className={`absolute left-3 right-3 bottom-0 h-[2px] transition-opacity ${active ? 'opacity-100' : 'opacity-0'}`}
        style={{ background: 'linear-gradient(90deg, transparent, #B81D24, #E5B84B, #B81D24, transparent)' }}
      />
    </button>
  );
}

export default function Navbar() {
  const { currentView, setCurrentView, currentUser, handleLogout, setPlayOpen } = useApp();
  const [menuOpen, setMenuOpen] = useState(false);
  const [scrolled, setScrolled] = useState(false);
  const admin = isAdmin(currentUser?.privilege);

  useEffect(() => {
    const onScroll = () => setScrolled(window.scrollY > 24);
    onScroll();
    window.addEventListener('scroll', onScroll, { passive: true });
    return () => window.removeEventListener('scroll', onScroll);
  }, []);

  // Esc closes the mobile menu
  useEffect(() => {
    if (!menuOpen) return;
    const onKey = (e) => e.key === 'Escape' && setMenuOpen(false);
    window.addEventListener('keydown', onKey);
    return () => window.removeEventListener('keydown', onKey);
  }, [menuOpen]);

  const go = (view) => { setCurrentView(view); setMenuOpen(false); };
  const glass = scrolled || menuOpen || currentView !== 'home';

  return (
    <header className="fixed top-0 inset-x-0 z-[500]">
      <StatusBar />

      <nav
        className="h-16 transition-[background,border-color,backdrop-filter] duration-300"
        style={{
          background: glass ? 'rgba(9,8,10,0.78)' : 'linear-gradient(180deg, rgba(9,8,10,0.75), rgba(9,8,10,0))',
          backdropFilter: glass ? 'blur(14px) saturate(140%)' : 'none',
          WebkitBackdropFilter: glass ? 'blur(14px) saturate(140%)' : 'none',
          borderBottom: `1px solid ${glass ? 'rgba(42,34,38,0.9)' : 'transparent'}`,
        }}
        aria-label="Main"
      >
        <div className="max-w-7xl mx-auto h-full px-4 sm:px-6 flex items-center">
          <button type="button" onClick={() => go('home')} className="shrink-0 mr-6" aria-label="Karn home">
            <img src={logo} alt="Karn" className="h-10 w-auto" style={{ filter: 'drop-shadow(0 2px 10px rgba(0,0,0,.8))' }} />
          </button>

          {/* Desktop links */}
          <div className="hidden lg:flex items-center">
            {LINKS.map(l => (
              <NavItem key={l.view} {...l} active={currentView === l.view} onClick={go} />
            ))}
          </div>

          <div className="flex-1" />

          {/* Desktop right side */}
          <div className="hidden lg:flex items-center gap-3">
            {admin && (
              <button type="button" onClick={() => go('admin')}
                className={`k-btn h-9 px-3 text-[0.8rem] ${currentView === 'admin' ? 'k-btn-gold' : 'text-karn-muted hover:text-karn-gold-hi'}`}>
                <LayoutDashboard size={15} /> Admin CP
              </button>
            )}
            {currentUser ? (
              <div className="flex items-center gap-2 pl-3 border-l border-karn-border h-9">
                <span className="flex items-center gap-1.5 font-ui font-semibold text-sm text-karn-text">
                  <UserCircle size={17} className="text-karn-gold" /> {currentUser.username}
                </span>
                <button type="button" onClick={handleLogout} title="Log out" aria-label="Log out"
                  className="p-2 rounded text-karn-dim hover:text-karn-crimson-hi transition-colors">
                  <LogOut size={16} />
                </button>
              </div>
            ) : (
              <button type="button" onClick={() => go('staff_login')} className="k-btn k-btn-ghost h-9 px-4 text-[0.8rem]">
                <UserCircle size={15} /> Login
              </button>
            )}
            <button type="button" onClick={() => setPlayOpen(true)} className="k-btn k-btn-crimson h-9 px-5 text-[0.82rem]">
              <Download size={15} /> Play Now
            </button>
          </div>

          {/* Mobile toggles */}
          <div className="lg:hidden flex items-center gap-2">
            <button type="button" onClick={() => setPlayOpen(true)} className="k-btn k-btn-crimson h-9 px-3 text-[0.78rem]">Play</button>
            <button
              type="button"
              className="p-2 rounded text-karn-text hover:text-karn-gold-hi"
              onClick={() => setMenuOpen(v => !v)}
              aria-label={menuOpen ? 'Close menu' : 'Open menu'}
              aria-expanded={menuOpen}
            >
              {menuOpen ? <X size={22} /> : <Menu size={22} />}
            </button>
          </div>
        </div>
      </nav>

      {/* Mobile menu */}
      {menuOpen && (
        <div className="lg:hidden border-b border-karn-border" style={{ background: 'rgba(9,8,10,0.97)', animation: 'k-fade-in .15s ease' }}>
          <div className="max-w-7xl mx-auto px-4 py-2 flex flex-col">
            {LINKS.map(({ label, view }) => (
              <button key={view} type="button" onClick={() => go(view)}
                className={`text-left py-3.5 font-ui font-bold uppercase tracking-[0.14em] border-b border-karn-border/60 ${currentView === view ? 'text-karn-gold-hi' : 'text-karn-text/85'}`}>
                {label}
              </button>
            ))}
            {admin && (
              <button type="button" onClick={() => go('admin')} className="text-left py-3.5 font-ui font-bold uppercase tracking-[0.14em] border-b border-karn-border/60 text-karn-gold">
                Admin CP
              </button>
            )}
            <div className="py-3 flex items-center justify-between gap-3">
              {currentUser ? (
                <>
                  <span className="font-ui font-semibold text-karn-text flex items-center gap-1.5"><UserCircle size={17} className="text-karn-gold" /> {currentUser.username}</span>
                  <button type="button" onClick={() => { handleLogout(); setMenuOpen(false); }} className="k-btn k-btn-ghost h-9 px-4 text-[0.78rem]"><LogOut size={14} /> Log out</button>
                </>
              ) : (
                <button type="button" onClick={() => go('staff_login')} className="k-btn k-btn-ghost h-9 px-4 text-[0.78rem]"><UserCircle size={15} /> Login</button>
              )}
            </div>
          </div>
        </div>
      )}
    </header>
  );
}
