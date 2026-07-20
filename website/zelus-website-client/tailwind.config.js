/** @type {import('tailwindcss').Config} */
export default {
  content: [
    "./index.html",
    "./src/**/*.{js,ts,jsx,tsx}",
  ],
  theme: {
    extend: {
      colors: {
        'zelus-bg':        '#09090b',
        'zelus-dark':      '#050507',
        'zelus-stone':     '#0d0d12',
        'zelus-border':    '#1e1a14',
        'zelus-gold':      '#d4af37',
        'zelus-gold-light':'#f0d060',
        'zelus-gold-dark': '#8b6914',
        'zelus-blue':      '#1e3a8a',
      },
      fontFamily: {
        'sans':    ['system-ui', 'sans-serif'],
        'fantasy': ['Cinzel', 'Trajan Pro', 'serif'],
      },
      boxShadow: {
        'gold':    '0 0 20px rgba(212,175,55,0.3), 0 0 40px rgba(212,175,55,0.1)',
        'gold-sm': '0 0 10px rgba(212,175,55,0.2)',
      },
    },
  },
  plugins: [],
}
