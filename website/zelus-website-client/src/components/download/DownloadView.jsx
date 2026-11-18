import { useMemo } from 'react';
import { Monitor, Apple, Terminal, Coffee, Download, ShieldCheck, RefreshCw, Cpu } from 'lucide-react';
import {
  LAUNCHER_URL, MAC_ARM_URL, MAC_INTEL_URL, LINUX_APPIMAGE_URL, LINUX_TAR_URL, LAUNCHER_JAR_URL, JAR_URL,
  DISCORD_URL, REVISION,
} from '../../data/links.js';
import DiscordIcon from '../ui/DiscordIcon.jsx';

// Every link points at the "development-latest" tag, not GitHub's "latest" alias: that alias only resolves to the
// newest non-prerelease, and development-latest is published as a rolling --prerelease on every client push.
const PLATFORMS = [
  {
    id: 'windows',
    Icon: Monitor,
    label: 'Windows',
    badge: 'Windows 10 / 11',
    note: 'The Karn Beta installer. Installs once and updates the game by itself every time you open it.',
    downloads: [{ label: 'Download for Windows', url: LAUNCHER_URL }],
    steps: ['Run KarnBeta-windows.exe and follow the installer.', 'Open Karn Beta from the Start menu or the desktop shortcut.'],
  },
  {
    id: 'mac',
    Icon: Apple,
    label: 'macOS',
    badge: 'macOS 12+',
    note: 'Karn Beta as a disk image. Pick the build for your Mac\'s chip.',
    downloads: [
      { label: 'Apple Silicon (M1-M4)', url: MAC_ARM_URL },
      { label: 'Intel', url: MAC_INTEL_URL },
    ],
    steps: [
      'Open the .dmg and drag Karn Beta into Applications.',
      'First launch only: right-click the app and choose Open (it is not notarised by Apple yet).',
    ],
  },
  {
    id: 'linux',
    Icon: Terminal,
    label: 'Linux',
    badge: 'x86-64',
    note: 'A single AppImage, or a plain .tar.gz if your distro has no FUSE.',
    downloads: [
      { label: 'AppImage', url: LINUX_APPIMAGE_URL },
      { label: '.tar.gz', url: LINUX_TAR_URL },
    ],
    steps: [
      'AppImage: chmod +x KarnBeta-linux-x86_64.AppImage, then run it.',
      '.tar.gz: extract it and run KarnBeta/bin/KarnBeta.',
    ],
  },
  {
    id: 'jar',
    Icon: Coffee,
    label: 'Universal .jar',
    badge: 'Any OS · Java 21',
    note: 'No installer. The launcher jar keeps the game updated; client.jar is the game on its own.',
    downloads: [
      { label: 'Launcher .jar', url: LAUNCHER_JAR_URL },
      { label: 'client.jar', url: JAR_URL },
    ],
    steps: ['Install Java 21, then run: java -jar karn-beta-launcher.jar', 'Or double-click client.jar to play without the launcher.'],
  },
];

const STEPS = [
  { n: 1, title: 'Download', body: 'Pick your system below: Windows, macOS, Linux or the universal .jar.' },
  { n: 2, title: 'Install',  body: 'Run the installer. The launcher fetches Java and the game for you.' },
  { n: 3, title: 'Play',     body: 'Log in with any new username: your character is created on the spot.' },
];

const FACTS = [
  { Icon: Cpu,         title: `Revision ${REVISION}`,   body: 'Built on the latest RuneLite with 117 HD included.' },
  { Icon: RefreshCw,   title: 'Always current',        body: 'The launcher checks the game and the cache before every login.' },
  { Icon: ShieldCheck, title: 'Your progress is safe', body: 'Accounts, banks and items carry over through every update.' },
];

/** the visitor's own system, to put its card first */
function detectPlatform() {
  if (typeof navigator === 'undefined') return 'windows';
  const ua = `${navigator.userAgent} ${navigator.platform ?? ''}`.toLowerCase();
  if (ua.includes('mac')) return 'mac';
  if (ua.includes('linux') && !ua.includes('android')) return 'linux';
  return 'windows';
}

function PlatformCard({ platform, primary }) {
  const { label, badge, note, downloads, steps } = platform;
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
            {primary && <span className="k-chip !text-[0.62rem]">Your system</span>}
          </div>
          <p className="font-ui font-semibold text-sm text-karn-muted tracking-wide">{badge}</p>
        </div>
      </div>
      <p className="text-karn-muted mt-5 leading-relaxed">{note}</p>
      <ol className="mt-4 space-y-1.5 text-sm text-karn-text/85 flex-1 list-decimal list-inside">
        {steps.map(s => <li key={s} className="leading-snug">{s}</li>)}
      </ol>
      <div className={`mt-6 grid gap-2 ${downloads.length > 1 ? 'sm:grid-cols-2' : ''}`}>
        {downloads.map((d, i) => (
          <a key={d.url} href={d.url}
             className={`k-btn ${primary && i === 0 ? 'k-btn-crimson' : 'k-btn-gold'} h-12 px-5 text-[0.82rem]`}>
            <Download size={17} /> {d.label}
          </a>
        ))}
      </div>
    </div>
  );
}

export default function DownloadView() {
  const mine = useMemo(detectPlatform, []);
  const ordered = useMemo(
    () => [...PLATFORMS].sort((a, b) => (b.id === mine) - (a.id === mine)),
    [mine],
  );

  return (
    <div className="max-w-5xl mx-auto px-4 sm:px-6 pt-10 sm:pt-16 pb-10">
      <header className="text-center max-w-2xl mx-auto">
        <p className="k-eyebrow">Play now · Free</p>
        <h1 className="k-heading k-gold-text text-4xl sm:text-5xl mt-3">Download Karn</h1>
        <p className="text-karn-muted mt-4 leading-relaxed">
          One download and you are in. The Karn Beta launcher keeps your game up to date on Windows, macOS and Linux,
          so you only ever install it once.
        </p>
      </header>

      <div className="mt-12 grid gap-4 md:grid-cols-2">
        {ordered.map(p => <PlatformCard key={p.id} platform={p} primary={p.id === mine} />)}
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
