const RELEASES_BASE = 'https://github.com/tamerab1/zelus-server/releases/latest/download';

const PLATFORMS = [
  {
    id:    'windows',
    label: 'Windows',
    badge: 'Windows 10 / 11',
    file:  'ZelusLauncher-windows.exe',
    note:  'Run the installer. No Java required.',
  },
  {
    id:    'mac',
    label: 'macOS',
    badge: 'macOS 11+',
    file:  'ZelusLauncher-mac.dmg',
    note:  'Open the DMG and drag ZelusLauncher to Applications.',
  },
  {
    id:    'linux',
    label: 'Linux',
    badge: 'Ubuntu / Debian',
    file:  'ZelusLauncher-linux.deb',
    note:  'Install with: sudo dpkg -i ZelusLauncher-linux.deb',
  },
];

function PlatformCard({ platform }) {
  const { label, badge, file, note } = platform;
  const url = `${RELEASES_BASE}/${file}`;

  return (
    <div style={{
      background:   'linear-gradient(180deg, rgba(30,25,15,0.95) 0%, rgba(18,15,8,0.98) 100%)',
      border:       '1px solid rgba(212,175,55,0.2)',
      boxShadow:    '0 4px 32px rgba(0,0,0,0.6), inset 0 1px 0 rgba(255,255,255,0.04)',
      padding:      '40px 32px',
      display:      'flex',
      flexDirection:'column',
      alignItems:   'center',
      gap:          '20px',
      position:     'relative',
      overflow:     'hidden',
    }}>
      {/* Corner accents */}
      <div style={{ position:'absolute', top:0, left:0, width:24, height:24,
        borderTop:'1px solid rgba(212,175,55,0.4)', borderLeft:'1px solid rgba(212,175,55,0.4)' }} />
      <div style={{ position:'absolute', top:0, right:0, width:24, height:24,
        borderTop:'1px solid rgba(212,175,55,0.4)', borderRight:'1px solid rgba(212,175,55,0.4)' }} />
      <div style={{ position:'absolute', bottom:0, left:0, width:24, height:24,
        borderBottom:'1px solid rgba(212,175,55,0.4)', borderLeft:'1px solid rgba(212,175,55,0.4)' }} />
      <div style={{ position:'absolute', bottom:0, right:0, width:24, height:24,
        borderBottom:'1px solid rgba(212,175,55,0.4)', borderRight:'1px solid rgba(212,175,55,0.4)' }} />

      {/* Labels */}
      <div style={{ textAlign:'center' }}>
        <div className="font-fantasy" style={{ fontSize:22, color:'#f0d060',
          letterSpacing:'0.15em', textShadow:'0 0 16px rgba(212,175,55,0.5)',
          marginBottom:6 }}>
          {label}
        </div>
        <div style={{ fontSize:11, color:'#b0a590', letterSpacing:'0.12em', fontFamily:'monospace' }}>
          {badge}
        </div>
      </div>

      {/* Note */}
      <p style={{ fontSize:12, color:'#b0a590', textAlign:'center', lineHeight:1.7,
        maxWidth:220, minHeight:38 }}>
        {note}
      </p>

      {/* Button */}
      <a
        href={url}
        className="font-fantasy"
        style={{
          display:'inline-block', padding:'12px 36px',
          background:'linear-gradient(180deg, #2e2410 0%, #1a1408 100%)',
          border:'1px solid rgba(212,175,55,0.6)',
          color:'#d4af37', fontSize:11, letterSpacing:'0.22em',
          textDecoration:'none',
          boxShadow:'inset 0 1px 0 rgba(255,255,255,0.06), 0 4px 16px rgba(0,0,0,0.5), 0 0 20px rgba(212,175,55,0.08)',
          transition:'all 0.15s',
        }}
        onMouseOver={e => {
          e.currentTarget.style.borderColor = '#d4af37';
          e.currentTarget.style.boxShadow = 'inset 0 1px 0 rgba(255,255,255,0.06), 0 4px 24px rgba(0,0,0,0.6), 0 0 32px rgba(212,175,55,0.2)';
        }}
        onMouseOut={e => {
          e.currentTarget.style.borderColor = 'rgba(212,175,55,0.6)';
          e.currentTarget.style.boxShadow = 'inset 0 1px 0 rgba(255,255,255,0.06), 0 4px 16px rgba(0,0,0,0.5), 0 0 20px rgba(212,175,55,0.08)';
        }}
      >
        DOWNLOAD
      </a>
    </div>
  );
}

