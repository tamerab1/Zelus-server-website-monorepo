import { useApp } from '../../context/AppContext.jsx';

const DISCORD_URL = 'https://discord.gg/XZ3E6Nur2r';

const NAV_LINKS = [
  { label: 'Play Now',         view: 'download' },
  { label: 'Vote for Rewards', view: 'vote'     },
  { label: 'Donor Store',      view: 'store'    },
  { label: 'Hiscores',         view: 'hiscores' },
];

const COMMUNITY_LINKS = [
  { label: 'Discord Server', href: DISCORD_URL },
  { label: 'Server Rules',   href: '#rules'    },
  { label: 'Support Ticket', href: '#support'  },
  { label: 'Report a Bug',   href: '#report'   },
];

const LEGAL_LINKS = [
  { label: 'Terms of Service', href: '#tos'     },
  { label: 'Privacy Policy',   href: '#privacy' },
  { label: 'Disclaimer',       href: '#disclaimer' },
];

function FooterNavLink({ label, onClick }) {
  return (
    <li>
      <button
        onClick={onClick}
        className="font-fantasy text-xs tracking-wide transition-colors text-left"
        style={{ color: '#8a7f72' }}
        onMouseOver={e => e.currentTarget.style.color = '#d4af37'}
        onMouseOut={e  => e.currentTarget.style.color = '#8a7f72'}
      >
        {label}
      </button>
    </li>
  );
}

function FooterExternalLink({ label, href }) {
  return (
    <li>
      <a
        href={href}
        target={href.startsWith('http') ? '_blank' : undefined}
        rel="noopener noreferrer"
        className="font-fantasy text-xs tracking-wide transition-colors"
        style={{ color: '#8a7f72', textDecoration: 'none' }}
        onMouseOver={e => e.currentTarget.style.color = '#d4af37'}
        onMouseOut={e  => e.currentTarget.style.color = '#8a7f72'}
      >
        {label}
      </a>
    </li>
  );
}

export default function Footer() {
  const { setCurrentView } = useApp();

  return (
    <footer style={{ background: '#030305', borderTop: '1px solid #1e1a14' }}>
      <div className="gold-divider" />

      <div className="max-w-7xl mx-auto px-8 pt-10 pb-6">
        <div className="grid grid-cols-1 md:grid-cols-4 gap-8 mb-8">

          {/* ── Brand ── */}
          <div className="md:col-span-1">
            <h4
              className="font-fantasy text-lg tracking-widest mb-3"
              style={{ color: '#d4af37', textShadow: '0 0 12px rgba(212,175,55,0.3)' }}
            >
              ✦ ZELUS RSPS ✦
            </h4>
            <p className="font-sans text-xs leading-relaxed mb-4" style={{ color: '#8a7f72' }}>
              A dedicated team delivering the most immersive custom RuneScape
              experience. Join our ever-growing community today.
            </p>
            {/* Social icons */}
            <div className="flex gap-2">
              {[
                { label: 'Discord', letter: 'D', href: DISCORD_URL },
                { label: 'YouTube', letter: 'YT', href: '#youtube' },
                { label: 'Twitter', letter: 'X',  href: '#twitter' },
              ].map(({ label, letter, href }) => (
                <a
                  key={label}
                  href={href}
                  title={label}
                  target={href.startsWith('http') ? '_blank' : undefined}
                  rel="noopener noreferrer"
                  className="w-8 h-8 flex items-center justify-center font-fantasy text-xs font-bold transition-all no-underline"
                  style={{ background: 'rgba(255,255,255,0.03)', border: '1px solid #2a2420', borderRadius: 2, color: '#8a7f72' }}
                  onMouseOver={e => { e.currentTarget.style.color = '#d4af37'; e.currentTarget.style.borderColor = 'rgba(212,175,55,0.35)'; }}
                  onMouseOut={e  => { e.currentTarget.style.color = '#8a7f72'; e.currentTarget.style.borderColor = '#2a2420'; }}
                >
                  {letter}
                </a>
              ))}
            </div>
          </div>

          {/* ── Quick Links ── */}
          <div>
            <h5 className="font-fantasy text-xs tracking-widest mb-4" style={{ color: '#6a5f50' }}>
              QUICK LINKS
            </h5>
            <ul className="space-y-2.5">
              {NAV_LINKS.map(({ label, view }) => (
                <FooterNavLink key={label} label={label} onClick={() => setCurrentView(view)} />
              ))}
            </ul>
          </div>

          {/* ── Community ── */}
          <div>
            <h5 className="font-fantasy text-xs tracking-widest mb-4" style={{ color: '#6a5f50' }}>
              COMMUNITY
            </h5>
            <ul className="space-y-2.5">
              {COMMUNITY_LINKS.map(({ label, href }) => (
                <FooterExternalLink key={label} label={label} href={href} />
              ))}
            </ul>
          </div>

          {/* ── Legal ── */}
          <div>
            <h5 className="font-fantasy text-xs tracking-widest mb-4" style={{ color: '#6a5f50' }}>
              LEGAL
            </h5>
            <ul className="space-y-2.5">
              {LEGAL_LINKS.map(({ label, href }) => (
                <FooterExternalLink key={label} label={label} href={href} />
              ))}
            </ul>
          </div>
        </div>

        {/* ── Bottom bar ── */}
        <div
          className="flex flex-col md:flex-row justify-between items-center pt-5 font-fantasy text-xs tracking-widest"
          style={{ borderTop: '1px solid #1a1610', color: '#6a5f50' }}
        >
          <p>© 2026 ZELUS PROJECT. ALL RIGHTS RESERVED.</p>
          <p className="mt-3 md:mt-0" style={{ color: '#4a3f32' }}>
            NOT AFFILIATED WITH JAGEX LIMITED.
          </p>
        </div>
      </div>
    </footer>
  );
}
