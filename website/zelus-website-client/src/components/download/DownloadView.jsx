import { Monitor, Coffee, Download, ShieldCheck, RefreshCw, Cpu } from 'lucide-react';
import { LAUNCHER_URL, JAR_URL, DISCORD_URL, REVISION } from '../../data/links.js';
import DiscordIcon from '../ui/DiscordIcon.jsx';

// The links point at the "development-latest" tag, not GitHub's "latest" alias: that alias only resolves to the
// newest non-prerelease, and development-latest is published as a rolling --prerelease on every client push.
const PLATFORMS = [
  {
    id: 'launcher',
    Icon: Monitor,
    label: 'Windows Launcher',
    badge: 'Windows 10 / 11',
    note: 'Installs once and updates the client by itself every time you open it.',
    url: LAUNCHER_URL,
    primary: true,
  },
  {
    id: 'jar',
    Icon: Coffee,
    label: 'Client.jar',
    badge: 'Java 21 required',
    note: 'No installer. Download it and double-click to play.',
    url: JAR_URL,
  },
];

const STEPS = [
  { n: 1, title: 'Download', body: 'Get the launcher, or client.jar if you already have Java 21.' },
  { n: 2, title: 'Run it',   body: 'Install the launcher, or double-click client.jar.' },
  { n: 3, title: 'Play',     body: 'Log in with any new username: your character is created on the spot.' },
];

const FACTS = [
  { Icon: Cpu,         title: `Revision ${REVISION}`, body: 'Built on the latest RuneLite with 117 HD included.' },
  { Icon: RefreshCw,   title: 'Always current',      body: 'The launcher pulls every update before you log in.' },
  { Icon: ShieldCheck, title: 'Your progress is safe', body: 'Accounts, banks and items carry over through every update.' },
];

function PlatformCard({ platform }) {
  const { label, badge, note, url, primary } = platform;
  return (
    <div className={`k-panel k-panel--hover k-cap p-7 sm:p-8 flex flex-col ${primary ? 'border-karn-gold/45' : ''}`}
         style={primary ? { background: 'linear-gradient(180deg, rgba(184,29,36,0.14), rgba(18,16,20,1) 55%)' } : undefined}>
      <div className="flex items-center gap-4">
        <span className={`grid place-items-center w-14 h-14 rounded-md shrink-0 ${primary ? 'text-white' : 'text-karn-gold-hi border border-karn-border bg-black/30'}`}
              style={primary ? { background: 'linear-gradient(180deg, #CF262E, #8E141A)', boxShadow: 'inset 0 1px 0 rgba(255,255,255,.25)' } : undefined}>
          <platform.Icon size={24} />
        </span>
        <div>
          <div className="flex items-center gap-2 flex-wrap">
            <h2 className="font-ui font-bold text-xl uppercase tracking-[0.1em] text-white">{label}</h2>
            {primary && <span className="k-chip !text-[0.62rem]">Recommended</span>}
          </div>
          <p className="font-ui font-semibold text-sm text-karn-muted tracking-wide">{badge}</p>
        </div>
      </div>
      <p className="text-karn-muted mt-5 leading-relaxed flex-1">{note}</p>
      <a href={url} className={`k-btn ${primary ? 'k-btn-crimson' : 'k-btn-gold'} h-12 px-6 text-[0.88rem] mt-6`}>
        <Download size={17} /> Download
      </a>
    </div>
  );
}

export default function DownloadView() {
  return (
    <div className="max-w-5xl mx-auto px-4 sm:px-6 pt-10 sm:pt-16 pb-10">
      <header className="text-center max-w-2xl mx-auto">
        <p className="k-eyebrow">Play now · Free</p>
        <h1 className="k-heading k-gold-text text-4xl sm:text-5xl mt-3">Download Karn</h1>
        <p className="text-karn-muted mt-4 leading-relaxed">
          One download and you are in. The launcher keeps your client up to date, so you only ever install it once.
        </p>
      </header>

      <div className="mt-12 grid gap-4 md:grid-cols-2">
        {PLATFORMS.map(p => <PlatformCard key={p.id} platform={p} />)}
      </div>

      <section className="mt-16" aria-labelledby="how-title">
        <div className="k-divider max-w-xs mx-auto" />
        <h2 id="how-title" className="font-fantasy font-bold text-2xl text-white text-center mt-8">How it works</h2>
        <ol className="mt-8 grid gap-4 sm:grid-cols-3">
          {STEPS.map(s => (
            <li key={s.n} className="k-panel p-6 text-center">
              <span className="font-fantasy font-black text-4xl k-gold-text">{s.n}</span>
              <p className="font-ui font-bold uppercase tracking-[0.14em] text-white mt-2">{s.title}</p>
              <p className="text-sm text-karn-muted mt-2 leading-relaxed">{s.body}</p>
            </li>
          ))}
        </ol>
      </section>

      <section className="mt-12 grid gap-4 sm:grid-cols-3" aria-label="About the client">
        {FACTS.map(f => (
          <div key={f.title} className="flex gap-3.5 items-start p-5 rounded-md border border-karn-border bg-karn-surface/60">
            <f.Icon size={20} className="text-karn-gold-hi shrink-0 mt-0.5" />
            <div>
              <p className="font-ui font-bold text-white tracking-wide">{f.title}</p>
              <p className="text-sm text-karn-muted mt-1 leading-relaxed">{f.body}</p>
            </div>
          </div>
        ))}
      </section>

      <p className="text-center text-sm text-karn-muted mt-12">
        Stuck?{' '}
        <a href={DISCORD_URL} target="_blank" rel="noopener noreferrer" className="inline-flex items-center gap-1.5 text-karn-gold-hi hover:text-white font-semibold">
          <DiscordIcon size={15} color="currentColor" /> Ask on Discord
        </a>
      </p>
    </div>
  );
}
