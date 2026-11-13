import { useApp } from '../../context/AppContext.jsx';
import { DISCORD_URL, SITE_DOMAIN, REVISION } from '../../data/links.js';
import DiscordIcon from '../ui/DiscordIcon.jsx';
import logo from '../../assets/karn-logo.webp';

// Real destinations only: nothing here links to a page that doesn't exist yet.
const GAME_LINKS = [
  { label: 'Play Now',   view: 'download' },
  { label: 'Highscores', view: 'hiscores' },
  { label: 'Vote',       view: 'vote'     },
  { label: 'Store',      view: 'store'    },
];

function FooterHeading({ children }) {
  return <h3 className="font-ui font-bold text-[0.78rem] uppercase tracking-[0.24em] text-karn-gold-hi mb-4">{children}</h3>;
}

export default function Footer() {
  const { setCurrentView, setPlayOpen } = useApp();
  const year = new Date().getFullYear();

  return (
    <footer className="relative mt-24 border-t border-karn-border" style={{ background: '#060507' }}>
      <div className="k-divider absolute inset-x-0 top-0" />

      <div className="max-w-7xl mx-auto px-4 sm:px-6 pt-14 pb-10 grid gap-10 md:grid-cols-[1.5fr_1fr_1fr]">
        {/* Brand */}
        <div>
          <img src={logo} alt="Karn" className="h-14 w-auto" />
          <p className="mt-4 text-sm text-karn-muted leading-relaxed max-w-sm">
            A revision {REVISION} Old School server built around true progression PvM, the Colosseum of Trials and
            high-stakes PvP.
          </p>
          <button type="button" onClick={() => setPlayOpen(true)} className="k-btn k-btn-crimson h-10 px-5 text-[0.8rem] mt-6">
            Play Karn free
          </button>
        </div>

        {/* Game */}
        <nav aria-label="Footer">
          <FooterHeading>Game</FooterHeading>
          <ul className="space-y-2.5">
            {GAME_LINKS.map(l => (
              <li key={l.view}>
                <button type="button" onClick={() => setCurrentView(l.view)}
                  className="text-sm text-karn-muted hover:text-karn-gold-hi transition-colors">
                  {l.label}
                </button>
              </li>
            ))}
          </ul>
        </nav>

        {/* Community */}
        <div>
          <FooterHeading>Community</FooterHeading>
          <a href={DISCORD_URL} target="_blank" rel="noopener noreferrer"
             className="inline-flex items-center gap-2 text-sm text-karn-muted hover:text-white transition-colors">
            <DiscordIcon size={16} color="#8C9EFF" /> Discord server
          </a>
          <p className="text-sm text-karn-dim mt-3 leading-relaxed">
            Updates, support and giveaways are all posted on Discord.
          </p>
        </div>
      </div>

      <div className="border-t border-karn-border/70">
        <div className="max-w-7xl mx-auto px-4 sm:px-6 py-6 flex flex-col md:flex-row gap-3 md:items-center md:justify-between text-xs text-karn-dim leading-relaxed">
          <p>
            © {year} <span className="text-karn-muted font-semibold">{SITE_DOMAIN}</span>. All rights reserved.
          </p>
          <p className="md:text-right max-w-2xl">
            Karn is an independent fan-made game server and is not affiliated with, endorsed by or connected to Jagex Ltd.
            Old School RuneScape and RuneScape are trademarks of Jagex Ltd.
          </p>
        </div>
      </div>
    </footer>
  );
}
