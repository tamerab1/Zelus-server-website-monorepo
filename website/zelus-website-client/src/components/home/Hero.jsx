import React, { useState, useEffect } from 'react';
import { useApp } from '../../context/AppContext.jsx';
import logo from '../../assets/logo.png';

const API = import.meta.env.VITE_API_URL ?? 'http://localhost:8000';

/* ── Diamond gem ornament — pure inline SVG, no shared gradient IDs ── */
let _gemId = 0;
function DiamondGem({ side }) {
  const id = React.useRef(`gem_${++_gemId}`).current;
  const fillId   = `${id}_f`;
  const borderId = `${id}_b`;
  return (
    <div
      className="absolute top-1/2"
      style={{
        [side]: '-13px',
        transform: 'translateY(-50%)',
        zIndex: 3,
        pointerEvents: 'none',
      }}
    >
      <svg width="26" height="26" viewBox="0 0 26 26" fill="none">
        <defs>
          <linearGradient id={fillId} x1="5" y1="5" x2="21" y2="21" gradientUnits="userSpaceOnUse">
            <stop offset="0%"   stopColor="#6dd5ed"/>
            <stop offset="40%"  stopColor="#1a8aaa"/>
            <stop offset="100%" stopColor="#0a3f5c"/>
          </linearGradient>
          <linearGradient id={borderId} x1="0" y1="0" x2="26" y2="26" gradientUnits="userSpaceOnUse">
            <stop offset="0%"   stopColor="#e8c84a"/>
            <stop offset="50%"  stopColor="#9a7020"/>
            <stop offset="100%" stopColor="#c9a030"/>
          </linearGradient>
        </defs>
        {/* outer gold diamond frame */}
        <polygon points="13,1 25,13 13,25 1,13" fill="#0a1e30" stroke={`url(#${borderId})`} strokeWidth="1.5"/>
        {/* inner gem face */}
        <polygon points="13,5 21,13 13,21 5,13" fill={`url(#${fillId})`}/>
        {/* top-left highlight */}
        <polygon points="13,5 21,13 13,13" fill="rgba(140,230,255,0.18)"/>
        {/* centre spark */}
        <circle cx="13" cy="13" r="1.5" fill="rgba(180,240,255,0.55)"/>
      </svg>
    </div>
  );
}

/* ── Key icon SVG (for download button) ── */
function KeyIcon() {
  return (
    <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="#c9a030" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
      <circle cx="7.5" cy="15.5" r="5.5"/>
      <path d="M21 2L13 10"/>
      <path d="M15 4l2 2"/>
      <path d="M13 10l-2 2"/>
      <path d="M11 12l-3 3"/>
    </svg>
  );
}

/* ── MMO-style button ──────────────────────────────────── */
function MMOButton({ children, onClick, wide = false, ghost = false, className = '' }) {
  const [hovered, setHovered] = useState(false);

  if (ghost) {
    return (
      <button
        onClick={onClick}
        className={`btn-mmo-ghost font-fantasy ${className}`}
        style={{ minWidth: wide ? '320px' : '220px' }}
      >
        {children}
      </button>
    );
  }

  return (
    <button
      onClick={onClick}
      onMouseEnter={() => setHovered(true)}
      onMouseLeave={() => setHovered(false)}
      className={`btn-mmo font-fantasy ${className}`}
      style={{
        minWidth: wide ? '340px' : '240px',
        boxShadow: hovered
          ? '0 0 40px rgba(0,180,220,0.25), 0 0 60px rgba(212,175,55,0.12), 0 6px 24px rgba(0,0,0,0.9), inset 0 1px 0 rgba(100,210,240,0.12)'
          : '0 0 20px rgba(0,130,180,0.12), 0 4px 18px rgba(0,0,0,0.85), inset 0 1px 0 rgba(100,210,240,0.07)',
        borderColor: hovered ? '#d4af37' : '#a87c18',
      }}
    >
      {/* left gem */}
      <DiamondGem side="left" />

      {/* top inner bevel */}
      <div className="btn-mmo-bevel-top" />

      {/* content */}
      <div className="btn-mmo-content">
        {children}
      </div>

      {/* right gem */}
      <DiamondGem side="right" />
    </button>
  );
}