export default function DownloadView() {
  return (
    <div style={{ minHeight:'100vh', paddingBottom:80 }}>

      {/* ── Hero ── */}
      <div style={{
        textAlign:'center', padding:'72px 24px 48px',
        borderBottom:'1px solid rgba(212,175,55,0.1)',
        marginBottom:64,
      }}>
        <div style={{
          display:'inline-block', padding:'40px 48px 36px',
          background:'linear-gradient(180deg, rgba(5,4,8,0.72) 0%, rgba(5,4,8,0.45) 100%)',
          backdropFilter:'blur(2px)',
          boxShadow:'0 0 60px rgba(0,0,0,0.6)',
        }}>
          {/* Divider line top */}
          <div style={{ width:120, height:1, margin:'0 auto 32px',
            background:'linear-gradient(90deg,transparent,#d4af37,transparent)' }} />

          <p className="font-fantasy" style={{
            fontSize:11, letterSpacing:'0.55em', marginBottom:12,
            color:'#d4af37', textShadow:'0 1px 8px rgba(0,0,0,0.9)',
          }}>
            PLAY NOW
          </p>
          <h1 className="font-fantasy" style={{
            fontSize:'clamp(28px,5vw,48px)', letterSpacing:'0.2em',
            color:'#ffffff', textShadow:'0 2px 20px rgba(0,0,0,0.9), 0 0 30px rgba(212,175,55,0.2)',
            marginBottom:20,
          }}>
            DOWNLOAD ZELUS
          </h1>
          {/* Divider line */}
          <div style={{ width:80, height:1, margin:'0 auto 20px',
            background:'linear-gradient(90deg,transparent,rgba(212,175,55,0.6),transparent)' }} />
          <p style={{ color:'#c8bfb0', fontSize:13, letterSpacing:'0.1em',
            maxWidth:480, margin:'0 auto', lineHeight:1.8,
            textShadow:'0 1px 6px rgba(0,0,0,0.8)' }}>
            The Zelus launcher automatically keeps your client up to date.
            Download once, play forever.
          </p>
        </div>
      </div>

      {/* ── Platform cards ── */}
      <div style={{
        maxWidth:960, margin:'0 auto', padding:'0 24px',
        display:'grid', gridTemplateColumns:'repeat(auto-fit,minmax(260px,1fr))',
        gap:24, marginBottom:72,
      }}>
        {PLATFORMS.map(p => <PlatformCard key={p.id} platform={p} />)}
      </div>

      {/* ── How it works ── */}
      <div style={{ maxWidth:700, margin:'0 auto', padding:'0 24px' }}>
        <div style={{ textAlign:'center', marginBottom:36 }}>
          <div className="font-fantasy" style={{ fontSize:16, letterSpacing:'0.2em',
            color:'#d4af37', marginBottom:8,
            textShadow:'0 2px 12px rgba(0,0,0,0.9)' }}>
            HOW IT WORKS
          </div>
          <div style={{ width:60, height:1, margin:'0 auto',
            background:'linear-gradient(90deg,transparent,rgba(212,175,55,0.6),transparent)' }} />
        </div>

        <div style={{ display:'grid', gridTemplateColumns:'repeat(auto-fit,minmax(180px,1fr))', gap:24 }}>
          {[
            { n:'1', title:'Download',   body:'Get the launcher for your OS above.' },
            { n:'2', title:'Install',    body:'Run the installer (Windows) or open the DMG/DEB.' },
            { n:'3', title:'Play',       body:'The launcher downloads the client and connects automatically.' },
          ].map(({ n, title, body }) => (
            <div key={n} style={{ textAlign:'center', padding:'24px 16px',
              background:'rgba(5,4,8,0.92)', border:'1px solid rgba(212,175,55,0.25)',
              boxShadow:'0 4px 24px rgba(0,0,0,0.7)' }}>
              <div className="font-fantasy" style={{ fontSize:28, color:'#d4af37',
                marginBottom:8 }}>{n}</div>
              <div className="font-fantasy" style={{ fontSize:12, letterSpacing:'0.18em',
                color:'#d4af37', marginBottom:8 }}>{title.toUpperCase()}</div>
              <div style={{ fontSize:12, color:'#c8bfb0', lineHeight:1.7 }}>{body}</div>
            </div>
          ))}
        </div>
      </div>
    </div>
  );
}
