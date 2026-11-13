import { useEffect, useRef } from 'react';
import { X, Download, Monitor, Coffee, ChevronRight } from 'lucide-react';
import { useApp } from '../../context/AppContext.jsx';
import { LAUNCHER_URL, JAR_URL, DISCORD_URL } from '../../data/links.js';
import DiscordIcon from '../ui/DiscordIcon.jsx';

const STEPS = [
  { n: 1, title: 'Download', body: 'Grab the Windows launcher. It installs once and keeps the client up to date by itself.' },
  { n: 2, title: 'Install & open', body: 'Run the installer, then open Karn from your Start menu or desktop.' },
  { n: 3, title: 'Create your hero', body: 'Pick any username on the login screen: your account is made on first login.' },
];

/**
 * "Play Now" download modal, opened from the hero, the nav and the Play page.
 * Esc / backdrop click closes it; focus moves into the dialog and back out again.
 */
export default function PlayModal() {
  const { playOpen, setPlayOpen, setCurrentView } = useApp();
  const dialogRef = useRef(null);
  const lastFocus = useRef(null);

  useEffect(() => {
    if (!playOpen) return;
    lastFocus.current = document.activeElement;
    const prevOverflow = document.body.style.overflow;
    document.body.style.overflow = 'hidden';
    dialogRef.current?.querySelector('a,button')?.focus();

    const onKey = (e) => {
      if (e.key === 'Escape') setPlayOpen(false);
      if (e.key === 'Tab' && dialogRef.current) {
        const items = [...dialogRef.current.querySelectorAll('a[href],button')];
        if (!items.length) return;
        const first = items[0], last = items[items.length - 1];
        if (e.shiftKey && document.activeElement === first) { e.preventDefault(); last.focus(); }
        else if (!e.shiftKey && document.activeElement === last) { e.preventDefault(); first.focus(); }
      }
    };
    window.addEventListener('keydown', onKey);
    return () => {
      window.removeEventListener('keydown', onKey);
      document.body.style.overflow = prevOverflow;
      lastFocus.current?.focus?.();
    };
  }, [playOpen, setPlayOpen]);

  if (!playOpen) return null;

  return (
    <div className="fixed inset-0 z-[900] flex items-end sm:items-center justify-center sm:p-6"
         style={{ animation: 'k-fade-in .18s ease' }}>
      <div className="absolute inset-0 bg-black/75 backdrop-blur-sm" onClick={() => setPlayOpen(false)} />

      <div
        ref={dialogRef}
        role="dialog"
        aria-modal="true"
        aria-labelledby="play-title"
        className="k-panel k-cap relative w-full sm:max-w-[640px] max-h-[92vh] overflow-y-auto rounded-b-none sm:rounded-md"
        style={{ animation: 'k-modal-in .28s cubic-bezier(.22,1,.36,1)', background: 'linear-gradient(180deg, #17121A 0%, #0F0D11 100%)' }}
      >
        <button type="button" onClick={() => setPlayOpen(false)} aria-label="Close"
          className="absolute top-3 right-3 p-2 rounded text-karn-dim hover:text-white hover:bg-white/5 transition-colors">
          <X size={20} />
        </button>

        <div className="px-6 sm:px-8 pt-8 pb-6">
          <p className="k-eyebrow">Free to play · Windows</p>
          <h2 id="play-title" className="k-heading k-gold-text text-3xl sm:text-4xl mt-2">Enter Karn</h2>
          <p className="text-karn-muted mt-3 text-[0.95rem] leading-relaxed max-w-md">
            Download the launcher, log in with any new username and you are in the world in under two minutes.
          </p>

          {/* Primary download */}
          <a href={LAUNCHER_URL}
             className="group mt-6 flex items-center gap-4 p-4 rounded-md border border-karn-gold/50 transition-all hover:border-karn-gold-hi hover:shadow-gold"
             style={{ background: 'linear-gradient(90deg, rgba(184,29,36,0.22), rgba(184,29,36,0.06))' }}>
            <span className="grid place-items-center w-12 h-12 rounded-md shrink-0 text-white"
                  style={{ background: 'linear-gradient(180deg, #CF262E, #8E141A)', boxShadow: 'inset 0 1px 0 rgba(255,255,255,.25)' }}>
              <Monitor size={22} />
            </span>
            <span className="flex-1 min-w-0">
              <span className="flex items-center gap-2">
                <span className="font-ui font-bold text-lg uppercase tracking-[0.1em] text-white">Windows Launcher</span>
                <span className="k-chip !text-[0.62rem]">Recommended</span>
              </span>
              <span className="block text-sm text-karn-muted mt-0.5">Windows 10 / 11 · installs once, updates itself</span>
            </span>
            <Download size={20} className="text-karn-gold-hi shrink-0 group-hover:translate-y-0.5 transition-transform" />
          </a>

          {/* Secondary download */}
          <a href={JAR_URL}
             className="group mt-3 flex items-center gap-4 p-4 rounded-md border border-karn-border hover:border-karn-gold/50 bg-white/[0.02] transition-colors">
            <span className="grid place-items-center w-12 h-12 rounded-md shrink-0 border border-karn-border text-karn-gold-hi bg-black/30">
              <Coffee size={20} />
            </span>
            <span className="flex-1 min-w-0">
              <span className="font-ui font-bold text-base uppercase tracking-[0.1em] text-karn-text">Client.jar</span>
              <span className="block text-sm text-karn-muted mt-0.5">No installer · needs Java 21</span>
            </span>
            <Download size={18} className="text-karn-muted shrink-0 group-hover:text-karn-gold-hi transition-colors" />
          </a>

          {/* Steps */}
          <ol className="mt-7 grid sm:grid-cols-3 gap-3">
            {STEPS.map(s => (
              <li key={s.n} className="rounded-md border border-karn-border bg-black/25 p-4">
                <span className="font-fantasy font-black text-2xl k-gold-text">{s.n}</span>
                <p className="font-ui font-bold uppercase tracking-[0.12em] text-sm text-white mt-1">{s.title}</p>
                <p className="text-[0.82rem] text-karn-muted mt-1.5 leading-relaxed">{s.body}</p>
              </li>
            ))}
          </ol>
        </div>

        <div className="px-6 sm:px-8 py-4 border-t border-karn-border flex flex-wrap items-center justify-between gap-3 bg-black/30">
          <a href={DISCORD_URL} target="_blank" rel="noopener noreferrer"
             className="flex items-center gap-2 text-sm text-karn-muted hover:text-white transition-colors">
            <DiscordIcon size={16} color="#8C9EFF" /> Need help? Ask on Discord
          </a>
          <button type="button" className="k-link" onClick={() => { setPlayOpen(false); setCurrentView('download'); }}>
            Full download page <ChevronRight size={14} />
          </button>
        </div>
      </div>
    </div>
  );
}
