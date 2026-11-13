/** @type {import('tailwindcss').Config} */
export default {
  content: [
    "./index.html",
    "./src/**/*.{js,ts,jsx,tsx}",
  ],
  theme: {
    extend: {
      colors: {
        // Karn: Crimson & Forged Gold
        'karn-void':     '#09080A', // page background
        'karn-surface':  '#121014', // cards / panels
        'karn-raised':   '#19151B', // hover / nested surfaces
        'karn-border':   '#2A2226', // metallic hairline
        'karn-gold':     '#D4AF37', // hover glow, accents
        'karn-gold-hi':  '#E5B84B', // heading gradient top
        'karn-gold-lo':  '#C59B27', // heading gradient bottom
        'karn-crimson':  '#B81D24', // primary action
        'karn-crimson-hi': '#DC2626',
        'karn-text':     '#EDE6DD',
        'karn-muted':    '#A39A90',
        'karn-dim':      '#6E655D',
      },
      fontFamily: {
        'sans':    ['Inter', 'system-ui', 'sans-serif'],
        'fantasy': ['Cinzel', 'Trajan Pro', 'serif'],
        'ui':      ['Rajdhani', 'Inter', 'system-ui', 'sans-serif'],
        // Numbers in tables/stats: Rajdhani with tabular figures (see .tabular in index.css)
        'mono':    ['Rajdhani', 'ui-monospace', 'monospace'],
      },
      boxShadow: {
        'gold':    '0 0 0 1px rgba(212,175,55,0.45), 0 0 24px rgba(212,175,55,0.18)',
        'gold-sm': '0 0 12px rgba(212,175,55,0.2)',
        'panel':   '0 18px 40px -18px rgba(0,0,0,0.85)',
      },
    },
  },
  plugins: [],
}
