import { useMemo } from 'react';
import { Download } from 'lucide-react';
import { useApp } from '../../context/AppContext.jsx';
import { DISCORD_URL } from '../../data/links.js';
import DiscordIcon from '../ui/DiscordIcon.jsx';
import heroArt from '../../assets/karn-hero.webp';
import logo from '../../assets/karn-logo.webp';

/** A handful of rising embers over the artwork (CSS only; hidden for reduced motion). */
function Embers({ count = 18 }) {
  const embers = useMemo(() => Array.from({ length: count }, (_, i) => ({
    left: `${(i * 53) % 100}%`,
    delay: `${(i * 0.73) % 9}s`,
    duration: `${9 + ((i * 1.7) % 7)}s`,
    drift: `${((i % 5) - 2) * 18}px`,
    size: 2 + (i % 3),
  })), [count]);
  return (
    <div aria-hidden="true" className="absolute inset-0 overflow-hidden">
      {embers.map((e, i) => (
        <span key={i} className="k-ember"
          style={{ left: e.left, animationDelay: e.delay, animationDuration: e.duration, '--drift': e.drift, width: e.size, height: e.size }} />
      ))}
    </div>
  );
}

export default function Hero() {
  const { setPlayOpen } = useApp();

  return (
    <section className="relative isolate overflow-hidden" style={{ minHeight: 'min(92vh, 920px)' }}>
      {/* Artwork */}
      <img src={heroArt} alt="" aria-hidden="true" fetchPriority="high"
           className="absolute inset-0 -z-20 w-full h-full object-cover object-[center_40%]" />
      {/* Vignette + bottom fade into the void */}
      <div aria-hidden="true" className="absolute inset-0 -z-10" style={{
        background: [
          'radial-gradient(ellipse 75% 60% at 50% 42%, rgba(9,8,10,0.05) 0%, rgba(9,8,10,0.55) 70%, rgba(9,8,10,0.85) 100%)',
          'linear-gradient(180deg, rgba(9,8,10,0.7) 0%, rgba(9,8,10,0.1) 22%, rgba(9,8,10,0.15) 55%, rgba(9,8,10,0.85) 82%, #09080A 100%)',
        ].join(','),
      }} />
      <Embers />

      <div className="relative max-w-5xl mx-auto px-4 sm:px-6 text-center flex flex-col items-center"
           style={{ paddingTop: 'calc(var(--k-header-h) + clamp(40px, 9vh, 110px))', paddingBottom: 'clamp(150px, 20vh, 210px)' }}>
        <img src={logo} alt="Karn" className="k-logo-glow k-rise w-[min(560px,86vw)] h-auto" />

        <div className="k-rise mt-6 flex items-center gap-3 sm:gap-4" style={{ animationDelay: '.12s' }}>
          <span className="hidden sm:block h-px w-16" style={{ background: 'linear-gradient(90deg, transparent, #D4AF37)' }} />
          <h1 className="font-ui font-bold uppercase text-[0.95rem] sm:text-[1.2rem] tracking-[0.22em] sm:tracking-[0.3em] text-white"
              style={{ textShadow: '0 2px 12px rgba(0,0,0,.9)' }}>
            Revision 241 <span className="text-karn-crimson-hi mx-1.5">•</span> True Progression PvM &amp; High-Stakes PvP
          </h1>
          <span className="hidden sm:block h-px w-16" style={{ background: 'linear-gradient(90deg, #D4AF37, transparent)' }} />
        </div>

        <p className="k-rise mt-4 max-w-xl text-karn-text/80 text-[0.98rem] sm:text-[1.05rem] leading-relaxed"
           style={{ animationDelay: '.2s', textShadow: '0 2px 10px rgba(0,0,0,.95)' }}>
          Four raids, a Colosseum built for champions and a weapon that grows with every kill.
          Your legend is earned, not handed out.
        </p>

        <div className="k-rise mt-9 flex flex-col sm:flex-row items-center gap-3 sm:gap-4" style={{ animationDelay: '.28s' }}>
          <button type="button" onClick={() => setPlayOpen(true)}
                  className="k-btn k-btn-crimson h-14 px-10 text-[1.05rem] tracking-[0.2em] w-64 sm:w-auto">
            <Download size={19} /> Play Now
          </button>
          <a href={DISCORD_URL} target="_blank" rel="noopener noreferrer"
             className="k-btn k-btn-ghost h-14 px-8 text-[0.95rem] w-64 sm:w-auto">
            <DiscordIcon size={18} color="#8C9EFF" /> Join Discord
          </a>
        </div>
      </div>
    </section>
  );
}