/* ─────────────────────────────────────────────────────── */

export default function Hero() {
  const { setCurrentView } = useApp();
  const [onlineCount, setOnlineCount] = useState(null);

  useEffect(() => {
    let cancelled = false;
    const fetchCount = async () => {
      try {
        const res = await fetch(`${API}/players/online`);
        if (!res.ok) return;
        const data = await res.json();
        if (!cancelled) setOnlineCount(data.online ?? 0);
      } catch { /* ignore */ }
    };
    fetchCount();
    const id = setInterval(fetchCount, 30_000);
    return () => { cancelled = true; clearInterval(id); };
  }, []);

  return (
    <section
      className="relative flex items-center justify-center"
      style={{ minHeight: '85vh', overflow: 'visible' }}
    >
      {/* Lightning flash effect */}
      <div className="absolute inset-0 pointer-events-none lightning-overlay" />

      {/* Logo */}
      <img
        src={logo}
        alt="Zelus"
        className="logo-animated"
        style={{
          position: 'absolute',
          top: 0,
          left: '50%',
          zIndex: 600,
          height: 'clamp(10rem, 24vw, 22rem)',
          width: 'auto',
          objectFit: 'contain',
          pointerEvents: 'none',
          opacity: 0,
        }}
      />

      {/* Content */}
      <div
        className="relative z-10 text-center px-4 pb-24 w-full max-w-4xl mx-auto flex flex-col items-center"
        style={{ paddingTop: 'clamp(9rem, 21vw, 17rem)' }}
      >
        <p
          className="font-fantasy text-sm tracking-[0.45em] mb-4 uppercase"
          style={{ color: '#d4af37', textShadow: '0 2px 5px rgba(0,0,0,0.8)' }}
        >
          OLD SCHOOL FANTASY RSPS
        </p>

        <p
          className="text-gray-300 text-base max-w-xl mx-auto mb-10 leading-relaxed font-serif tracking-wide"
          style={{ textShadow: '0 2px 6px rgba(0,0,0,0.9)' }}
        >
          Experience the pinnacle of custom RSPS. Fight legendary bosses,
          compete in high-stakes PvP, and build your legacy.
        </p>

        {/* ── Stacked CTA buttons ── */}
        <div className="flex flex-col items-center gap-4 w-full">

          {/* DOWNLOAD CLIENT */}
          <MMOButton wide={true} onClick={() => setCurrentView('download')}>
            <div className="flex flex-col items-center gap-0.5">
              <div className="flex items-center gap-3">
                <span
                  className="text-sm tracking-[0.2em] font-bold"
                  style={{ color: '#f0e060', textShadow: '0 0 14px rgba(212,175,55,0.7), 0 1px 3px rgba(0,0,0,0.9)' }}
                >
                  DOWNLOAD CLIENT (v2.1)
                </span>
                <KeyIcon />
              </div>
              <span
                className="text-xs tracking-[0.25em]"
                style={{ color: '#7ab8cc', fontSize: '9px', letterSpacing: '0.2em' }}
              >
                — WINDOWS, MAC &amp; LINUX —
              </span>
            </div>
          </MMOButton>

        </div>

        {/* Stats */}
        <div className="flex flex-wrap justify-center gap-6 mt-12">
          {[
            { label: 'Players Online', value: onlineCount === null ? '…' : onlineCount },
            { label: 'Custom Items',   value: '500+'    },
          ].map(({ label, value }) => (
            <div
              key={label}
              className="stat-box-fantasy text-center flex flex-col items-center justify-center"
            >
              <div
                className="font-fantasy text-3xl font-bold mb-1"
                style={{ color: '#ffd700', textShadow: '0 2px 12px rgba(212,175,55,0.6)' }}
              >
                {value}
              </div>
              <div
                className="text-xs tracking-widest mt-2 font-fantasy uppercase"
                style={{ color: '#888' }}
              >
                {label}
              </div>
            </div>
          ))}
        </div>
      </div>
    </section>
  );
}
